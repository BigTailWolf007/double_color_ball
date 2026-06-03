package com.dcb.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcb.auth.entity.User;
import com.dcb.auth.mapper.UserMapper;
import com.dcb.auth.service.AuthService;
import com.dcb.common.enums.PrizeLevel;
import com.dcb.common.exception.BizException;
import com.dcb.common.result.PageResult;
import com.dcb.common.util.LotteryUtils;
import com.dcb.lottery.entity.LotteryResult;
import com.dcb.lottery.service.LotteryService;
import com.dcb.purchase.dto.PurchaseAddDTO;
import com.dcb.purchase.dto.PurchaseUpdateDTO;
import com.dcb.purchase.entity.PurchaseRecord;
import com.dcb.purchase.mapper.PurchaseRecordMapper;
import com.dcb.purchase.vo.PurchaseRecordVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 购买记录服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRecordMapper purchaseRecordMapper;
    private final LotteryService lotteryService;
    private final AuthService authService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 批量录入购买记录，录入后自动计算中奖等级
     */
    @Transactional(rollbackFor = Exception.class)
    public void add(List<PurchaseAddDTO> dtoList, Long userId) {
        log.info("批量录入购买记录，共{}组，userId={}", dtoList.size(), userId);

        // 查询当前用户已有 ballKey（含 admin 公共数据 NULL），用于去重
        Set<String> existingKeys = new HashSet<>();
        for (PurchaseAddDTO dto : dtoList) {
            String allIssue = dto.getIssue();
            if (allIssue != null && !allIssue.isEmpty()) {
                List<String> keys = purchaseRecordMapper.selectBallKeysByIssue(allIssue);
                // 过滤：只排除当前用户自己的和 NULL（公共）的 ballKey
                for (String k : keys) {
                    existingKeys.add(k);
                }
            }
        }

        int skip = 0;
        for (PurchaseAddDTO dto : dtoList) {
            LotteryUtils.validateRed(Arrays.asList(
                    dto.getRed1(), dto.getRed2(), dto.getRed3(),
                    dto.getRed4(), dto.getRed5(), dto.getRed6()));
            LotteryUtils.validateBlue(dto.getBlue());

            String ballKey = LotteryUtils.buildBallKey(dto);
            // 去重：同一用户已存在相同号码则跳过
            if (!existingKeys.add(ballKey)) {
                skip++;
                continue;
            }

            PurchaseRecord record = PurchaseRecord.builder()
                    .issue(dto.getIssue())
                    .red1(dto.getRed1()).red2(dto.getRed2()).red3(dto.getRed3())
                    .red4(dto.getRed4()).red5(dto.getRed5()).red6(dto.getRed6())
                    .blue(dto.getBlue())
                    .ballKey(ballKey)
                    .quantity(dto.getQuantity())
                    .remark(dto.getRemark())
                    .userId(dto.getUserId() != null ? dto.getUserId() : userId)
                    .sumVal(LotteryUtils.calcSum(dto.getRed1(), dto.getRed2(), dto.getRed3(),
                            dto.getRed4(), dto.getRed5(), dto.getRed6()))
                    .zoneRatio(LotteryUtils.calcZoneRatio(dto.getRed1(), dto.getRed2(), dto.getRed3(),
                            dto.getRed4(), dto.getRed5(), dto.getRed6()))
                    .oddEvenRatio(LotteryUtils.calcOddEvenRatio(dto.getRed1(), dto.getRed2(), dto.getRed3(),
                            dto.getRed4(), dto.getRed5(), dto.getRed6()))
                    .rangeVal(LotteryUtils.calcRange(dto.getRed1(), dto.getRed2(), dto.getRed3(),
                            dto.getRed4(), dto.getRed5(), dto.getRed6()))
                    .build();

            LotteryResult lottery = lotteryService.getByIssue(dto.getIssue());
            if (lottery != null) {
                calcAndFill(record, lottery);
                log.debug("期号 {} 已开奖，自动计算中奖等级：{}", dto.getIssue(),
                        PrizeLevel.ofLevel(record.getPrizeLevel()).getDesc());
            } else {
                log.debug("期号 {} 暂无开奖号码，待后续补算", dto.getIssue());
            }
            purchaseRecordMapper.insert(record);
        }
        log.info("购买记录录入完成，共{}组，跳过重复{}组", dtoList.size() - skip, skip);
    }

    /**
     * 按 ID 列表强制重算中奖等级。管理员可重算全部，普通用户仅能重算自己的
     */
    @Transactional(rollbackFor = Exception.class)
    public int recalcByIds(List<Long> ids, Long userId, String role) {
        if (ids == null || ids.isEmpty()) return 0;
        List<PurchaseRecord> records = purchaseRecordMapper.selectBatchIds(ids);
        if (records.isEmpty()) return 0;
        // 非管理员校验：只能重算自己的记录
        if (!"ADMIN".equals(role)) {
            for (PurchaseRecord r : records) {
                if (r.getUserId() != null && !r.getUserId().equals(userId)) {
                    throw new BizException("无权重算他人记录");
                }
            }
        }

        // 按期号分组，批量查开奖号码
        Map<String, LotteryResult> lotteryCache = new HashMap<>();
        List<PurchaseRecord> toUpdate = new ArrayList<>();
        for (PurchaseRecord record : records) {
            LotteryResult lottery = lotteryCache.computeIfAbsent(
                    record.getIssue(), lotteryService::getByIssue);
            if (lottery == null) continue;
            calcAndFill(record, lottery);
            toUpdate.add(record);
        }
        if (!toUpdate.isEmpty()) {
            purchaseRecordMapper.batchUpdatePrize(toUpdate);
        }
        log.info("按ID重算完成，共更新 {} 条记录", toUpdate.size());
        return toUpdate.size();
    }

    /**
     * 补算指定期号所有未计算记录的中奖等级
     */
    @Transactional(rollbackFor = Exception.class)
    public int calc(String issue) {
        log.info("开始补算期号 {} 的中奖等级", issue);
        LotteryResult lottery = lotteryService.getByIssue(issue);
        if (lottery == null) {
            log.warn("期号 {} 暂无开奖号码，无法补算", issue);
            return 0;
        }
        List<PurchaseRecord> records = purchaseRecordMapper.selectList(
                new LambdaQueryWrapper<PurchaseRecord>()
                        .eq(PurchaseRecord::getIssue, issue)
                        .isNull(PurchaseRecord::getPrizeLevel));
        if (records.isEmpty()) {
            return 0;
        }
        records.forEach(record -> calcAndFill(record, lottery));
        purchaseRecordMapper.batchUpdatePrize(records);
        log.info("期号 {} 补算完成，共更新 {} 条记录", issue, records.size());
        return records.size();
    }

    /**
     * 编辑购买记录（仅允许修改注数和备注），注数变更后重新计算奖金
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PurchaseUpdateDTO dto, Long userId, String role) {
        PurchaseRecord record = purchaseRecordMapper.selectById(id);
        if (record == null) {
            throw new IllegalArgumentException("购买记录不存在：" + id);
        }
        if (!"ADMIN".equals(role) && record.getUserId() != null && !record.getUserId().equals(userId)) {
            throw new BizException("无权编辑他人记录");
        }
        int oldQuantity = record.getQuantity();
        record.setQuantity(dto.getQuantity());
        record.setRemark(dto.getRemark());

        // 注数变更后按比例重新计算奖金
        if (record.getPrizeLevel() != null && record.getPrizeMoney() != null) {
            BigDecimal perUnit = record.getPrizeMoney().divide(
                    BigDecimal.valueOf(oldQuantity), 2, RoundingMode.HALF_UP);
            record.setPrizeMoney(perUnit.multiply(BigDecimal.valueOf(dto.getQuantity())));
        } else {
            record.setPrizeMoney(null);
        }
        purchaseRecordMapper.updateById(record);
        log.info("编辑购买记录，id：{}，注数：{}，备注：{}", id, dto.getQuantity(), dto.getRemark());
    }

    /**
     * 删除购买记录
     */
    public void delete(Long id, Long userId, String role) {
        PurchaseRecord record = purchaseRecordMapper.selectById(id);
        if (record == null) throw new BizException("购买记录不存在");
        if (!"ADMIN".equals(role) && record.getUserId() != null && !record.getUserId().equals(userId)) {
            throw new BizException("无权删除他人记录");
        }
        purchaseRecordMapper.deleteById(id);
        log.info("删除购买记录，id：{}", id);
    }

    /**
     * 按期号删除该期所有购买记录。userId 为 null 则删除全部，否则仅删除该用户的
     */
    public int deleteByIssue(String issue, Long userId) {
        LambdaQueryWrapper<PurchaseRecord> wrapper = new LambdaQueryWrapper<PurchaseRecord>()
                .eq(PurchaseRecord::getIssue, issue);
        if (userId != null) {
            wrapper.eq(PurchaseRecord::getUserId, userId);
        }
        int count = purchaseRecordMapper.delete(wrapper);
        log.info("按期号删除购买记录，期号：{}，userId：{}，共删除 {} 条", issue, userId, count);
        return count;
    }

    /**
     * 按 ID 列表批量删除购买记录。管理员可删全部，普通用户仅能删自己的
     */
    public int deleteByIds(List<Long> ids, Long userId, String role) {
        if (ids == null || ids.isEmpty()) return 0;
        // 非管理员校验：只能删除自己的记录
        if (!"ADMIN".equals(role)) {
            List<PurchaseRecord> records = purchaseRecordMapper.selectBatchIds(ids);
            for (PurchaseRecord r : records) {
                if (r.getUserId() != null && !r.getUserId().equals(userId)) {
                    throw new BizException("无权删除他人记录");
                }
            }
        }
        int count = purchaseRecordMapper.deleteBatchIds(ids);
        log.info("批量删除购买记录，共删除 {} 条", count);
        return count;
    }

    /**
     * 模糊查询期号，倒序返回最多10个
     */
    public List<String> suggestIssues(String keyword) {
        return purchaseRecordMapper.selectIssuesByKeyword(keyword == null ? "" : keyword, 10);
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
     * 分页查询购买记录
     */
    public PageResult<PurchaseRecordVO> list(String issue, List<Integer> prizeLevels, int page, int size, Long userId) {
        log.info("Service.list: issue={}, prizeLevels={}, page={}, size={}, userId={}", issue, prizeLevels, page, size, userId);
        // 提取 -1（待计算），转换为 IS NULL 查询
        boolean includeNullPrize = prizeLevels != null && prizeLevels.remove(Integer.valueOf(-1));
        Page<PurchaseRecord> pageParam = new Page<>(page, size);
        Page<PurchaseRecord> result = (Page<PurchaseRecord>) purchaseRecordMapper
                .selectPageByCondition(pageParam, issue, prizeLevels, includeNullPrize, userId);
        log.info("Service.list result: total={}, records={}", result.getTotal(), result.getRecords().size());

        List<PurchaseRecord> records = result.getRecords();

        // 批量预加载：用户名 + 开奖号码
        Map<Long, String> usernameMap = buildUsernameMap(records);
        Map<String, LotteryResult> lotteryMap = lotteryService.getByIssues(
                records.stream().map(PurchaseRecord::getIssue).distinct().collect(Collectors.toList()));

        List<PurchaseRecordVO> voList = new ArrayList<>();
        for (PurchaseRecord r : records) {
            voList.add(toVO(r, usernameMap, lotteryMap));
        }
        return PageResult.of(result.getTotal(), voList);
    }

    /**
     * 汇总统计：总投入、总奖金、盈亏
     */
    public Map<String, Object> summary(String issue, List<Integer> prizeLevels, Long userId) {
        boolean includeNullPrize = prizeLevels != null && prizeLevels.remove(Integer.valueOf(-1));
        Map<String, Object> raw = purchaseRecordMapper.selectSummary(issue, prizeLevels, includeNullPrize, userId);
        if (raw == null) {
            raw = new HashMap<>();
        }
        long totalQuantity = raw.get("totalQuantity") == null ? 0L
                : ((Number) raw.get("totalQuantity")).longValue();
        BigDecimal totalPrizeMoney = raw.get("totalPrizeMoney") == null ? BigDecimal.ZERO
                : new BigDecimal(raw.get("totalPrizeMoney").toString());

        BigDecimal totalCost = BigDecimal.valueOf(totalQuantity * 2);
        BigDecimal profit = totalPrizeMoney.subtract(totalCost);

        Map<String, Object> result = new HashMap<>();
        result.put("totalCost", totalCost);
        result.put("totalPrizeMoney", totalPrizeMoney);
        result.put("profit", profit);
        return result;
    }

    /**
     * 分片批量计算购买记录奖金（供异步多线程调用）
     *
     * @param issue    期号
     * @param idStart  分片起始 ID（包含）
     * @param idEnd    分片结束 ID（不包含）
     * @param lottery  开奖号码
     */
    public void calcBatch(String issue, long idStart, long idEnd, LotteryResult lottery) {
        List<PurchaseRecord> records = purchaseRecordMapper.selectList(
                new LambdaQueryWrapper<PurchaseRecord>()
                        .eq(PurchaseRecord::getIssue, issue)
                        .ge(PurchaseRecord::getId, idStart)
                        .lt(PurchaseRecord::getId, idEnd)
                        .isNull(PurchaseRecord::getPrizeLevel));
        if (records.isEmpty()) return;
        records.forEach(record -> calcAndFill(record, lottery));
        purchaseRecordMapper.batchUpdatePrize(records);
    }

    private void calcAndFill(PurchaseRecord record, LotteryResult lottery) {
        List<Integer> buyReds = LotteryUtils.toRedList(
                record.getRed1(), record.getRed2(), record.getRed3(),
                record.getRed4(), record.getRed5(), record.getRed6());
        List<Integer> drawReds = LotteryUtils.toRedList(
                lottery.getRed1(), lottery.getRed2(), lottery.getRed3(),
                lottery.getRed4(), lottery.getRed5(), lottery.getRed6());

        PrizeLevel level = LotteryUtils.calcPrize(buyReds, record.getBlue(), drawReds, lottery.getBlue());
        record.setPrizeLevel(level.getLevel());

        // 从接口奖品数据中查找实际单注奖金（优先使用 API 实际值，其次用枚举固定值）
        Map<String, Long> prizeMap = parsePrizeMap(lottery.getPrizeJson());
        Long apiBonus = prizeMap.get(level.getDesc());
        BigDecimal singleBonus;
        if (apiBonus != null && apiBonus > 0) {
            singleBonus = BigDecimal.valueOf(apiBonus);
        } else {
            singleBonus = BigDecimal.valueOf(level.getFixedPrize());
        }
        record.setPrizeMoney(singleBonus.multiply(BigDecimal.valueOf(record.getQuantity())));
    }

    /**
     * 解析奖品 JSON，返回 奖品名 → 单注奖金 映射
     */
    private Map<String, Long> parsePrizeMap(String prizeJson) {
        if (prizeJson == null || prizeJson.isEmpty() || "[]".equals(prizeJson)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Long> map = new HashMap<>();
            JsonNode arr = objectMapper.readTree(prizeJson);
            if (arr.isArray()) {
                for (JsonNode p : arr) {
                    String name = p.path("prizename").asText("");
                    long bonus = p.path("singlebonus").asLong(0);
                    if (!name.isEmpty()) {
                        map.put(name, bonus);
                    }
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("解析奖品JSON失败：{}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /** 从记录列表中收集所有非空 userId，批量查询用户名 */
    private Map<Long, String> buildUsernameMap(List<PurchaseRecord> records) {
        List<Long> userIds = records.stream()
                .map(PurchaseRecord::getUserId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) return new HashMap<>();
        List<User> users = userMapper.selectBatchIds(userIds);
        return users.stream().collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
    }

    private PurchaseRecordVO toVO(PurchaseRecord r) {
        return toVO(r, new HashMap<>(), new HashMap<>());
    }

    private PurchaseRecordVO toVO(PurchaseRecord r, Map<Long, String> usernameMap) {
        return toVO(r, usernameMap, new HashMap<>());
    }

    private PurchaseRecordVO toVO(PurchaseRecord r, Map<Long, String> usernameMap, Map<String, LotteryResult> lotteryMap) {
        String desc = r.getPrizeLevel() == null ? "待计算"
                : PrizeLevel.ofLevel(r.getPrizeLevel()).getDesc();
        LotteryResult lottery = lotteryMap.get(r.getIssue());
        return PurchaseRecordVO.builder()
                .id(r.getId())
                .issue(r.getIssue())
                .reds(LotteryUtils.toRedList(r.getRed1(), r.getRed2(), r.getRed3(),
                        r.getRed4(), r.getRed5(), r.getRed6()))
                .blue(r.getBlue())
                .drawReds(lottery != null ? LotteryUtils.toRedList(lottery.getRed1(), lottery.getRed2(), lottery.getRed3(),
                        lottery.getRed4(), lottery.getRed5(), lottery.getRed6()) : null)
                .drawBlue(lottery != null ? lottery.getBlue() : null)
                .quantity(r.getQuantity())
                .prizeLevel(r.getPrizeLevel())
                .prizeLevelDesc(desc)
                .prizeMoney(r.getPrizeMoney())
                .remark(r.getRemark())
                .createdAt(r.getCreatedAt())
                .sumVal(r.getSumVal())
                .zoneRatio(r.getZoneRatio())
                .oddEvenRatio(r.getOddEvenRatio())
                .rangeVal(r.getRangeVal())
                .username(r.getUserId() != null ? usernameMap.get(r.getUserId()) : null)
                .build();
    }
}
