package com.dcb.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcb.common.enums.PrizeLevel;
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
import java.util.*;

/**
 * 购买记录服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRecordMapper purchaseRecordMapper;
    private final LotteryService lotteryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 批量录入购买记录，录入后自动计算中奖等级
     */
    @Transactional(rollbackFor = Exception.class)
    public void add(List<PurchaseAddDTO> dtoList) {
        log.info("批量录入购买记录，共{}组", dtoList.size());
        for (PurchaseAddDTO dto : dtoList) {
            LotteryUtils.validateRed(Arrays.asList(
                    dto.getRed1(), dto.getRed2(), dto.getRed3(),
                    dto.getRed4(), dto.getRed5(), dto.getRed6()));
            LotteryUtils.validateBlue(dto.getBlue());

            PurchaseRecord record = PurchaseRecord.builder()
                    .issue(dto.getIssue())
                    .red1(dto.getRed1()).red2(dto.getRed2()).red3(dto.getRed3())
                    .red4(dto.getRed4()).red5(dto.getRed5()).red6(dto.getRed6())
                    .blue(dto.getBlue())
                    .ballKey(LotteryUtils.buildBallKey(dto))
                    .quantity(dto.getQuantity())
                    .remark(dto.getRemark())
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
        log.info("购买记录录入完成，共{}组", dtoList.size());
    }

    /**
     * 按 ID 列表强制重算中奖等级（无论是否已计算过）
     */
    @Transactional(rollbackFor = Exception.class)
    public int recalcByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        List<PurchaseRecord> records = purchaseRecordMapper.selectBatchIds(ids);
        if (records.isEmpty()) return 0;

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
    public void update(Long id, PurchaseUpdateDTO dto) {
        PurchaseRecord record = purchaseRecordMapper.selectById(id);
        if (record == null) {
            throw new IllegalArgumentException("购买记录不存在：" + id);
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
    public void delete(Long id) {
        purchaseRecordMapper.deleteById(id);
        log.info("删除购买记录，id：{}", id);
    }

    /**
     * 按期号删除该期所有购买记录，返回删除条数
     */
    public int deleteByIssue(String issue) {
        int count = purchaseRecordMapper.delete(
                new LambdaQueryWrapper<PurchaseRecord>().eq(PurchaseRecord::getIssue, issue));
        log.info("按期号删除购买记录，期号：{}，共删除 {} 条", issue, count);
        return count;
    }

    /**
     * 按 ID 列表批量删除购买记录，返回删除条数
     */
    public int deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
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
     * 分页查询购买记录
     */
    public PageResult<PurchaseRecordVO> list(String issue, List<Integer> prizeLevels, int page, int size) {
        log.info("Service.list: issue={}, prizeLevels={}, page={}, size={}", issue, prizeLevels, page, size);
        Page<PurchaseRecord> pageParam = new Page<>(page, size);
        Page<PurchaseRecord> result = (Page<PurchaseRecord>) purchaseRecordMapper
                .selectPageByCondition(pageParam, issue, prizeLevels);
        log.info("Service.list result: total={}, records={}", result.getTotal(), result.getRecords().size());

        List<PurchaseRecordVO> voList = new ArrayList<>();
        for (PurchaseRecord r : result.getRecords()) {
            voList.add(toVO(r));
        }
        return PageResult.of(result.getTotal(), voList);
    }

    /**
     * 汇总统计：总投入、总奖金、盈亏
     */
    public Map<String, Object> summary(String issue, List<Integer> prizeLevels) {
        Map<String, Object> raw = purchaseRecordMapper.selectSummary(issue, prizeLevels);
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

    private PurchaseRecordVO toVO(PurchaseRecord r) {
        String desc = r.getPrizeLevel() == null ? "待计算"
                : PrizeLevel.ofLevel(r.getPrizeLevel()).getDesc();
        // 获取开奖号码用于前端命中高亮
        LotteryResult lottery = lotteryService.getByIssue(r.getIssue());
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
                .build();
    }
}
