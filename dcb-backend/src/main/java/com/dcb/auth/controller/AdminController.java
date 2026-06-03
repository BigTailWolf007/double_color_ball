package com.dcb.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcb.auth.entity.Order;
import com.dcb.auth.entity.User;
import com.dcb.auth.entity.UserActive;
import com.dcb.auth.mapper.OrderMapper;
import com.dcb.auth.mapper.UserActiveMapper;
import com.dcb.auth.mapper.UserMapper;
import com.dcb.auth.service.AuthService;
import com.dcb.auth.service.PermissionHelper;
import com.dcb.common.result.PageResult;
import com.dcb.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员接口：用户管理、活跃度、订单
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserMapper userMapper;
    private final UserActiveMapper userActiveMapper;
    private final OrderMapper orderMapper;
    private final AuthService authService;
    private final HttpServletRequest request;

    // ========== 用户管理 ==========

    /** 用户列表 */
    @GetMapping("/users")
    public Result<PageResult<Map<String, Object>>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PermissionHelper.checkFull(request, authService);

        Page<User> pageParam = new Page<>(page, size);
        Page<User> result = (Page<User>) userMapper.selectPage(pageParam,
                new LambdaQueryWrapper<User>().orderByDesc(User::getCreatedAt));

        List<Map<String, Object>> list = result.getRecords().stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            m.put("role", u.getRole());
            m.put("status", u.getStatus());
            m.put("subscribeExpireAt", u.getSubscribeExpireAt());
            m.put("createdAt", u.getCreatedAt());
            m.put("lastLoginAt", u.getLastLoginAt());
            m.put("hasFullAccess", authService.hasFullAccess(u));
            return m;
        }).collect(Collectors.toList());

        return Result.success(PageResult.of(result.getTotal(), list));
    }

    /** 切换用户状态（启用/禁用） */
    @PutMapping("/users/{id}/status")
    public Result<Void> toggleUserStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        PermissionHelper.checkFull(request, authService);
        User user = userMapper.selectById(id);
        if (user == null) throw new RuntimeException("用户不存在");
        user.setStatus(body.get("status"));
        userMapper.updateById(user);
        return Result.success();
    }

    // ========== 活跃度 ==========

    /** 活跃度概览 */
    @GetMapping("/activity")
    public Result<Map<String, Object>> activity() {
        PermissionHelper.checkFull(request, authService);
        LocalDate today = LocalDate.now();

        Map<String, Object> data = new HashMap<>();
        // 今日DAU
        data.put("todayDau", countDau(today));
        // 昨日DAU
        data.put("yesterdayDau", countDau(today.minusDays(1)));
        // 本月MAU
        data.put("thisMonthMau", countMau(today.withDayOfMonth(1), today.plusDays(1)));
        // 上月MAU
        data.put("lastMonthMau", countMau(today.minusMonths(1).withDayOfMonth(1), today.withDayOfMonth(1)));
        // 总用户数
        data.put("totalUsers", userMapper.selectCount(null));
        // 付费用户数
        data.put("paidUsers", userMapper.selectCount(
                new LambdaQueryWrapper<User>().gt(User::getSubscribeExpireAt, LocalDate.now())));

        // 近7天日活趋势
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            Map<String, Object> p = new HashMap<>();
            p.put("date", d.toString().substring(5));
            p.put("dau", countDau(d));
            p.put("mau", countMau(d.minusDays(30), d.plusDays(1)));
            trend.add(p);
        }
        data.put("trend", trend);

        // 近7天活跃用户明细
        List<UserActive> actives = userActiveMapper.selectList(
                new LambdaQueryWrapper<UserActive>()
                        .ge(UserActive::getCreatedAt, today.minusDays(7).atStartOfDay())
                        .orderByDesc(UserActive::getCreatedAt));
        List<Map<String, Object>> detail = actives.stream().map(a -> {
            User u = userMapper.selectById(a.getUserId());
            Map<String, Object> m = new HashMap<>();
            m.put("nickname", u != null ? u.getNickname() : "-");
            m.put("loginType", a.getLoginType());
            m.put("ip", a.getIp());
            m.put("time", a.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
        data.put("recentActives", detail);

        return Result.success(data);
    }

    // ========== 订单管理 ==========

    /** 订单列表 */
    @GetMapping("/orders")
    public Result<PageResult<Order>> listOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PermissionHelper.checkFull(request, authService);

        Page<Order> pageParam = new Page<>(page, size);
        Page<Order> result = (Page<Order>) orderMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Order>().orderByDesc(Order::getCreatedAt));

        return Result.success(PageResult.of(result.getTotal(), result.getRecords()));
    }

    /** 总收入统计 */
    @GetMapping("/orders/summary")
    public Result<Map<String, Object>> ordersSummary() {
        PermissionHelper.checkFull(request, authService);
        List<Order> paidOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>().eq(Order::getStatus, 1));
        java.math.BigDecimal totalRevenue = paidOrders.stream()
                .map(Order::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        Map<String, Object> data = new HashMap<>();
        data.put("totalOrders", paidOrders.size());
        data.put("totalRevenue", totalRevenue);
        return Result.success(data);
    }

    private long countDau(LocalDate date) {
        return userActiveMapper.selectList(
                new LambdaQueryWrapper<UserActive>()
                        .ge(UserActive::getCreatedAt, date.atStartOfDay())
                        .lt(UserActive::getCreatedAt, date.plusDays(1).atStartOfDay()))
                .stream().map(UserActive::getUserId).distinct().count();
    }

    private long countMau(LocalDate from, LocalDate to) {
        return userActiveMapper.selectList(
                new LambdaQueryWrapper<UserActive>()
                        .ge(UserActive::getCreatedAt, from.atStartOfDay())
                        .lt(UserActive::getCreatedAt, to.atStartOfDay()))
                .stream().map(UserActive::getUserId).distinct().count();
    }
}
