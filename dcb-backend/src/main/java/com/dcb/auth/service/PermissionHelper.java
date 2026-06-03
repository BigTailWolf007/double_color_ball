package com.dcb.auth.service;

import com.dcb.auth.entity.User;
import com.dcb.common.exception.BizException;

import javax.servlet.http.HttpServletRequest;

/**
 * 权限检查工具——控制器中直接调用，无需 AOP
 */
public class PermissionHelper {

    /** 检查 FULL 权限（管理员 / 首月免费 / 已订阅） */
    public static void checkFull(HttpServletRequest request, AuthService authService) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new BizException("未登录");
        User user = authService.getById(userId);
        if (user == null || !authService.hasFullAccess(user)) {
            throw new BizException("权限不足，请续费后使用");
        }
    }
}
