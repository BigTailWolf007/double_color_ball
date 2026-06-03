package com.dcb.payment.controller;

import com.dcb.common.result.Result;
import com.dcb.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

/**
 * 支付接口
 */
@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final HttpServletRequest request;

    /** 套餐列表 */
    @PostMapping("/plans")
    public Result<Object> plans() {
        return Result.success(paymentService.getPlans());
    }

    /** 下单，返回微信支付参数 */
    @PostMapping("/order")
    public Result<Map<String, String>> createOrder(@RequestBody Map<String, Object> body) {
        Long userId = (Long) request.getAttribute("userId");
        int planId = ((Number) body.get("planId")).intValue();
        Map<String, String> payParams = paymentService.createOrder(userId, planId);
        return Result.success(payParams);
    }

    /** 微信支付回调通知 */
    @PostMapping("/notify")
    public String notify(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 读取微信回调的 XML 数据
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        String xmlData = sb.toString();
        log.info("收到微信支付回调：{}", xmlData);

        boolean result = paymentService.handleNotify(xmlData);
        // 返回 XML 确认
        return result
                ? "<xml><return_code><![CDATA[SUCCESS]]></return_code><return_msg><![CDATA[OK]]></return_msg></xml>"
                : "<xml><return_code><![CDATA[FAIL]]></return_code><return_msg><![CDATA[ERROR]]></return_msg></xml>";
    }
}
