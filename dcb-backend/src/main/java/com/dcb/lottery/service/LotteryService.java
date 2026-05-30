package com.dcb.lottery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcb.common.exception.BizException;
import com.dcb.common.result.PageResult;
import com.dcb.common.util.LotteryUtils;
import com.dcb.lottery.dto.LotteryAddDTO;
import com.dcb.lottery.entity.LotteryResult;
import com.dcb.lottery.mapper.LotteryResultMapper;
import com.dcb.lottery.vo.LotteryResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 开奖号码服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LotteryService {

    private final LotteryResultMapper lotteryResultMapper;

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

    private LotteryResultVO toVO(LotteryResult r) {
        return LotteryResultVO.builder()
                .id(r.getId())
                .issue(r.getIssue())
                .drawDate(r.getDrawDate())
                .reds(LotteryUtils.toRedList(r.getRed1(), r.getRed2(), r.getRed3(),
                        r.getRed4(), r.getRed5(), r.getRed6()))
                .blue(r.getBlue())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
