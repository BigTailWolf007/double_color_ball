package com.dcb.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcb.common.enums.PrizeLevel;
import com.dcb.common.result.PageResult;
import com.dcb.common.util.LotteryUtils;
import com.dcb.lottery.entity.LotteryResult;
import com.dcb.lottery.service.LotteryService;
import com.dcb.purchase.dto.PurchaseAddDTO;
import com.dcb.purchase.entity.PurchaseRecord;
import com.dcb.purchase.mapper.PurchaseRecordMapper;
import com.dcb.purchase.vo.PurchaseRecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 购买记录服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRecordMapper purchaseRecordMapper;
    private final LotteryService lotteryService;

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
     * 删除购买记录
     */
    public void delete(Long id) {
        purchaseRecordMapper.deleteById(id);
        log.info("删除购买记录，id：{}", id);
    }

    /**
     * 分页查询购买记录
     */
    public PageResult<PurchaseRecordVO> list(String issue, Integer prizeLevel, int page, int size) {
        Page<PurchaseRecord> pageParam = new Page<>(page, size);
        Page<PurchaseRecord> result = (Page<PurchaseRecord>) purchaseRecordMapper
                .selectPageByCondition(pageParam, issue, prizeLevel);

        List<PurchaseRecordVO> voList = new ArrayList<>();
        for (PurchaseRecord r : result.getRecords()) {
            voList.add(toVO(r));
        }
        return PageResult.of(result.getTotal(), voList);
    }

    /**
     * 汇总统计：总投入、总奖金、盈亏
     */
    public Map<String, Object> summary() {
        Map<String, Object> raw = purchaseRecordMapper.selectSummary();
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

    private void calcAndFill(PurchaseRecord record, LotteryResult lottery) {
        List<Integer> buyReds = Arrays.asList(
                record.getRed1(), record.getRed2(), record.getRed3(),
                record.getRed4(), record.getRed5(), record.getRed6());
        List<Integer> drawReds = Arrays.asList(
                lottery.getRed1(), lottery.getRed2(), lottery.getRed3(),
                lottery.getRed4(), lottery.getRed5(), lottery.getRed6());

        PrizeLevel level = LotteryUtils.calcPrize(buyReds, record.getBlue(), drawReds, lottery.getBlue());
        record.setPrizeLevel(level.getLevel());
        BigDecimal fixedPrize = BigDecimal.valueOf(level.getFixedPrize());
        record.setPrizeMoney(fixedPrize.multiply(BigDecimal.valueOf(record.getQuantity())));
    }

    private PurchaseRecordVO toVO(PurchaseRecord r) {
        String desc = r.getPrizeLevel() == null ? "待计算"
                : PrizeLevel.ofLevel(r.getPrizeLevel()).getDesc();
        return PurchaseRecordVO.builder()
                .id(r.getId())
                .issue(r.getIssue())
                .reds(LotteryUtils.toRedList(r.getRed1(), r.getRed2(), r.getRed3(),
                        r.getRed4(), r.getRed5(), r.getRed6()))
                .blue(r.getBlue())
                .quantity(r.getQuantity())
                .prizeLevel(r.getPrizeLevel())
                .prizeLevelDesc(desc)
                .prizeMoney(r.getPrizeMoney())
                .remark(r.getRemark())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
