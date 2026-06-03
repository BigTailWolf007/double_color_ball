package com.dcb.predict.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcb.auth.entity.User;
import com.dcb.auth.mapper.UserMapper;
import com.dcb.common.enums.PrizeLevel;
import com.dcb.common.exception.BizException;
import com.dcb.common.result.PageResult;
import com.dcb.common.util.LotteryUtils;
import com.dcb.lottery.entity.LotteryResult;
import com.dcb.lottery.service.LotteryService;
import com.dcb.predict.dto.BallKeyRowDTO;
import com.dcb.predict.dto.PredictSaveDTO;
import com.dcb.predict.entity.PredictRecord;
import com.dcb.predict.mapper.PredictRecordMapper;
import com.dcb.predict.vo.PredictRecordVO;
import com.dcb.purchase.dto.PurchaseAddDTO;
import com.dcb.purchase.service.PurchaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 预测号码服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PredictService {

    private final PredictRecordMapper predictRecordMapper;
    private final LotteryService lotteryService;
    private final PurchaseService purchaseService;
    private final UserMapper userMapper;

    /**
     * 保存预测号码，若该期已有开奖号码则立即计算命中结果，跳过同期重复号码
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(List<PredictSaveDTO> dtoList, Long userId) {
        log.info("保存预测号码，共{}组", dtoList.size());

        // 1. 提取所有不重复期号
        List<String> issues = dtoList.stream().map(PredictSaveDTO::getIssue).distinct().collect(Collectors.toList());

        // 2. 批量查已存在的 ballKey，按期号分组
        Map<String, Set<String>> existingKeyMap = new HashMap<>();
        for (String issue : issues) {
            existingKeyMap.put(issue, new HashSet<>());
        }
        List<BallKeyRowDTO> existingRows = predictRecordMapper.selectBallKeysByIssues(issues, userId);
        for (BallKeyRowDTO row : existingRows) {
            existingKeyMap.computeIfAbsent(row.getIssue(), k -> new HashSet<>()).add(row.getBallKey());
        }

        // 3. 批量查开奖号码
        Map<String, LotteryResult> lotteryCache = lotteryService.getByIssues(issues);

        // 4. 逐条处理，去重后收集待插入记录
        int skip = 0;
        List<PredictRecord> toInsert = new ArrayList<>();
        for (PredictSaveDTO dto : dtoList) {
            LotteryUtils.validateRed(Arrays.asList(
                    dto.getRed1(), dto.getRed2(), dto.getRed3(),
                    dto.getRed4(), dto.getRed5(), dto.getRed6()));
            LotteryUtils.validateBlue(dto.getBlue());

            String key = LotteryUtils.buildBallKey(dto);
            if (!existingKeyMap.get(dto.getIssue()).add(key)) {
                skip++;
                continue;
            }

            PredictRecord record = PredictRecord.builder()
                    .issue(dto.getIssue())
                    .red1(dto.getRed1()).red2(dto.getRed2()).red3(dto.getRed3())
                    .red4(dto.getRed4()).red5(dto.getRed5()).red6(dto.getRed6())
                    .blue(dto.getBlue())
                    .ballKey(key)
                    .userId(userId)
                    .sumVal(LotteryUtils.calcSum(dto.getRed1(), dto.getRed2(), dto.getRed3(),
                            dto.getRed4(), dto.getRed5(), dto.getRed6()))
                    .zoneRatio(LotteryUtils.calcZoneRatio(dto.getRed1(), dto.getRed2(), dto.getRed3(),
                            dto.getRed4(), dto.getRed5(), dto.getRed6()))
                    .oddEvenRatio(LotteryUtils.calcOddEvenRatio(dto.getRed1(), dto.getRed2(), dto.getRed3(),
                            dto.getRed4(), dto.getRed5(), dto.getRed6()))
                    .rangeVal(LotteryUtils.calcRange(dto.getRed1(), dto.getRed2(), dto.getRed3(),
                            dto.getRed4(), dto.getRed5(), dto.getRed6()))
                    .build();

            LotteryResult lottery = lotteryCache.get(dto.getIssue());
            if (lottery != null) {
                calcAndFill(record, lottery);
                log.debug("期号 {} 已开奖，立即计算命中结果：{}", dto.getIssue(),
                        PrizeLevel.ofLevel(record.getPrizeLevel()).getDesc());
            } else {
                log.debug("期号 {} 暂无开奖号码，待后续补算", dto.getIssue());
            }
            toInsert.add(record);
        }

        // 5. 批量插入
        if (!toInsert.isEmpty()) {
            predictRecordMapper.batchInsert(toInsert);
        }
        log.info("预测号码保存完成，共{}组，跳过重复{}组", toInsert.size(), skip);
    }

    /**
     * 手动触发重新计算指定期号所有预测记录的命中结果
     */
    @Transactional(rollbackFor = Exception.class)
    public int calc(String issue) {
        log.info("开始重新计算期号 {} 的预测命中结果", issue);
        LotteryResult lottery = lotteryService.getByIssue(issue);
        if (lottery == null) {
            log.warn("期号 {} 暂无开奖号码，无法计算", issue);
            return 0;
        }
        List<PredictRecord> records = predictRecordMapper.selectList(
                new LambdaQueryWrapper<PredictRecord>()
                        .eq(PredictRecord::getIssue, issue));
        if (records.isEmpty()) {
            return 0;
        }
        records.forEach(record -> calcAndFill(record, lottery));
        predictRecordMapper.batchUpdateHitResult(records);
        log.info("期号 {} 重新计算完成，共更新 {} 条记录", issue, records.size());
        return records.size();
    }

    /**
     * 分页查询预测号码
     */
    public PageResult<PredictRecordVO> list(String issue, int page, int size, Long userId) {
        Page<PredictRecord> pageParam = new Page<>(page, size);
        Page<PredictRecord> result = (Page<PredictRecord>) predictRecordMapper
                .selectPageByIssue(pageParam, issue, userId);

        List<PredictRecord> records = result.getRecords();

        // 批量预加载：用户名 + 开奖号码
        Map<Long, String> usernameMap = buildUsernameMap(records);
        Map<String, LotteryResult> lotteryMap = lotteryService.getByIssues(
                records.stream().map(PredictRecord::getIssue).distinct().collect(Collectors.toList()));

        List<PredictRecordVO> voList = new ArrayList<>();
        for (PredictRecord r : records) {
            voList.add(toVO(r, usernameMap, lotteryMap));
        }
        return PageResult.of(result.getTotal(), voList);
    }

    /**
     * 按 ID 删除单条预测记录
     */
    public void deleteById(Long id, Long userId, String role) {
        PredictRecord record = predictRecordMapper.selectById(id);
        if (record == null) throw new BizException("预测记录不存在");
        if (!"ADMIN".equals(role) && record.getUserId() != null && !record.getUserId().equals(userId)) {
            throw new BizException("无权删除他人记录");
        }
        predictRecordMapper.deleteById(id);
        log.info("删除预测记录，id：{}", id);
    }

    /**
     * 按期号删除该期所有预测记录。userId 为 null 则删除全部，否则仅删除该用户的
     */
    public int deleteByIssue(String issue, Long userId) {
        LambdaQueryWrapper<PredictRecord> wrapper = new LambdaQueryWrapper<PredictRecord>()
                .eq(PredictRecord::getIssue, issue);
        if (userId != null) {
            wrapper.eq(PredictRecord::getUserId, userId);
        }
        int count = predictRecordMapper.delete(wrapper);
        log.info("按期号删除预测记录，期号：{}，userId：{}，共删除 {} 条", issue, userId, count);
        return count;
    }

    /**
     * 查询所有不重复的期号列表
     */
    public List<String> listIssues() {
        return predictRecordMapper.selectDistinctIssues();
    }

    /**
     * 查询指定期号下某用户的预测记录（用于权限校验）
     */
    public List<PredictRecord> listByIssueAndUser(String issue, Long userId) {
        return predictRecordMapper.selectList(
                new LambdaQueryWrapper<PredictRecord>()
                        .eq(PredictRecord::getIssue, issue)
                        .eq(PredictRecord::getUserId, userId));
    }

    /**
     * 模糊查询期号，倒序返回最多10个
     */
    public List<String> suggestIssues(String keyword) {
        return predictRecordMapper.selectIssuesByKeyword(keyword == null ? "" : keyword, 10);
    }

    /**
     * 模糊查询用户名，用于用户筛选下拉提示
     */
    public List<Map<String, Object>> suggestUsers(String keyword) {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .like(User::getUsername, keyword == null ? "" : keyword)
                        .or().like(User::getNickname, keyword == null ? "" : keyword)
                        .last("LIMIT 10"));
        return users.stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * 查询有预测记录的所有用户（用于导出时的用户筛选下拉）
     */
    public List<Map<String, Object>> listUsers() {
        List<PredictRecord> records = predictRecordMapper.selectList(null);
        Set<Long> userIds = records.stream()
                .map(PredictRecord::getUserId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) return new ArrayList<>();
        List<User> users = userMapper.selectBatchIds(new ArrayList<>(userIds));
        return users.stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * 按期号列表将预测号码同步到购买记录。userId 为 null 则同步全部，否则仅同步该用户的
     */
    @Transactional(rollbackFor = Exception.class)
    public int syncToPurchaseByIssues(List<String> issues, Long userId) {
        if (issues == null || issues.isEmpty()) return 0;
        List<PredictRecord> records = predictRecordMapper.selectByIssues(issues, userId);
        return doSync(records);
    }

    /**
     * 按 ID 列表将预测号码同步到购买记录。管理员可同步全部，普通用户仅能同步自己的
     */
    @Transactional(rollbackFor = Exception.class)
    public int syncToPurchaseByIds(List<Long> ids, Long userId, String role) {
        if (ids == null || ids.isEmpty()) return 0;
        List<PredictRecord> records = predictRecordMapper.selectBatchIds(ids);
        // 非管理员校验：只能同步自己的记录
        if (!"ADMIN".equals(role)) {
            for (PredictRecord r : records) {
                if (r.getUserId() != null && !r.getUserId().equals(userId)) {
                    throw new BizException("无权同步他人记录");
                }
            }
        }
        return doSync(records);
    }

    private int doSync(List<PredictRecord> records) {
        if (records.isEmpty()) return 0;
        List<PurchaseAddDTO> dtoList = new ArrayList<>(records.size());
        for (PredictRecord r : records) {
            dtoList.add(PurchaseAddDTO.builder()
                    .issue(r.getIssue())
                    .red1(r.getRed1()).red2(r.getRed2()).red3(r.getRed3())
                    .red4(r.getRed4()).red5(r.getRed5()).red6(r.getRed6())
                    .blue(r.getBlue())
                    .quantity(1)
                    .userId(r.getUserId())  // 保留预测记录的归属用户
                    .build());
        }
        purchaseService.add(dtoList, null);
        log.info("预测号码同步到购买记录，共 {} 条", dtoList.size());
        return dtoList.size();
    }

    /**
     * 流式写出指定期号的预测号码为 TXT 格式
     * 不关闭 outputStream，由调用方（Servlet 容器）负责关闭
     */
    public void exportTxt(List<String> issues, Long userId, OutputStream outputStream) throws IOException {
        List<PredictRecord> records = predictRecordMapper.selectByIssues(issues, userId);
        PrintWriter writer = new PrintWriter(
                new java.io.OutputStreamWriter(outputStream, java.nio.charset.StandardCharsets.UTF_8), true);
        String currentIssue = null;
        for (PredictRecord r : records) {
            if (!r.getIssue().equals(currentIssue)) {
                if (currentIssue != null) writer.println();
                writer.println("=== 期号：" + r.getIssue() + " ===");
                currentIssue = r.getIssue();
            }
            String reds = String.format("%02d %02d %02d %02d %02d %02d",
                    r.getRed1(), r.getRed2(), r.getRed3(),
                    r.getRed4(), r.getRed5(), r.getRed6());
            writer.println(reds + " | " + String.format("%02d", r.getBlue()));
        }
        writer.flush();
        log.info("导出预测号码，期号：{}，共 {} 条", issues, records.size());
    }

    /**
     * 分片批量计算预测记录命中结果（供异步多线程调用）
     *
     * @param issue    期号
     * @param idStart  分片起始 ID（包含）
     * @param idEnd    分片结束 ID（不包含）
     * @param lottery  开奖号码
     */
    public void calcBatch(String issue, long idStart, long idEnd, LotteryResult lottery) {
        List<PredictRecord> records = predictRecordMapper.selectList(
                new LambdaQueryWrapper<PredictRecord>()
                        .eq(PredictRecord::getIssue, issue)
                        .ge(PredictRecord::getId, idStart)
                        .lt(PredictRecord::getId, idEnd)
                        .isNull(PredictRecord::getPrizeLevel));
        if (records.isEmpty()) return;
        records.forEach(record -> calcAndFill(record, lottery));
        predictRecordMapper.batchUpdateHitResult(records);
    }

    /**
     * 全量重算分片（不过滤 NULL，重新计算所有记录）
     */
    public void recalcBatch(String issue, long idStart, long idEnd, LotteryResult lottery) {
        List<PredictRecord> records = predictRecordMapper.selectList(
                new LambdaQueryWrapper<PredictRecord>()
                        .eq(PredictRecord::getIssue, issue)
                        .ge(PredictRecord::getId, idStart)
                        .lt(PredictRecord::getId, idEnd));
        if (records.isEmpty()) return;
        records.forEach(record -> calcAndFill(record, lottery));
        predictRecordMapper.batchUpdateHitResult(records);
    }

    private void calcAndFill(PredictRecord record, LotteryResult lottery) {
        List<Integer> predictReds = Arrays.asList(
                record.getRed1(), record.getRed2(), record.getRed3(),
                record.getRed4(), record.getRed5(), record.getRed6());
        List<Integer> drawReds = Arrays.asList(
                lottery.getRed1(), lottery.getRed2(), lottery.getRed3(),
                lottery.getRed4(), lottery.getRed5(), lottery.getRed6());

        long hitRed = predictReds.stream().filter(drawReds::contains).count();
        boolean hitBlue = record.getBlue().equals(lottery.getBlue());

        PrizeLevel level = LotteryUtils.calcPrize(predictReds, record.getBlue(), drawReds, lottery.getBlue());
        record.setHitRed((int) hitRed);
        record.setHitBlue(hitBlue ? 1 : 0);
        record.setPrizeLevel(level.getLevel());
    }

    /** 从记录列表中收集所有非空 userId，批量查询用户名 */
    private Map<Long, String> buildUsernameMap(List<PredictRecord> records) {
        List<Long> userIds = records.stream()
                .map(PredictRecord::getUserId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) return new HashMap<>();
        List<User> users = userMapper.selectBatchIds(userIds);
        return users.stream().collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
    }

    private PredictRecordVO toVO(PredictRecord r) {
        return toVO(r, new HashMap<>(), new HashMap<>());
    }

    private PredictRecordVO toVO(PredictRecord r, Map<Long, String> usernameMap) {
        return toVO(r, usernameMap, new HashMap<>());
    }

    private PredictRecordVO toVO(PredictRecord r, Map<Long, String> usernameMap, Map<String, LotteryResult> lotteryMap) {
        String desc = r.getPrizeLevel() == null ? "待开奖"
                : PrizeLevel.ofLevel(r.getPrizeLevel()).getDesc();
        LotteryResult lottery = lotteryMap.get(r.getIssue());
        return PredictRecordVO.builder()
                .id(r.getId())
                .issue(r.getIssue())
                .reds(LotteryUtils.toRedList(r.getRed1(), r.getRed2(), r.getRed3(),
                        r.getRed4(), r.getRed5(), r.getRed6()))
                .blue(r.getBlue())
                .drawReds(lottery != null ? LotteryUtils.toRedList(lottery.getRed1(), lottery.getRed2(), lottery.getRed3(),
                        lottery.getRed4(), lottery.getRed5(), lottery.getRed6()) : null)
                .drawBlue(lottery != null ? lottery.getBlue() : null)
                .hitRed(r.getHitRed())
                .hitBlue(r.getHitBlue())
                .prizeLevel(r.getPrizeLevel())
                .prizeLevelDesc(desc)
                .createdAt(r.getCreatedAt())
                .sumVal(r.getSumVal())
                .zoneRatio(r.getZoneRatio())
                .oddEvenRatio(r.getOddEvenRatio())
                .rangeVal(r.getRangeVal())
                .username(r.getUserId() != null ? usernameMap.get(r.getUserId()) : null)
                .build();
    }
}
