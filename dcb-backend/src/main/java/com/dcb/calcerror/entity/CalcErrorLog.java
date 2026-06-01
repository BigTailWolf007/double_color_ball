package com.dcb.calcerror.entity;

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
 * 计算错误日志实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_calc_error_log")
public class CalcErrorLog {

    /** 主键 */
    @TableId(value = "fid", type = IdType.AUTO)
    private Long id;

    /** 期号 */
    @TableField("fissue")
    private String issue;

    /** 计算类型：purchase / predict */
    @TableField("fcalc_type")
    private String calcType;

    /** 分片起始 ID */
    @TableField("fid_start")
    private Long idStart;

    /** 分片结束 ID（不包含） */
    @TableField("fid_end")
    private Long idEnd;

    /** 异常信息 */
    @TableField("ferror_msg")
    private String errorMsg;

    /** 状态：0=待处理, 1=已重试成功, 2=已忽略 */
    @TableField("fstatus")
    private Integer status;

    /** 已重试次数 */
    @TableField("fretry_count")
    private Integer retryCount;

    /** 创建时间 */
    @TableField("fcreated_at")
    private LocalDateTime createdAt;
}
