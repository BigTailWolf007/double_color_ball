package com.dcb.common.util;

import com.dcb.common.config.service.ConfigService;
import com.dcb.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 微信 API 工具类
 */
@Slf4j
@Component
public class WechatApiUtil {

    private static final String CODE2SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code";

    private final ConfigService configService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WechatApiUtil(ConfigService configService) {
        this.configService = configService;
    }

    /**
     * 用临时 code 换取 openid 和 session_key
     * 若 wx.appid 未配置（本地开发环境），返回 mock 数据
     */
    public WechatSession code2Session(String code) {
        String appid = getConfig("wx.appid");
        String secret = getConfig("wx.secret");

        // 本地开发 mock 模式：未配置 appid/secret 时，把 code 直接当作 openid
        if (appid == null || appid.isEmpty() || secret == null || secret.isEmpty()) {
            log.info("微信配置未就绪，使用 mock 模式，code={}", code);
            return new WechatSession(code, null);
        }

        String url = String.format(CODE2SESSION_URL, appid, secret, code);
        log.debug("请求微信 code2Session：{}", url);

        try {
            RestTemplate restTemplate = createRestTemplate();
            String responseBody = restTemplate.getForObject(url, String.class);
            log.debug("微信 code2Session 响应：{}", responseBody);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);

            // 检查微信返回的错误
            if (result.containsKey("errcode")) {
                int errcode = ((Number) result.get("errcode")).intValue();
                String errmsg = (String) result.getOrDefault("errmsg", "未知错误");
                log.error("微信 code2Session 失败：errcode={}, errmsg={}", errcode, errmsg);
                throw new BizException("微信登录失败：" + errmsg);
            }

            String openid = (String) result.get("openid");
            String sessionKey = (String) result.get("session_key");
            log.info("微信登录成功：openid={}", openid);
            return new WechatSession(openid, sessionKey);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信 code2Session 异常：{}", e.getMessage());
            throw new BizException("微信登录失败，请稍后重试");
        }
    }

    private String getConfig(String key) {
        try {
            return configService.getString(key);
        } catch (Exception e) {
            log.warn("读取配置 {} 失败：{}", key, e.getMessage());
            return "";
        }
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }

    /**
     * 微信会话信息
     */
    public static class WechatSession {
        private final String openid;
        private final String sessionKey;

        public WechatSession(String openid, String sessionKey) {
            this.openid = openid;
            this.sessionKey = sessionKey;
        }

        public String getOpenid() { return openid; }
        public String getSessionKey() { return sessionKey; }
    }
}
