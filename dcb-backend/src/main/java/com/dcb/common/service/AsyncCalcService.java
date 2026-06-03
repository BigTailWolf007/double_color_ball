package com.dcb.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dcb.calcerror.entity.CalcErrorLog;
import com.dcb.calcerror.mapper.CalcErrorLogMapper;
import com.dcb.lottery.entity.LotteryResult;
import com.dcb.lottery.service.LotteryService;
import com.dcb.predict.entity.PredictRecord;
import com.dcb.predict.mapper.PredictRecordMapper;
import com.dcb.predict.service.PredictService;
import com.dcb.purchase.entity.PurchaseRecord;
import com.dcb.purchase.mapper.PurchaseRecordMapper;
import com.dcb.purchase.service.PurchaseService;
import com.dcb.common.config.service.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 异步多线程计算服务
 * 将中奖计算按 ID 范围分片，提交到线程池并行执行
 */
@Slf4j
@Service
public class AsyncCalcService {

    private final PurchaseRecordMapper purchaseRecordMapper;
    private final PredictRecordMapper predictRecordMapper;
    private final LotteryService lotteryService;
    private final PurchaseService purchaseService;
    private final PredictService predictService;
    private final CalcErrorLogMapper calcErrorLogMapper;
    private final PlatformTransactionManager transactionManager;
    private final Executor executor;
    private final ConfigService configService;

    public AsyncCalcService(
            PurchaseRecordMapper purchaseRecordMapper,
            PredictRecordMapper predictRecordMapper,
            LotteryService lotteryService,
            PurchaseService purchaseService,
            PredictService predictService,
            CalcErrorLogMapper calcErrorLogMapper,
            PlatformTransactionManager transactionManager,
            Executor executor,
            ConfigService configService) {
        this.purchaseRecordMapper = purchaseRecordMapper;
        this.predictRecordMapper = predictRecordMapper;
        this.lotteryService = lotteryService;
        this.purchaseService = purchaseService;
        this.predictService = predictService;
        this.calcErrorLogMapper = calcErrorLogMapper;
        this.transactionManager = transactionManager;
        this.executor = executor;
        this.configService = configService;
    }

    /**
     * 异步计算指定期号的购买记录
     */
    @Async("calcTaskExecutor")
    public void asyncCalcPurchase(String issue) {
        asyncCalc(issue, "purchase");
    }

    /**
     * 异步计算指定期号的预测记录
     */
    @Async("calcTaskExecutor")
    public void asyncCalcPredict(String issue) {
        asyncCalc(issue, "predict");
    }

    /**
     * 异步重新计算指定期号的预测记录（全量，不限于 NULL）
     */
    @Async("calcTaskExecutor")
    public void asyncRecalcPredict(String issue) {
        asyncRecalc(issue, "predict");
    }

    /**
     * 按错误日志记录重新执行分片计算
     */
    @Async("calcTaskExecutor")
    public void retryErrorLog(Long errorLogId) {
        CalcErrorLog errorLog = calcErrorLogMapper.selectById(errorLogId);
        if (errorLog == null) {
            log.warn("错误日志不存在，id：{}", errorLogId);
            return;
        }
        LotteryResult lottery = lotteryService.getByIssue(errorLog.getIssue());
        if (lottery == null) {
            log.warn("期号 {} 暂无开奖号码，无法重试", errorLog.getIssue());
            return;
        }

        log.info("重试错误日志，id：{}，期号：{}，类型：{}，区间：[{}, {})",
                errorLogId, errorLog.getIssue(), errorLog.getCalcType(),
                errorLog.getIdStart(), errorLog.getIdEnd());

        try {
            executeShardWithRetry(errorLog.getIssue(), errorLog.getCalcType(),
                    errorLog.getIdStart(), errorLog.getIdEnd(), 0, 1, lottery, false);
            // 成功后更新状态
            errorLog.setStatus(1);
            errorLog.setRetryCount(errorLog.getRetryCount() + 1);
            calcErrorLogMapper.updateById(errorLog);
            log.info("错误日志重试成功，id：{}", errorLogId);
        } catch (Exception e) {
            errorLog.setRetryCount(errorLog.getRetryCount() + 1);
            errorLog.setErrorMsg("重试失败：" + e.getMessage());
            calcErrorLogMapper.updateById(errorLog);
            log.error("错误日志重试失败，id：{}", errorLogId, e);
        }
    }

