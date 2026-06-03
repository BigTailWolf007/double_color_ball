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
@TableName("t_user")
public class User {

    @TableId(value = "fid", type = IdType.AUTO)
    private Long id;

    @TableField("fopenid")
    private String openid;

    @TableField("fusername")
    private String username;

    @TableField("fpassword")
    private String password;

    @TableField("fnickname")
    private String nickname;

    @TableField("favatar")
    private String avatar;

    @TableField("frole")
    private String role;

    @TableField("fstatus")
    private Integer status;

    @TableField("fsubscribe_expire_at")
    private LocalDateTime subscribeExpireAt;

    @TableField("fcreated_at")
    private LocalDateTime createdAt;

    @TableField("flast_login_at")
    private LocalDateTime lastLoginAt;
}
