package com.dcb.payment.service;

import com.dcb.auth.entity.Order;
import com.dcb.auth.entity.User;
import com.dcb.auth.mapper.OrderMapper;
import com.dcb.auth.mapper.UserMapper;
import com.dcb.common.config.service.ConfigService;
import com.dcb.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 微信支付服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String UNIFIED_ORDER_URL = "https://api.mch.weixin.qq.com/pay/unifiedorder";

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final ConfigService configService;

    /** 套餐定义 */
    public List<Map<String, Object>> getPlans() {
        List<Map<String, Object>> plans = new ArrayList<>();
        plans.add(plan(1, "月卡", 9.9, 30));
        plans.add(plan(2, "季卡", 24.9, 90));
        plans.add(plan(3, "年卡", 79.9, 365));
        return plans;
    }

    private Map<String, Object> plan(int id, String name, double price, int days) {
        Map<String, Object> p = new HashMap<>();
        p.put("id", id);
        p.put("name", name);
        p.put("price", price);
        p.put("days", days);
        return p;
    }

    /** 获取套餐详情 */
    private PlanInfo getPlanInfo(int planId) {
        switch (planId) {
            case 1: return new PlanInfo(30, new BigDecimal("9.90"));
            case 2: return new PlanInfo(90, new BigDecimal("24.90"));
            case 3: return new PlanInfo(365, new BigDecimal("79.90"));
            default: throw new BizException("无效的套餐ID");
        }
    }

    /**
     * 创建支付订单，返回小程序调起支付所需参数
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> createOrder(Long userId, int planId) {
        PlanInfo plan = getPlanInfo(planId);

        // 生成订单号
        String orderNo = generateOrderNo();
        LocalDateTime now = LocalDateTime.now();

        // 计算订阅起止时间
        User user = userMapper.selectById(userId);
        LocalDateTime startAt = now;
        if (user.getSubscribeExpireAt() != null && user.getSubscribeExpireAt().isAfter(now)) {
            startAt = user.getSubscribeExpireAt(); // 续费从到期时间开始
        }
        LocalDateTime endAt = startAt.plusDays(plan.days);

        // 保存订单到数据库
        Order order = Order.builder()
                .userId(userId)
                .orderNo(orderNo)
                .amount(plan.price)
                .startAt(startAt)
                .endAt(endAt)
                .status(0) // 待支付
                .createdAt(now)
                .build();
        orderMapper.insert(order);

        // 调用微信统一下单 API
        return callUnifiedOrder(orderNo, plan.price, userId);
    }

    /**
     * 调用微信统一下单 API，返回小程序支付参数
     */
    private Map<String, String> callUnifiedOrder(String orderNo, BigDecimal amount, Long userId) {
        try {
            String appid = configService.getString("wx.appid");
            String mchid = configService.getString("wx.mchid");
            String mchkey = configService.getString("wx.mchkey");
            String notifyUrl = configService.getString("wx.notify-url");

            // 构建 XML 请求参数
            Map<String, String> params = new LinkedHashMap<>();
            params.put("appid", appid);
            params.put("mch_id", mchid);
            params.put("nonce_str", UUID.randomUUID().toString().replace("-", ""));
            params.put("body", "双色球分析-订阅服务");
            params.put("out_trade_no", orderNo);
            params.put("total_fee", String.valueOf(amount.multiply(new BigDecimal("100")).intValue())); // 单位：分
            params.put("spbill_create_ip", "127.0.0.1");
            params.put("notify_url", notifyUrl);
            params.put("trade_type", "JSAPI");
            params.put("openid", userMapper.selectById(userId).getOpenid());
            params.put("sign_type", "MD5");

            // 生成签名
            String sign = generateSign(params, mchkey);
            params.put("sign", sign);

            // 转为 XML
            String xmlBody = mapToXml(params);
            log.debug("统一下单请求：{}", xmlBody);

            // 发送 HTTP 请求
            String responseXml = httpPost(UNIFIED_ORDER_URL, xmlBody);
            log.debug("统一下单响应：{}", responseXml);

            // 解析响应
            Map<String, String> respMap = xmlToMap(responseXml);
            if (!"SUCCESS".equals(respMap.get("return_code"))) {
                throw new BizException("统一下单失败：" + respMap.get("return_msg"));
            }
            if (!"SUCCESS".equals(respMap.get("result_code"))) {
                throw new BizException("统一下单失败：" + respMap.get("err_code_des"));
            }

            // 生成小程序支付参数
            String prepayId = respMap.get("prepay_id");
            return buildPayParams(appid, prepayId, mchkey);

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("统一下单异常：{}", e.getMessage());
            throw new BizException("下单失败，请稍后重试");
        }
    }

    /**
     * 生成小程序支付参数
     */
    private Map<String, String> buildPayParams(String appid, String prepayId, String mchkey) {
        Map<String, String> payParams = new LinkedHashMap<>();
        payParams.put("appId", appid);
        payParams.put("timeStamp", String.valueOf(System.currentTimeMillis() / 1000));
        payParams.put("nonceStr", UUID.randomUUID().toString().replace("-", ""));
        payParams.put("package", "prepay_id=" + prepayId);
        payParams.put("signType", "MD5");
        payParams.put("paySign", generateSign(payParams, mchkey));
        return payParams;
    }

    /**
     * 处理微信支付异步通知
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean handleNotify(String xmlData) {
        try {
            Map<String, String> notifyMap = xmlToMap(xmlData);

            if (!"SUCCESS".equals(notifyMap.get("return_code"))) {
                log.warn("支付回调 return_code 非 SUCCESS：{}", notifyMap.get("return_msg"));
                return false;
            }

            String orderNo = notifyMap.get("out_trade_no");
            String transactionId = notifyMap.get("transaction_id");
            String totalFee = notifyMap.get("total_fee");

            // 验签
            String mchkey = configService.getString("wx.mchkey");
            String expectedSign = generateSign(notifyMap, mchkey);
            if (!expectedSign.equals(notifyMap.get("sign"))) {
                log.error("支付回调签名验证失败：orderNo={}", orderNo);
                return false;
            }

            // 查询订单
            Order order = orderMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                            .eq(Order::getOrderNo, orderNo));
            if (order == null) {
                log.error("支付回调订单不存在：orderNo={}", orderNo);
                return false;
            }

            if (order.getStatus() == 1) {
                log.info("订单已支付，重复通知：orderNo={}", orderNo);
                return true;
            }

            // 更新订单状态
            order.setWxTransactionId(transactionId);
            order.setStatus(1); // 已支付
            order.setPaidAt(LocalDateTime.now());
            orderMapper.updateById(order);

            // 更新用户订阅到期时间
            User user = userMapper.selectById(order.getUserId());
            user.setSubscribeExpireAt(order.getEndAt());
            userMapper.updateById(user);

            log.info("支付成功：userId={}, orderNo={}, amount={}分, endAt={}",
                    order.getUserId(), orderNo, totalFee, order.getEndAt());
            return true;

        } catch (Exception e) {
            log.error("处理支付回调异常：{}", e.getMessage());
            return false;
        }
    }

    // ========== 工具方法 ==========

    private String generateOrderNo() {
        return "DCB" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", new Random().nextInt(10000));
    }

    /** 生成 MD5 签名 */
    private String generateSign(Map<String, String> params, String key) {
        // 按 key 字典序排序，过滤 sign 字段
        StringBuilder sb = new StringBuilder();
        new TreeMap<>(params).forEach((k, v) -> {
            if (v != null && !v.isEmpty() && !"sign".equals(k)) {
                sb.append(k).append("=").append(v).append("&");
            }
        });
        sb.append("key=").append(key);

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02X", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new BizException("签名生成失败");
        }
    }

    /** Map 转 XML */
    private String mapToXml(Map<String, String> params) {
        StringBuilder xml = new StringBuilder("<xml>");
        params.forEach((k, v) -> xml.append("<").append(k).append(">")
                .append("<![CDATA[").append(v).append("]]>")
                .append("</").append(k).append(">"));
        xml.append("</xml>");
        return xml.toString();
    }

    /** XML 转 Map */
    private Map<String, String> xmlToMap(String xml) throws Exception {
        Map<String, String> map = new HashMap<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Element root = doc.getDocumentElement();
        NodeList nodes = root.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element) {
                Element elem = (Element) nodes.item(i);
                map.put(elem.getTagName(), elem.getTextContent());
            }
        }
        return map;
    }

    /** HTTP POST 请求 */
    private String httpPost(String urlStr, String xmlBody) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/xml;charset=UTF-8");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(xmlBody.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        StringBuilder response = new StringBuilder();
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    private static class PlanInfo {
        final int days;
        final BigDecimal price;
        PlanInfo(int days, BigDecimal price) { this.days = days; this.price = price; }
    }
}
