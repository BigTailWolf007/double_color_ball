package com.dcb.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_user_active")
public class UserActive {

    @TableId(value = "fid", type = IdType.AUTO)
    private Long id;

    @TableField("fuser_id")
    private Long userId;

    @TableField("flogin_type")
    private String loginType;

    @TableField("fip")
    private String ip;

    @TableField("fcreated_at")
    private LocalDateTime createdAt;
}
