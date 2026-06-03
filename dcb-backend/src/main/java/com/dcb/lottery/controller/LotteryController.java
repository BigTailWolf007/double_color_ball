package com.dcb.lottery.controller;

import com.dcb.common.result.PageResult;
import com.dcb.common.result.Result;
import com.dcb.common.service.AsyncCalcService;
import com.dcb.auth.service.PermissionHelper;
import com.dcb.auth.service.AuthService;
import com.dcb.lottery.dto.LotteryAddDTO;
import com.dcb.lottery.dto.LotterySyncDTO;
import com.dcb.lottery.service.LotteryService;
import com.dcb.lottery.vo.LotteryResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 开奖号码接口
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/lottery")
@RequiredArgsConstructor
public class LotteryController {

    private final LotteryService lotteryService;
    private final AsyncCalcService asyncCalcService;
    private final AuthService authService;
    private final HttpServletRequest request;

    /** TXT 文件批量导入开奖号码 */
    @PostMapping("/import")
    public Result<Map<String, Object>> importTxt(@RequestParam("file") MultipartFile file) {
        PermissionHelper.checkFull(request, authService);
        return Result.success(lotteryService.importFromTxt(file));
    }

    /** 手动录入单条开奖号码 */
    @PostMapping("/add")
    public Result<Void> add(@Validated @RequestBody LotteryAddDTO dto) {
        PermissionHelper.checkFull(request, authService);
        lotteryService.add(dto);
        // 录入后触发异步多线程计算购买记录和预测号码的中奖情况
        String issue = dto.getIssue();
        asyncCalcService.asyncCalcPurchase(issue);
        asyncCalcService.asyncCalcPredict(issue);
        log.info("手动录入开奖号码，期号：{}，已提交异步计算任务", issue);
        return Result.success();
    }

    /** 彩票接口同步 */
    @PostMapping("/sync")
    public Result<Map<String, Object>> sync(@Validated @RequestBody LotterySyncDTO dto) {
        PermissionHelper.checkFull(request, authService);
        Map<String, Object> result = lotteryService.sync(dto);
        // 同步成功后触发异步多线程计算
        String issue = dto.getIssue();
        asyncCalcService.asyncCalcPurchase(issue);
        asyncCalcService.asyncCalcPredict(issue);
        result.put("calcMode", "async");
        log.info("同步完成，期号：{}，已提交异步计算任务", issue);
        return Result.success(result);
    }

    /** 删除开奖号码 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        PermissionHelper.checkFull(request, authService);
        lotteryService.delete(id);
        return Result.success();
    }

    /** 分页查询开奖号码，支持按期号、日期范围筛选 */
    @GetMapping("/list")
    public Result<PageResult<LotteryResultVO>> list(
            @RequestParam(required = false) String issue,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.success(lotteryService.list(issue, startDate, endDate, page, size));
    }

    /** 模糊查询期号，倒序返回最多10个，用于输入框下拉提示 */
    @GetMapping("/issue-suggest")
    public Result<List<String>> issueSuggest(@RequestParam(defaultValue = "") String q) {
        return Result.success(lotteryService.suggestIssues(q));
    }

    /** 冷热号分析 */
    @GetMapping("/analysis")
    public Result<Map<String, Object>> analysis() {
        return Result.success(lotteryService.analysis());
    }
}
