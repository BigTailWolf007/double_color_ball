package com.dcb.common.service;

import com.dcb.lottery.dto.LotterySyncDTO;
import com.dcb.lottery.service.LotteryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 定时同步服务
 * 双色球开奖日（二/四/日）自动同步开奖信息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledSyncService {

    private final LotteryService lotteryService;
    private final AsyncCalcService asyncCalcService;

    /**
     * 开奖日晚 21:20 首次同步（周二、四、日）
     */
    @Scheduled(cron = "0 20 21 ? * 3,5,1")
    public void syncOnDrawNight() {
        log.info("=== 定时任务：开奖日晚间同步 ===");
        syncLatestIssue();
    }

    /**
     * 开奖次日早 08:00 二次同步（周三、五、一）
     * 补全奖金等开奖后统计的数据
     */
    @Scheduled(cron = "0 0 8 ? * 4,6,2")
    public void syncOnNextMorning() {
        log.info("=== 定时任务：开奖次日早晨同步 ===");
        syncLatestIssue();
    }

    /**
     * 同步最新一期开奖信息
     * 从数据库查最新期号 +1，调用外部 API
     */
    private void syncLatestIssue() {
        try {
            // 从期号建议接口获取最新期号（倒序排列）
            java.util.List<String> issues = lotteryService.suggestIssues("");
            if (issues.isEmpty()) {
                log.info("数据库中暂无开奖记录，跳过定时同步");
                return;
            }

            // 最新期号 +1 = 预期新期号
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

            // 调用同步
            LotterySyncDTO dto = new LotterySyncDTO(nextIssue);
            java.util.Map<String, Object> result = lotteryService.sync(dto);

            boolean newRecord = (boolean) result.getOrDefault("newRecord", false);
            log.info("定时同步完成，期号：{}，{}", nextIssue,
                    newRecord ? "新增记录" : "已存在（仅更新）");

            // 触发异步计算
            asyncCalcService.asyncCalcPurchase(nextIssue);
            asyncCalcService.asyncCalcPredict(nextIssue);

        } catch (Exception e) {
            // 外部 API 无数据时属于正常情况（尚未开奖），仅记录不抛异常
            log.info("定时同步：外部API暂无期号数据，跳过（{}）", e.getMessage());
        }
    }
}
