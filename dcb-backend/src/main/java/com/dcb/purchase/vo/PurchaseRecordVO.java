package com.dcb.purchase.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 购买记录VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRecordVO {

    /** 主键 */
    private Long id;

    /** 期号 */
    private String issue;

    /** 红球列表（已排序） */
    private List<Integer> reds;

    /** 蓝球 */
    private Integer blue;

    /** 开奖红球（用于前端命中高亮） */
    private List<Integer> drawReds;

    /** 开奖蓝球（用于前端命中高亮） */
    private Integer drawBlue;

    /** 注数 */
    private Integer quantity;

    /** 中奖等级值(0-6,NULL=待计算) */
    private Integer prizeLevel;

    /** 中奖等级描述 */
    private String prizeLevelDesc;

    /** 总奖金 */
    private BigDecimal prizeMoney;

    /** 备注 */
    private String remark;

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
