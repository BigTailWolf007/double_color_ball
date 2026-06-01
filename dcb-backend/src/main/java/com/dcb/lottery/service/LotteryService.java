package com.dcb.lottery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcb.common.exception.BizException;
import com.dcb.common.result.PageResult;
import com.dcb.common.util.LotteryUtils;
import com.dcb.lottery.dto.LotteryAddDTO;
import com.dcb.lottery.dto.LotterySyncDTO;
import com.dcb.lottery.entity.LotteryResult;
import com.dcb.lottery.mapper.LotteryResultMapper;
import com.dcb.lottery.vo.LotteryResultVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 开奖号码服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LotteryService {

    /** 外部彩票 API 地址 */
    private static final String LOTTERY_API_URL =
            "https://api2.tanshuapi.com/api/caipiao/v1/query?key=41169186260d56cdeda190c0f99b8c9f&caipiaoid=11&issueno=";

    /** API 调用超时时间（毫秒） */
    private static final int API_TIMEOUT = 10000;

    /** 金额格式化器 */
    private static final NumberFormat MONEY_FORMAT = NumberFormat.getIntegerInstance(Locale.CHINA);

    private final LotteryResultMapper lotteryResultMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 彩票接口同步
     * 调用外部 API 获取指定期号的开奖信息，解析并保存到数据库
     *
     * @param dto 同步请求（期号）
     * @return 同步结果信息
     */
    public Map<String, Object> sync(LotterySyncDTO dto) {
        String issue = dto.getIssue();
        log.info("开始同步开奖信息，期号：{}", issue);

        // 1. 调用外部 API
        String responseBody;
        try {
            RestTemplate restTemplate = createRestTemplate();
            responseBody = restTemplate.getForObject(LOTTERY_API_URL + issue, String.class);
        } catch (Exception e) {
            log.error("调用外部彩票API失败，期号：{}，错误：{}", issue, e.getMessage(), e);
            throw new BizException("调用外部彩票API失败：" + e.getMessage());
        }

        // 2. 解析 JSON 响应
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            log.error("解析外部API响应失败，期号：{}", issue, e);
            throw new BizException("解析外部API响应失败");
        }

        int code = root.path("code").asInt(-1);
        if (code != 1) {
            String msg = root.path("msg").asText("未知错误");
            throw new BizException("外部API返回错误：" + msg);
        }

        JsonNode data = root.get("data");
        if (data == null || data.isNull()) {
            throw new BizException("外部API未返回开奖数据");
        }

        // 3. 解析各字段
        String number = data.path("number").asText();
        String referNumber = data.path("refernumber").asText();
        String openDateStr = data.path("opendate").asText(null);
        String deadlineStr = data.has("deadline") && !data.get("deadline").isNull()
                ? data.get("deadline").asText() : null;
        long saleAmountVal = data.path("saleamount").asLong(0);
        String totalMoneyStr = data.path("totalmoney").asText("0");

        // 解析红球（空格分隔）
        String[] redStrs = number.split(" ");
        if (redStrs.length != 6) {
            throw new BizException("外部API返回的红球数量异常：" + number);
        }
        int r1 = Integer.parseInt(redStrs[0].trim());
        int r2 = Integer.parseInt(redStrs[1].trim());
        int r3 = Integer.parseInt(redStrs[2].trim());
        int r4 = Integer.parseInt(redStrs[3].trim());
        int r5 = Integer.parseInt(redStrs[4].trim());
        int r6 = Integer.parseInt(redStrs[5].trim());
        int blue = Integer.parseInt(referNumber.trim());

        LotteryUtils.validateRed(Arrays.asList(r1, r2, r3, r4, r5, r6));
        LotteryUtils.validateBlue(blue);

        // 解析奖品数组：若为空则按默认规则生成
        JsonNode prizeArr = data.get("prize");
        boolean prizeEmpty = prizeArr == null || !prizeArr.isArray() || prizeArr.size() == 0;
        String prizeJson;
        String prizeText;
        if (prizeEmpty) {
            prizeJson = generateDefaultPrizeJson();
            prizeText = generateDefaultPrizeText();
            log.info("外部API未返回奖品数据，使用默认规则");
        } else {
            prizeJson = prizeArr.toString();
            prizeText = buildPrizeText(prizeArr);
        }

        LocalDate openDate = openDateStr != null ? LocalDate.parse(openDateStr) : null;
        LocalDate deadline = deadlineStr != null ? LocalDate.parse(deadlineStr) : null;
        BigDecimal saleAmount = BigDecimal.valueOf(saleAmountVal);
        BigDecimal poolAmount = new BigDecimal(totalMoneyStr);

        // 4. 查询是否已存在该期号
        LotteryResult existing = lotteryResultMapper.selectOne(
                new LambdaQueryWrapper<LotteryResult>()
                        .eq(LotteryResult::getIssue, issue));

        boolean newRecord;
        if (existing != null) {
            // 5a. 已存在 —— 更新扩展字段
            LotteryResult update = new LotteryResult();
            update.setId(existing.getId());
            update.setPrizeJson(prizeJson);
            update.setPrizeText(prizeText);
            update.setDeadline(deadline);
            update.setSaleAmount(saleAmount);
            update.setPoolAmount(poolAmount);
            // 若本地开奖日期为空，用 API 返回的补充
            if (existing.getDrawDate() == null && openDate != null) {
                update.setDrawDate(openDate);
            }
            lotteryResultMapper.updateById(update);
            newRecord = false;
            log.info("同步更新开奖信息成功，期号：{}", issue);
        } else {
            // 5b. 不存在 —— 插入新记录
            LotteryResult entity = LotteryResult.builder()
                    .issue(issue)
                    .drawDate(openDate)
                    .red1(r1).red2(r2).red3(r3).red4(r4).red5(r5).red6(r6)
                    .blue(blue)
                    .ballKey(LotteryUtils.buildBallKey(issue, r1, r2, r3, r4, r5, r6, blue))
                    .prizeJson(prizeJson)
                    .prizeText(prizeText)
                    .deadline(deadline)
                    .saleAmount(saleAmount)
                    .poolAmount(poolAmount)
                    .build();
            try {
                lotteryResultMapper.insert(entity);
            } catch (DuplicateKeyException e) {
                throw new BizException("期号 " + issue + " 已存在（并发冲突）");
            }
            newRecord = true;
            log.info("同步新增开奖信息成功，期号：{}", issue);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("issue", issue);
        result.put("newRecord", newRecord);
        return result;
    }

    /**
     * 创建带超时的 RestTemplate
     */
    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(API_TIMEOUT);
        factory.setReadTimeout(API_TIMEOUT);
        return new RestTemplate(factory);
    }

    /**
     * 构建奖品可读文本
     * 格式：一等奖8注6,130,798元；二等奖135注268,041元；...
     */
    private String buildPrizeText(JsonNode prizeArr) {
        if (prizeArr == null || !prizeArr.isArray() || prizeArr.size() == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode p : prizeArr) {
            String name = p.path("prizename").asText("");
            int num = p.path("num").asInt(0);
            long singleBonus = p.path("singlebonus").asLong(0);
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(name).append(num).append("注")
                    .append(MONEY_FORMAT.format(singleBonus)).append("元");
        }
        return sb.toString();
    }

    /**
     * 生成默认奖品 JSON（API 无数据时使用）
     */
    private String generateDefaultPrizeJson() {
        return "[" +
            "{\"prizename\":\"一等奖\",\"require\":\"中6+1\",\"num\":0,\"singlebonus\":0}," +
            "{\"prizename\":\"二等奖\",\"require\":\"中6+0\",\"num\":0,\"singlebonus\":0}," +
            "{\"prizename\":\"三等奖\",\"require\":\"中5+1\",\"num\":0,\"singlebonus\":3000}," +
            "{\"prizename\":\"四等奖\",\"require\":\"中5+0/4+1\",\"num\":0,\"singlebonus\":200}," +
            "{\"prizename\":\"五等奖\",\"require\":\"中4+0/3+1\",\"num\":0,\"singlebonus\":10}," +
            "{\"prizename\":\"六等奖\",\"require\":\"中2+1/1+1/0+1\",\"num\":0,\"singlebonus\":5}," +
            "{\"prizename\":\"福运奖\",\"require\":\"\",\"num\":0,\"singlebonus\":5}" +
            "]";
    }

    /**
     * 生成默认奖品可读文本（API 无数据时使用）
     */
    private String generateDefaultPrizeText() {
        return "一等奖0注0元；二等奖0注0元；三等奖0注3,000元；四等奖0注200元；五等奖0注10元；六等奖0注5元；福运奖0注5元";
    }

    /**
     * TXT 文件导入开奖号码
     */
    public Map<String, Object> importFromTxt(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".txt")) {
            throw new BizException("仅支持 .txt 格式文件");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BizException("文件大小不能超过 10MB");
        }

        int success = 0, skip = 0, fail = 0;
        List<String> failDetails = new ArrayList<>();
        List<String> successIssues = new ArrayList<>();
        log.info("开始导入开奖号码文件：{}", filename);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    LotteryResult result = parseLine(line);
                    if (lotteryResultMapper.selectOne(
                            new LambdaQueryWrapper<LotteryResult>()
                                    .eq(LotteryResult::getIssue, result.getIssue())) != null) {
                        log.debug("期号 {} 已存在，跳过", result.getIssue());
                        skip++;
                        continue;
                    }
                    lotteryResultMapper.insert(result);
                    successIssues.add(result.getIssue());
                    success++;                } catch (Exception e) {
                    log.warn("第{}行解析失败：{}", lineNum, e.getMessage());
                    fail++;
                    failDetails.add("第" + lineNum + "行：" + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("文件读取失败：{}", e.getMessage(), e);
            throw new BizException("文件读取失败：" + e.getMessage());
        }

        log.info("导入完成：成功{}条，跳过{}条，失败{}条", success, skip, fail);
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("skip", skip);
        result.put("fail", fail);
        result.put("failDetails", failDetails);
        result.put("issues", successIssues);
        return result;
    }

    /**
     * 解析 TXT 单行：期号 红1 红2 红3 红4 红5 红6 蓝球
     */
    private LotteryResult parseLine(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length < 8) {
            throw new BizException("格式错误，需要8个字段，实际" + parts.length + "个");
        }
        String issue = parts[0];
        int r1 = Integer.parseInt(parts[1]);
        int r2 = Integer.parseInt(parts[2]);
        int r3 = Integer.parseInt(parts[3]);
        int r4 = Integer.parseInt(parts[4]);
        int r5 = Integer.parseInt(parts[5]);
        int r6 = Integer.parseInt(parts[6]);
        int blue = Integer.parseInt(parts[7]);

        LotteryUtils.validateRed(Arrays.asList(r1, r2, r3, r4, r5, r6));
        LotteryUtils.validateBlue(blue);

        return LotteryResult.builder()
                .issue(issue)
                .red1(r1).red2(r2).red3(r3).red4(r4).red5(r5).red6(r6)
                .blue(blue)
                .ballKey(LotteryUtils.buildBallKey(issue, r1, r2, r3, r4, r5, r6, blue))
                .build();
    }

    /**
     * 手动录入开奖号码
     */
    public void add(LotteryAddDTO dto) {
        LotteryUtils.validateRed(Arrays.asList(
                dto.getRed1(), dto.getRed2(), dto.getRed3(),
                dto.getRed4(), dto.getRed5(), dto.getRed6()));
        LotteryUtils.validateBlue(dto.getBlue());

        LotteryResult entity = LotteryResult.builder()
                .issue(dto.getIssue())
                .drawDate(dto.getDrawDate())
                .red1(dto.getRed1()).red2(dto.getRed2()).red3(dto.getRed3())
                .red4(dto.getRed4()).red5(dto.getRed5()).red6(dto.getRed6())
                .blue(dto.getBlue())
                .ballKey(LotteryUtils.buildBallKey(dto))
                .build();
        try {
            lotteryResultMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new BizException("期号 " + dto.getIssue() + " 已存在");
        }
        log.info("手动录入开奖号码成功，期号：{}", dto.getIssue());
    }

    /**
     * 删除开奖号码
     */
    public void delete(Long id) {
        lotteryResultMapper.deleteById(id);
        log.info("删除开奖号码，id：{}", id);
    }

    /**
     * 模糊查询期号，倒序返回最多10个
     */
    public List<String> suggestIssues(String keyword) {
        return lotteryResultMapper.selectIssuesByKeyword(keyword == null ? "" : keyword, 10);
    }

    /**
     * 分页查询开奖号码
     */
    public PageResult<LotteryResultVO> list(String issue, LocalDate startDate, LocalDate endDate,
                                             int page, int size) {
        Page<LotteryResult> pageParam = new Page<>(page, size);
        Page<LotteryResult> result = (Page<LotteryResult>) lotteryResultMapper.selectPage(
                pageParam, issue, startDate, endDate);

        List<LotteryResultVO> voList = new ArrayList<>();
        for (LotteryResult r : result.getRecords()) {
            voList.add(toVO(r));
        }
        return PageResult.of(result.getTotal(), voList);
    }

    /**
     * 根据期号查询开奖号码（供其他模块调用）
     */
    public LotteryResult getByIssue(String issue) {
        return lotteryResultMapper.selectOne(
                new LambdaQueryWrapper<LotteryResult>()
                        .eq(LotteryResult::getIssue, issue));
    }

    /**
     * 按期号列表批量查询开奖号码，返回 issue -> LotteryResult 映射
     */
    public Map<String, LotteryResult> getByIssues(List<String> issues) {
        if (issues == null || issues.isEmpty()) return new HashMap<>();
        List<LotteryResult> list = lotteryResultMapper.selectByIssues(issues);
        Map<String, LotteryResult> result = new HashMap<>();
        for (LotteryResult r : list) {
            result.put(r.getIssue(), r);
        }
        return result;
    }

    /**
     * 冷热号分析：统计最近 10/20/50/100 期红球和蓝球的出现频率
     *
     * @return 各期数的热号（出现最多前5）和冷号（出现最少前5）
     */
    public Map<String, Object> analysis() {
        // 查询最近 100 期开奖号码（倒序）
        List<LotteryResult> allResults = lotteryResultMapper.selectList(
                new LambdaQueryWrapper<LotteryResult>()
                        .orderByDesc(LotteryResult::getIssue)
                        .last("LIMIT 100"));

        Map<String, Object> result = new HashMap<>();
        int[] periods = {10, 20, 50, 100};
        for (int p : periods) {
            result.put("periods" + p, calcHotCold(allResults, p));
        }
        return result;
    }

    /**
     * 计算指定期数内的冷热号
     */
    private Map<String, Object> calcHotCold(List<LotteryResult> results, int periods) {
        int limit = Math.min(periods, results.size());
        int[] redFreq = new int[34];  // 索引 1-33
        int[] blueFreq = new int[17]; // 索引 1-16

        for (int i = 0; i < limit; i++) {
            LotteryResult r = results.get(i);
            redFreq[r.getRed1()]++; redFreq[r.getRed2()]++; redFreq[r.getRed3()]++;
            redFreq[r.getRed4()]++; redFreq[r.getRed5()]++; redFreq[r.getRed6()]++;
            blueFreq[r.getBlue()]++;
        }

        // 红球排序：按频率降序
        List<int[]> redList = new ArrayList<>();
        for (int i = 1; i <= 33; i++) {
            redList.add(new int[]{i, redFreq[i]});
        }
        redList.sort((a, b) -> b[1] - a[1]);

        // 蓝球排序
        List<int[]> blueList = new ArrayList<>();
        for (int i = 1; i <= 16; i++) {
            blueList.add(new int[]{i, blueFreq[i]});
        }
        blueList.sort((a, b) -> b[1] - a[1]);

        // 取前5热号
        List<Integer> redHot = new ArrayList<>();
        List<Integer> blueHot = new ArrayList<>();
        for (int i = 0; i < 5 && i < redList.size(); i++) redHot.add(redList.get(i)[0]);
        for (int i = 0; i < 5 && i < blueList.size(); i++) blueHot.add(blueList.get(i)[0]);

        // 取后5冷号（出现过但频率最低的）
        List<Integer> redCold = new ArrayList<>();
        List<Integer> blueCold = new ArrayList<>();
        for (int i = redList.size() - 1; i >= Math.max(0, redList.size() - 5); i--) redCold.add(redList.get(i)[0]);
        for (int i = blueList.size() - 1; i >= Math.max(0, blueList.size() - 5); i--) blueCold.add(blueList.get(i)[0]);

        Map<String, Object> data = new HashMap<>();
        data.put("redHot", redHot);
        data.put("redCold", redCold);
        data.put("blueHot", blueHot);
        data.put("blueCold", blueCold);
        data.put("sampleSize", limit);
        return data;
    }

    private LotteryResultVO toVO(LotteryResult r) {
        return LotteryResultVO.builder()
                .id(r.getId())
                .issue(r.getIssue())
                .drawDate(r.getDrawDate())
                .reds(LotteryUtils.toRedList(r.getRed1(), r.getRed2(), r.getRed3(),
                        r.getRed4(), r.getRed5(), r.getRed6()))
                .blue(r.getBlue())
                .prizeText(r.getPrizeText())
                .deadline(r.getDeadline())
                .saleAmount(r.getSaleAmount() != null ? MONEY_FORMAT.format(r.getSaleAmount()) : null)
                .poolAmount(r.getPoolAmount() != null ? MONEY_FORMAT.format(r.getPoolAmount()) : null)
                .createdAt(r.getCreatedAt())
                .build();
    }
}
