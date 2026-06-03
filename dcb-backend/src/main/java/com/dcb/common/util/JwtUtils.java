package com.dcb.common.util;

import com.dcb.common.config.service.ConfigService;
import com.dcb.common.exception.BizException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * 轻量 JWT 工具类（HS256，无需第三方依赖）
 */
@Slf4j
@Component
public class JwtUtils {

    private static final long EXPIRE_MS = 24 * 60 * 60 * 1000L; // 24小时
    private final ConfigService configService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtUtils(ConfigService configService) {
        this.configService = configService;
    }

    /**
     * 生成 JWT Token
     */
    public String generateToken(Long userId, String role, String loginType) {
        try {
            long now = System.currentTimeMillis();
            String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            java.util.Map<String, Object> claims = new java.util.LinkedHashMap<>();
            claims.put("userId", userId);
            claims.put("role", role);
            claims.put("loginType", loginType);
            claims.put("iat", now / 1000);
            claims.put("exp", (now + EXPIRE_MS) / 1000);
            String payloadJson = objectMapper.writeValueAsString(claims);

            String header = base64Encode(headerJson);
            String payload = base64Encode(payloadJson);
            String signature = sign(header + "." + payload);

            return header + "." + payload + "." + signature;
        } catch (Exception e) {
            throw new BizException("生成Token失败：" + e.getMessage());
        }
    }

    /**
     * 解析并校验 JWT Token
     */
    public Map<String, Object> parseToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) throw new BizException("Token格式错误");

            String expectedSig = sign(parts[0] + "." + parts[1]);
            if (!expectedSig.equals(parts[2])) throw new BizException("Token签名无效");

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> claims = objectMapper.readValue(payloadJson,
                    new TypeReference<Map<String, Object>>() {});

            // 检查过期
            long exp = ((Number) claims.get("exp")).longValue();
            if (System.currentTimeMillis() / 1000 > exp) {
                throw new BizException("Token已过期");
            }

            return claims;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("Token解析失败");
        }
    }

    private String base64Encode(String str) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(str.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String data) {
        try {
            String secret = configService.getString("jwt.secret");
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] sigBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);
        } catch (Exception e) {
            throw new BizException("签名失败：" + e.getMessage());
        }
    }
}
