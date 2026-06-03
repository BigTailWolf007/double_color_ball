package com.dcb.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dcb.auth.entity.User;
import com.dcb.auth.mapper.UserMapper;
import com.dcb.common.exception.BizException;
import com.dcb.common.util.JwtUtils;
import com.dcb.common.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtils jwtUtils;

    /**
     * 启动时确保默认管理员存在
     */
    @PostConstruct
    public void initAdmin() {
        try {
            User admin = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getUsername, "admin"));
            if (admin == null) {
                admin = User.builder()
                        .username("admin")
                        .password(PasswordUtil.hash("123456"))
                        .nickname("管理员")
                        .role("ADMIN")
                        .status(1)
                        .createdAt(LocalDateTime.now())
                        .build();
                userMapper.insert(admin);
                log.info("默认管理员已创建：admin / 123456");
            }
        } catch (Exception e) {
            log.warn("用户表初始化失败（请先执行 init.sql 或 migration.sql）：{}", e.getMessage());
        }
    }

    /**
     * Web 管理员登录
     */
    public Map<String, Object> webLogin(String username, String password) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || !PasswordUtil.verify(password, user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            throw new BizException("账号已被禁用");
        }
        if (!"ADMIN".equals(user.getRole())) {
            throw new BizException("非管理员账号，请使用微信小程序登录");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        String token = jwtUtils.generateToken(user.getId(), user.getRole(), "WEB");
        log.info("管理员登录：{}", username);
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("token", token);
        map.put("nickname", user.getNickname());
        map.put("role", user.getRole());
        return map;
    }

    /**
     * 微信小程序登录（根据 openid 查找或创建用户）
     */
    public Map<String, Object> wxLogin(String openid, String nickname, String avatar) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));

        if (user == null) {
            user = User.builder()
                    .openid(openid)
                    .nickname(nickname != null ? nickname : "微信用户")
                    .avatar(avatar)
                    .role("USER")
                    .status(1)
                    .createdAt(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
            log.info("新用户注册：openid={}, nickname={}", openid, nickname);
        } else {
            if (user.getStatus() == 0) {
                throw new BizException("账号已被禁用");
            }
            if (nickname != null) user.setNickname(nickname);
            if (avatar != null) user.setAvatar(avatar);
            userMapper.updateById(user);
        }

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        String token = jwtUtils.generateToken(user.getId(), user.getRole(), "WX");
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("token", token);
        map.put("nickname", user.getNickname());
        map.put("role", user.getRole());
        return map;
    }

    /**
     * 根据 ID 获取用户
     */
    public User getById(Long userId) {
        return userMapper.selectById(userId);
    }

    /**
     * 判断用户是否有全功能权限
     */
    public boolean hasFullAccess(User user) {
        if (user == null) return false;
        if ("ADMIN".equals(user.getRole())) return true;
        // 首月免费
        if (user.getCreatedAt() != null
                && user.getCreatedAt().plusDays(30).isAfter(LocalDateTime.now())) return true;
        // 已付费且在有效期内
        return user.getSubscribeExpireAt() != null
                && user.getSubscribeExpireAt().isAfter(LocalDateTime.now());
    }
}
