package com.dcb.auth.controller;

import com.dcb.auth.service.AuthService;
import com.dcb.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 认证接口
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Web 管理员登录 */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        return Result.success(authService.webLogin(username, password));
    }

    /** 微信小程序登录 */
    @PostMapping("/wx-login")
    public Result<Map<String, Object>> wxLogin(@RequestBody Map<String, String> body) {
        String openid = body.get("openid");
        String nickname = body.get("nickname");
        String avatar = body.get("avatar");
        // TODO: 正式环境需后端调微信接口用 code 换 openid
        return Result.success(authService.wxLogin(openid, nickname, avatar));
    }

    /** 获取当前用户信息 */
    @GetMapping("/me")
    public Result<Map<String, Object>> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("userId", userId);
        result.put("role", role);
        return Result.success(result);
    }
}
