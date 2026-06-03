package com.dcb.auth.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dcb.auth.entity.UserActive;
import com.dcb.auth.mapper.UserActiveMapper;
import com.dcb.common.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.Map;

/**
 * JWT 鉴权拦截器：校验 Token + 写入活跃记录
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final UserActiveMapper userActiveMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // OPTIONS 预检请求放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(401);
            return false;
        }

        try {
            String token = authHeader.substring(7);
            Map<String, Object> claims = jwtUtils.parseToken(token);

            Long userId = ((Number) claims.get("userId")).longValue();
            String role = (String) claims.get("role");
            String loginType = (String) claims.get("loginType");

            request.setAttribute("userId", userId);
            request.setAttribute("role", role);
            request.setAttribute("loginType", loginType);

            // 写入活跃记录（同用户同类型每天只记首次）
            recordActive(userId, loginType, request.getRemoteAddr());

            return true;
        } catch (Exception e) {
            response.setStatus(401);
            return false;
        }
    }

    private void recordActive(Long userId, String loginType, String ip) {
        try {
            // 今天是否已记录
            LocalDate today = LocalDate.now();
            Long count = userActiveMapper.selectCount(
                    new LambdaQueryWrapper<UserActive>()
                            .eq(UserActive::getUserId, userId)
                            .eq(UserActive::getLoginType, loginType)
                            .ge(UserActive::getCreatedAt, today.atStartOfDay())
                            .lt(UserActive::getCreatedAt, today.plusDays(1).atStartOfDay()));
            if (count != null && count > 0) return;

            UserActive active = UserActive.builder()
                    .userId(userId)
                    .loginType(loginType)
                    .ip(ip)
                    .build();
            userActiveMapper.insert(active);
        } catch (Exception e) {
            log.debug("记录活跃失败：{}", e.getMessage());
        }
    }
}
