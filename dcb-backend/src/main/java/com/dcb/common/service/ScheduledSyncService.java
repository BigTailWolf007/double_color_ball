package com.dcb.common.service;

import com.dcb.common.config.event.ConfigChangedEvent;
import com.dcb.common.config.service.ConfigService;
import com.dcb.lottery.dto.LotterySyncDTO;
import com.dcb.lottery.service.LotteryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.ScheduledFuture;

/**
 * 定时同步服务（动态 cron，从 ConfigService 读取）
 * 双色球开奖日（二/四/日）自动同步开奖信息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledSyncService {

    private final LotteryService lotteryService;
    private final AsyncCalcService asyncCalcService;
    private final ConfigService configService;
    private final TaskScheduler taskScheduler;

    private ScheduledFuture<?> nightTask;
    private ScheduledFuture<?> morningTask;

    /**
     * 启动时注册定时任务
     */
    @PostConstruct
    public void init() {
        scheduleNight();
        scheduleMorning();
    }

    /**
     * 重新调度所有定时任务（配置修改后调用）
     */
    public void reschedule() {
        cancelAll();
        scheduleNight();
        scheduleMorning();
        log.info("定时任务已重新调度");
    }

    /** 监听配置变更，SCHEDULE 组变更时自动重调度 */
    @EventListener
    public void onConfigChanged(ConfigChangedEvent event) {
        if (event.getConfigKey().startsWith("scheduled.")) {
            reschedule();
        }
    }

    /** 开奖日晚间同步 */
    private void scheduleNight() {
        String cron = configService.getCron("scheduled.sync.night");
        nightTask = taskScheduler.schedule(this::syncLatestIssue, new CronTrigger(cron));
        log.info("开奖日晚间同步任务已注册，cron：{}", cron);
    }

    /** 开奖次日早晨同步 */
    private void scheduleMorning() {
        String cron = configService.getCron("scheduled.sync.morning");
        morningTask = taskScheduler.schedule(this::syncLatestIssue, new CronTrigger(cron));
        log.info("开奖次日早晨同步任务已注册，cron：{}", cron);
    }

    /** 取消所有定时任务 */
    private void cancelAll() {
        if (nightTask != null) { nightTask.cancel(false); nightTask = null; }
        if (morningTask != null) { morningTask.cancel(false); morningTask = null; }
    }

    /**
     * 同步最新一期开奖信息
     * 从数据库查最新期号 +1，调用外部 API
     */
    private void syncLatestIssue() {
        try {
            java.util.List<String> issues = lotteryService.suggestIssues("");
            if (issues.isEmpty()) {
                log.info("数据库中暂无开奖记录，跳过定时同步");
                return;
            }

            String latestIssue = issues.get(0);
            String nextIssue;
            try {
                long num = Long.parseLong(latestIssue);
                nextIssue = String.valueOf(num + 1);
            } catch (NumberFormatException e) {
                log.warn("无法解析期号：{}", latestIssue);
                return;
            }

            log.info("定时同步：尝试同步期号 {}", nextIssue);

            LotterySyncDTO dto = new LotterySyncDTO(nextIssue);
            java.util.Map<String, Object> result = lotteryService.sync(dto);

            boolean newRecord = (boolean) result.getOrDefault("newRecord", false);
            log.info("定时同步完成，期号：{}，{}", nextIssue,
                    newRecord ? "新增记录" : "已存在（仅更新）");

            asyncCalcService.asyncCalcPurchase(nextIssue);
            asyncCalcService.asyncCalcPredict(nextIssue);

        } catch (Exception e) {
            log.info("定时同步：外部API暂无期号数据，跳过（{}）", e.getMessage());
        }
    }
}
