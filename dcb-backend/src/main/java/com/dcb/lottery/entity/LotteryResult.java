package com.dcb.lottery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 开奖号码实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_lottery_result")
public class LotteryResult {

    /** 主键 */
    @TableId(value = "fid", type = IdType.AUTO)
    private Long id;

    /** 期号 */
    @TableField("fissue")
    private String issue;

    /** 开奖日期 */
    @TableField("fdraw_date")
    private LocalDate drawDate;

    /** 红球1 */
    @TableField("fred1")
    private Integer red1;

    /** 红球2 */
    @TableField("fred2")
    private Integer red2;

    /** 红球3 */
    @TableField("fred3")
    private Integer red3;

    /** 红球4 */
    @TableField("fred4")
    private Integer red4;

    /** 红球5 */
    @TableField("fred5")
    private Integer red5;

    /** 红球6 */
    @TableField("fred6")
    private Integer red6;

    /** 蓝球 */
    @TableField("fblue")
    private Integer blue;

    /** 号码复合键：期号-红1-红2-红3-红4-红5-红6-蓝 */
    @TableField("fball_key")
    private String ballKey;

    /** 奖品分配JSON（原始数据，供后台解析） */
    @TableField("fprize_json")
    private String prizeJson;

    /** 奖品分配可读文本（如：一等奖8注6,130,798元；二等奖135注268,041元；...） */
    @TableField("fprize_text")
    private String prizeText;

    /** 最后领奖日期 */
    @TableField("fdeadline")
    private LocalDate deadline;

    /** 本期销售金额（元） */
    @TableField("fsale_amount")
    private java.math.BigDecimal saleAmount;

    /** 奖池总金额（元） */
    @TableField("fpool_amount")
    private java.math.BigDecimal poolAmount;

    /** 红球和值 */
    @TableField("fsum_val")
    private Integer sumVal;

    /** 区间比（低:中:高），如 2:2:2 */
    @TableField("fzone_ratio")
    private String zoneRatio;

    /** 奇偶比（奇:偶），如 3:3 */
    @TableField("fodd_even_ratio")
    private String oddEvenRatio;

    /** 跨度（红球最大值-最小值） */
    @TableField("frange_val")
    private Integer rangeVal;

    /** 创建时间 */
    @TableField("fcreated_at")
    private LocalDateTime createdAt;
}
