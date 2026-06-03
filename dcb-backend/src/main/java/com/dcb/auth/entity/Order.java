package com.dcb.auth.entity;

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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_order")
public class Order {

    @TableId(value = "fid", type = IdType.AUTO)
    private Long id;

    @TableField("fuser_id")
    private Long userId;

    @TableField("forder_no")
    private String orderNo;

    @TableField("fwx_transaction_id")
    private String wxTransactionId;

    @TableField("famount")
    private BigDecimal amount;

    @TableField("fstart_at")
    private LocalDateTime startAt;

    @TableField("fend_at")
    private LocalDateTime endAt;

    @TableField("fstatus")
    private Integer status;

    @TableField("fpaid_at")
    private LocalDateTime paidAt;

    @TableField("fcreated_at")
    private LocalDateTime createdAt;
}
