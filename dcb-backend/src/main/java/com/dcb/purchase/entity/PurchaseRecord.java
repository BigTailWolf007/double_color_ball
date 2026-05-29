package com.dcb.purchase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购买记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_purchase_record")
public class PurchaseRecord {

    /** 主键 */
    @TableId(value = "fid", type = IdType.AUTO)
    private Long id;

    /** 期号 */
    @TableField("fissue")
    private String issue;

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

    /** 注数 */
    @TableField("fquantity")
    private Integer quantity;

    /** 中奖等级(1-6,0=未中,NULL=待计算) */
    @TableField("fprize_level")
    private Integer prizeLevel;

    /** 总奖金 */
    @TableField("fprize_money")
    private BigDecimal prizeMoney;

    /** 备注 */
    @TableField("fremark")
    private String remark;

    /** 创建时间 */
    @TableField("fcreated_at")
    private LocalDateTime createdAt;
}