    /**
     * 通用异步计算：分片 → 提交 → 等待完成
     */
    private void asyncCalc(String issue, String calcType) {
        asyncCalcInternal(issue, calcType, false);
    }

    private void asyncRecalc(String issue, String calcType) {
        asyncCalcInternal(issue, calcType, true);
    }

    /**
     * 通用异步计算：分片 → 提交 → 等待完成
     * @param recalc true=全量重算，false=仅算NULL记录
     */
    private void asyncCalcInternal(String issue, String calcType, boolean recalc) {
        LotteryResult lottery = lotteryService.getByIssue(issue);
        if (lottery == null) {
            log.warn("期号 {} 暂无开奖号码，跳过{}计算", issue, calcType);
            return;
        }

        // 查询待计算记录的最小/最大 ID
        Long minId, maxId;
        if ("purchase".equals(calcType)) {
            minId = recalc ? getAllMinId(issue, PurchaseRecord.class) : getMinId(issue, PurchaseRecord.class);
            maxId = recalc ? getAllMaxId(issue, PurchaseRecord.class) : getMaxId(issue, PurchaseRecord.class);
        } else {
            minId = recalc ? getAllMinId(issue, PredictRecord.class) : getMinId(issue, PredictRecord.class);
            maxId = recalc ? getAllMaxId(issue, PredictRecord.class) : getMaxId(issue, PredictRecord.class);
        }

        if (minId == null || maxId == null) {
            log.info("期号 {} 无待计算{}记录", issue, calcType);
            return;
        }

        int totalShards = (int) ((maxId - minId) / configService.getInt("async.shard.size")) + 1;
        log.info("开始异步{}计算{}记录，期号：{}，ID范围：[{}, {})，分片数：{}",
                recalc ? "全量" : "", calcType, issue, minId, maxId, totalShards);

        long startTime = System.currentTimeMillis();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < totalShards; i++) {
            final long start = minId + (long) i * configService.getInt("async.shard.size");
            final long end = start + configService.getInt("async.shard.size");
            final int shardIndex = i;

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                executeShardWithRetry(issue, calcType, start, end, shardIndex, totalShards, lottery, recalc);
            }, executor);
            futures.add(future);
        }

        // 等待所有分片完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("异步计算{}记录完成，期号：{}，分片数：{}，总耗时：{}ms",
                calcType, issue, totalShards, elapsed);
    }

    /**
     * 执行单个分片（带重试），内层用 TransactionTemplate 管理事务
     */
    private void executeShardWithRetry(String issue, String calcType, long idStart, long idEnd,
                                        int shardIndex, int totalShards, LotteryResult lottery, boolean recalc) {
        Exception lastException = null;
        for (int retry = 0; retry <= configService.getInt("async.max.retries"); retry++) {
            try {
                TransactionTemplate tx = new TransactionTemplate(transactionManager);
                tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                tx.executeWithoutResult(status -> {
                    if ("purchase".equals(calcType)) {
                        purchaseService.calcBatch(issue, idStart, idEnd, lottery);
                    } else if (recalc) {
                        predictService.recalcBatch(issue, idStart, idEnd, lottery);
                    } else {
                        predictService.calcBatch(issue, idStart, idEnd, lottery);
                    }
                });
                if (retry > 0) {
                    log.info("分片 [{}/{}] 重试成功（第{}次），区间：[{}, {})",
                            shardIndex + 1, totalShards, retry, idStart, idEnd);
                }
                return; // 成功，退出
            } catch (Exception e) {
                lastException = e;
                if (retry < configService.getInt("async.max.retries")) {
                    long delay = (long) Math.pow(2, retry) * 1000L; // 1s, 2s, 4s
                    log.warn("分片 [{}/{}] 失败，区间：[{}, {})，{}ms后重试({}/{})：{}",
                            shardIndex + 1, totalShards, idStart, idEnd,
                            delay, retry + 1, configService.getInt("async.max.retries"), e.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // 全部重试失败，写入错误日志表
        String errorMsg = lastException != null ? lastException.getMessage() : "未知错误";
        log.error("分片 [{}/{}] 全部重试失败，区间：[{}, {})，错误：{}",
                shardIndex + 1, totalShards, idStart, idEnd, errorMsg);

        CalcErrorLog errorLog = CalcErrorLog.builder()
                .issue(issue)
                .calcType(calcType)
                .idStart(idStart)
                .idEnd(idEnd)
                .errorMsg(errorMsg.length() > 2000 ? errorMsg.substring(0, 2000) : errorMsg)
                .status(0)
                .retryCount(configService.getInt("async.max.retries"))
                .build();
        calcErrorLogMapper.insert(errorLog);
    }

    /** 查询待计算记录最小 ID */
    private Long getMinId(String issue, Class<?> entityClass) {
        if (entityClass == PurchaseRecord.class) {
            PurchaseRecord r = purchaseRecordMapper.selectOne(
                    new LambdaQueryWrapper<PurchaseRecord>()
                            .eq(PurchaseRecord::getIssue, issue)
                            .isNull(PurchaseRecord::getPrizeLevel)
                            .orderByAsc(PurchaseRecord::getId)
                            .last("LIMIT 1"));
            return r != null ? r.getId() : null;
        } else {
            PredictRecord r = predictRecordMapper.selectOne(
                    new LambdaQueryWrapper<PredictRecord>()
                            .eq(PredictRecord::getIssue, issue)
                            .isNull(PredictRecord::getPrizeLevel)
                            .orderByAsc(PredictRecord::getId)
                            .last("LIMIT 1"));
            return r != null ? r.getId() : null;
        }
    }

    /** 查询待计算记录最大 ID */
    private Long getMaxId(String issue, Class<?> entityClass) {
        if (entityClass == PurchaseRecord.class) {
            PurchaseRecord r = purchaseRecordMapper.selectOne(
                    new LambdaQueryWrapper<PurchaseRecord>()
                            .eq(PurchaseRecord::getIssue, issue)
                            .isNull(PurchaseRecord::getPrizeLevel)
                            .orderByDesc(PurchaseRecord::getId)
                            .last("LIMIT 1"));
            return r != null ? r.getId() : null;
        } else {
            PredictRecord r = predictRecordMapper.selectOne(
                    new LambdaQueryWrapper<PredictRecord>()
                            .eq(PredictRecord::getIssue, issue)
                            .isNull(PredictRecord::getPrizeLevel)
                            .orderByDesc(PredictRecord::getId)
                            .last("LIMIT 1"));
            return r != null ? r.getId() : null;
        }
    }

    /** 查询全量最小 ID（不过滤 NULL） */
    private Long getAllMinId(String issue, Class<?> entityClass) {
        if (entityClass == PurchaseRecord.class) {
            PurchaseRecord r = purchaseRecordMapper.selectOne(
                    new LambdaQueryWrapper<PurchaseRecord>()
                            .eq(PurchaseRecord::getIssue, issue)
                            .orderByAsc(PurchaseRecord::getId)
                            .last("LIMIT 1"));
            return r != null ? r.getId() : null;
        } else {
            PredictRecord r = predictRecordMapper.selectOne(
                    new LambdaQueryWrapper<PredictRecord>()
                            .eq(PredictRecord::getIssue, issue)
                            .orderByAsc(PredictRecord::getId)
                            .last("LIMIT 1"));
            return r != null ? r.getId() : null;
        }
    }

    /** 查询全量最大 ID（不过滤 NULL） */
    private Long getAllMaxId(String issue, Class<?> entityClass) {
        if (entityClass == PurchaseRecord.class) {
            PurchaseRecord r = purchaseRecordMapper.selectOne(
                    new LambdaQueryWrapper<PurchaseRecord>()
                            .eq(PurchaseRecord::getIssue, issue)
                            .orderByDesc(PurchaseRecord::getId)
                            .last("LIMIT 1"));
            return r != null ? r.getId() : null;
        } else {
            PredictRecord r = predictRecordMapper.selectOne(
                    new LambdaQueryWrapper<PredictRecord>()
                            .eq(PredictRecord::getIssue, issue)
                            .orderByDesc(PredictRecord::getId)
                            .last("LIMIT 1"));
            return r != null ? r.getId() : null;
        }
    }
}
