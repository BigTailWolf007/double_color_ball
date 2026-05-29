package com.dcb.predict.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcb.common.enums.PrizeLevel;
import com.dcb.common.result.PageResult;
import com.dcb.common.util.LotteryUtils;
import com.dcb.lottery.entity.LotteryResult;
import com.dcb.lottery.service.LotteryService;
import com.dcb.predict.dto.PredictSaveDTO;
import com.dcb.predict.entity.PredictRecord;
import com.dcb.predict.mapper.PredictRecordMapper;
import com.dcb.predict.vo.PredictRecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 预测号码服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PredictService {

    private final PredictRecordMapper predictRecordMapper;
    private final LotteryService lotteryService;

    /**
     * 保存预测号码，若该期已有开奖号码则立即计算命中结果
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(List<PredictSaveDTO> dtoList) {
        log.info("保存预测号码，共{}组", dtoList.size());
        for (PredictSaveDTO dto : dtoList) {
            LotteryUtils.validateRed(Arrays.asList(
                    dto.getRed1(), dto.getRed2(), dto.getRed3(),
                    dto.getRed4(), dto.getRed5(), dto.getRed6()));
            LotteryUtils.validateBlue(dto.getBlue());

            PredictRecord record = PredictRecord.builder()
                    .issue(dto.getIssue())
                    .red1(dto.getRed1()).red2(dto.getRed2()).red3(dto.getRed3())
                    .red4(dto.getRed4()).red5(dto.getRed5()).red6(dto.getRed6())
                    .blue(dto.getBlue())
                    .build();

            LotteryResult lottery = lotteryService.getByIssue(dto.getIssue());
            if (lottery != null) {
                calcAndFill(record, lottery);
                log.debug("期号 {} 已开奖，立即计算命中结果：{}", dto.getIssue(),
                        PrizeLevel.ofLevel(record.getPrizeLevel()).getDesc());
            } else {
                log.debug("期号 {} 暂无开奖号码，待后续补算", dto.getIssue());
            }
            predictRecordMapper.insert(record);
        }
        log.info("预测号码保存完成，共{}组", dtoList.size());
    }

    /**
     * 手动触发补算指定期号所有待开奖预测记录的命中结果
     */
    @Transactional(rollbackFor = Exception.class)
    public int calc(String issue) {
        log.info("开始补算期号 {} 的预测命中结果", issue);
        LotteryResult lottery = lotteryService.getByIssue(issue);
        if (lottery == null) {
            log.warn("期号 {} 暂无开奖号码，无法补算", issue);
            return 0;
        }
        List<PredictRecord> records = predictRecordMapper.selectList(
                new LambdaQueryWrapper<PredictRecord>()
                        .eq(PredictRecord::getIssue, issue)
                        .isNull(PredictRecord::getPrizeLevel));
        if (records.isEmpty()) {
            return 0;
        }
        records.forEach(record -> calcAndFill(record, lottery));
        predictRecordMapper.batchUpdateHitResult(records);
        log.info("期号 {} 预测补算完成，共更新 {} 条记录", issue, records.size());
        return records.size();
    }

    /**
     * 分页查询预测号码
     */
    public PageResult<PredictRecordVO> list(String issue, int page, int size) {
        Page<PredictRecord> pageParam = new Page<>(page, size);
        Page<PredictRecord> result = (Page<PredictRecord>) predictRecordMapper
                .selectPageByIssue(pageParam, issue);

        List<PredictRecordVO> voList = new ArrayList<>();
        for (PredictRecord r : result.getRecords()) {
            voList.add(toVO(r));
        }
        return PageResult.of(result.getTotal(), voList);
    }

    /**
     * 按 ID 删除单条预测记录
     */
    public void deleteById(Long id) {
        predictRecordMapper.deleteById(id);
        log.info("删除预测记录，id：{}", id);
    }

    /**
     * 按期号删除该期所有预测记录
     */
    public int deleteByIssue(String issue) {
        int count = predictRecordMapper.delete(
                new LambdaQueryWrapper<PredictRecord>()
                        .eq(PredictRecord::getIssue, issue));
        log.info("按期号删除预测记录，期号：{}，共删除 {} 条", issue, count);
        return count;
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

    private PredictRecordVO toVO(PredictRecord r) {
        String desc = r.getPrizeLevel() == null ? "待开奖"
                : PrizeLevel.ofLevel(r.getPrizeLevel()).getDesc();
        return PredictRecordVO.builder()
                .id(r.getId())
                .issue(r.getIssue())
                .reds(LotteryUtils.toRedList(r.getRed1(), r.getRed2(), r.getRed3(),
                        r.getRed4(), r.getRed5(), r.getRed6()))
                .blue(r.getBlue())
                .hitRed(r.getHitRed())
                .hitBlue(r.getHitBlue())
                .prizeLevel(r.getPrizeLevel())
                .prizeLevelDesc(desc)
                .createdAt(r.getCreatedAt())
                .build();
    }
}
