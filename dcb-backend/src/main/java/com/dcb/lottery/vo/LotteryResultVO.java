package com.dcb.lottery.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 开奖号码VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LotteryResultVO {

    /** 主键 */
    private Long id;

    /** 期号 */
    private String issue;

    /** 开奖日期 */
    private LocalDate drawDate;

    /** 红球列表（已排序） */
    private List<Integer> reds;

    /** 蓝球 */
    private Integer blue;

    /** 奖品分配可读文本 */
    private String prizeText;

    /** 最后领奖日期 */
    private LocalDate deadline;

    /** 本期销售金额（元） */
    private String saleAmount;

    /** 奖池总金额（元） */
    private String poolAmount;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 红球和值 */
    private Integer sumVal;

    /** 区间比（低:中:高），如 2:2:2 */
    private String zoneRatio;

    /** 奇偶比（奇:偶），如 3:3 */
    private String oddEvenRatio;

    /** 跨度（红球最大值-最小值） */
    private Integer rangeVal;
}
