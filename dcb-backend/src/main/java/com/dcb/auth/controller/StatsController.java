package com.dcb.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dcb.auth.entity.UserActive;
import com.dcb.auth.mapper.UserActiveMapper;
import com.dcb.auth.mapper.UserMapper;
import com.dcb.auth.service.AuthService;
import com.dcb.auth.service.PermissionHelper;
import com.dcb.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日活/月活统计接口
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final UserActiveMapper userActiveMapper;
    private final UserMapper userMapper;
    private final AuthService authService;
    private final HttpServletRequest request;

    /** 日活统计：今日 + 昨日 + 近7天趋势 */
    @GetMapping("/dau")
    public Result<Map<String, Object>> dau() {
        PermissionHelper.checkFull(request, authService);

        LocalDate today = LocalDate.now();
        Map<String, Object> result = new HashMap<>();

        // 今日日活
        result.put("today", countDau(today));
        // 昨日日活
        result.put("yesterday", countDau(today.minusDays(1)));

        // 近7天趋势
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            Map<String, Object> point = new HashMap<>();
            point.put("date", d.toString());
            point.put("count", countDau(d));
            trend.add(point);
        }
        result.put("trend", trend);
        return Result.success(result);
    }

    /** 月活统计 */
    @GetMapping("/mau")
    public Result<Map<String, Object>> mau() {
        PermissionHelper.checkFull(request, authService);

        LocalDate today = LocalDate.now();
        Map<String, Object> result = new HashMap<>();
        result.put("thisMonth", countUniqueUsers(today.minusDays(30)));
        result.put("lastMonth", countUniqueUsers(today.minusDays(60), today.minusDays(30)));
        return Result.success(result);
    }

    /** 总注册用户数 */
    @GetMapping("/users")
    public Result<Long> totalUsers() {
        PermissionHelper.checkFull(request, authService);
        return Result.success(userMapper.selectCount(null));
    }

    private long countDau(LocalDate date) {
        return userActiveMapper.selectCount(
                new LambdaQueryWrapper<UserActive>()
                        .ge(UserActive::getCreatedAt, date.atStartOfDay())
                        .lt(UserActive::getCreatedAt, date.plusDays(1).atStartOfDay()));
    }

    private long countUniqueUsers(LocalDate from) {
        return countUniqueUsers(from, LocalDate.now());
    }

    private long countUniqueUsers(LocalDate from, LocalDate to) {
        List<UserActive> list = userActiveMapper.selectList(
                new LambdaQueryWrapper<UserActive>()
                        .ge(UserActive::getCreatedAt, from.atStartOfDay())
                        .lt(UserActive::getCreatedAt, to.atStartOfDay()));
        return list.stream().map(UserActive::getUserId).distinct().count();
    }
}
