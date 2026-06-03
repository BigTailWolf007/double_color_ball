package com.dcb.common.config.entity;

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
 * 系统配置实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_sys_config")
public class SysConfig {

    @TableId(value = "fid", type = IdType.AUTO)
    private Long id;

    @TableField("fconfig_key")
    private String configKey;

    @TableField("fconfig_value")
    private String configValue;

    @TableField("fconfig_desc")
    private String configDesc;

    @TableField("fconfig_type")
    private String configType;

    @TableField("fconfig_group")
    private String configGroup;

    @TableField("fsort_order")
    private Integer sortOrder;

    @TableField("fupdated_at")
    private LocalDateTime updatedAt;
}
