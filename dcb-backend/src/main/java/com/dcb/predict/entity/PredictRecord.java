package com.dcb.predict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 预测号码实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_predict_record")
public class PredictRecord {

    /** 主键 */
    @TableId(value = "fid", type = IdType.AUTO)
    private Long id;

    /** 目标期号 */
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

    /** 号码复合键：期号-红1-红2-红3-红4-红5-红6-蓝 */
    @TableField("fball_key")
    private String ballKey;

    /** 命中红球数(0-6,NULL=待开奖) */
    @TableField("fhit_red")
    private Integer hitRed;

    /** 是否命中蓝球(0/1,NULL=待开奖) */
    @TableField("fhit_blue")
    private Integer hitBlue;

    /** 命中等级(1-6,0=未中,NULL=待开奖) */
    @TableField("fprize_level")
    private Integer prizeLevel;

    /** 创建时间 */
    @TableField("fcreated_at")
    private LocalDateTime createdAt;
}
