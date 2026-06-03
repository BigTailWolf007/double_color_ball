package com.dcb.auth.controller;

import com.dcb.auth.entity.Order;
import com.dcb.auth.entity.User;
import com.dcb.auth.mapper.OrderMapper;
import com.dcb.auth.mapper.UserMapper;
import com.dcb.auth.service.AuthService;
import com.dcb.auth.service.PermissionHelper;
import com.dcb.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 订单接口
 */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final AuthService authService;
    private final HttpServletRequest request;

    /** 创建续费订单 */
    @PostMapping("/create")
    public Result<Map<String, Object>> create() {
        Long userId = (Long) request.getAttribute("userId");
        User user = userMapper.selectById(userId);
        if (user == null) throw new RuntimeException("用户不存在");

        // 计算订单开始/到期时间
        LocalDateTime startAt;
        LocalDateTime now = LocalDateTime.now();
        if (user.getSubscribeExpireAt() != null && user.getSubscribeExpireAt().isAfter(now)) {
            // 有效期内续费：从到期时间接续
            startAt = user.getSubscribeExpireAt();
        } else {
            startAt = now;
        }
        LocalDateTime endAt = startAt.plusMonths(1);

        // 生成订单号
        String orderNo = "DCB" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", (int)(Math.random() * 10000));

        Order order = Order.builder()
                .userId(userId)
                .orderNo(orderNo)
                .amount(new BigDecimal("9.90"))
                .startAt(startAt)
                .endAt(endAt)
                .status(0)
                .build();
        orderMapper.insert(order);

        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("orderNo", orderNo);
        map.put("amount", "9.90");
        return Result.success(map);
    }

    /** 微信支付回调（骨架） */
    @PostMapping("/callback")
    public Result<Void> callback(@RequestBody Map<String, Object> body) {
        // TODO: 验签 + 更新订单状态 + 更新用户订阅到期时间
        String orderNo = (String) body.get("orderNo");
        String transactionId = (String) body.get("transactionId");

        Order order = orderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderNo, orderNo));
        if (order == null) throw new RuntimeException("订单不存在");

        order.setStatus(1);
        order.setPaidAt(LocalDateTime.now());
        order.setWxTransactionId(transactionId);
        orderMapper.updateById(order);

        // 更新用户到期时间
        User user = userMapper.selectById(order.getUserId());
        user.setSubscribeExpireAt(order.getEndAt());
        userMapper.updateById(user);

        return Result.success();
    }
}
