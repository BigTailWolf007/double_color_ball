package com.dcb.auth.interceptor;

import com.dcb.common.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * JWT 鉴权拦截器：校验 Token
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;

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

            return true;
        } catch (Exception e) {
            response.setStatus(401);
            return false;
        }
    }
}
