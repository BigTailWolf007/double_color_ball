package com.dcb.predict.controller;

import com.dcb.common.result.PageResult;
import com.dcb.common.result.Result;
import com.dcb.common.service.AsyncCalcService;
import com.dcb.predict.dto.PredictSaveDTO;
import com.dcb.predict.service.PredictService;
import com.dcb.predict.vo.PredictRecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 预测号码接口
 */
@Slf4j
@RestController
@RequestMapping("/api/predict")
@RequiredArgsConstructor
@Validated
public class PredictController {

    private final PredictService predictService;
    private final AsyncCalcService asyncCalcService;

    /** 保存规则推荐模块生成的预测号码，若该期已开奖则立即计算命中结果 */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody @Valid List<PredictSaveDTO> dtoList) {
        predictService.save(dtoList);
        return Result.success();
    }

    /** 手动触发重新计算（异步多线程） */
    @PostMapping("/calc/{issue}")
    public Result<String> calc(@PathVariable String issue) {
        log.info("提交异步重新计算预测命中结果，期号：{}", issue);
        asyncCalcService.asyncRecalcPredict(issue);
        return Result.success("已提交异步重新计算任务");
    }

    /** 分页查询预测号码，支持按目标期号筛选 */
    @GetMapping("/list")
    public Result<PageResult<PredictRecordVO>> list(
            @RequestParam(required = false) String issue,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.success(predictService.list(issue, page, size));
    }

    /** 按 ID 删除单条预测记录 */
    @DeleteMapping("/{id}")
    public Result<Void> deleteById(@PathVariable Long id) {
        predictService.deleteById(id);
        return Result.success();
    }

    /** 按期号删除该期所有预测记录，返回删除条数 */
    @DeleteMapping("/issue/{issue}")
    public Result<Integer> deleteByIssue(@PathVariable String issue) {
        return Result.success(predictService.deleteByIssue(issue));
    }

    /** 查询所有不重复的期号列表 */
    @GetMapping("/issues")
    public Result<List<String>> listIssues() {
        return Result.success(predictService.listIssues());
    }

    /** 流式导出指定期号的预测号码为 TXT 文件 */
    @PostMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestBody @NotEmpty(message = "期号列表不能为空") List<String> issues) throws IOException {
        String filename = URLEncoder.encode("预测号码_" + issues.size() + "期.txt", StandardCharsets.UTF_8.name());
        StreamingResponseBody body = outputStream -> predictService.exportTxt(issues, outputStream);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .body(body);
    }

    /** 按期号列表将预测号码同步到购买记录，返回同步条数 */
    @PostMapping("/sync-by-issues")
    public Result<Integer> syncByIssues(@RequestBody @NotEmpty(message = "期号列表不能为空") List<String> issues) {
        return Result.success(predictService.syncToPurchaseByIssues(issues));
    }

    /** 按 ID 列表将预测号码同步到购买记录，返回同步条数 */
    @PostMapping("/sync-by-ids")
    public Result<Integer> syncByIds(@RequestBody @NotEmpty(message = "ID列表不能为空") List<Long> ids) {
        return Result.success(predictService.syncToPurchaseByIds(ids));
    }

    /** 模糊查询期号，倒序返回最多10个，用于输入框下拉提示 */
    @GetMapping("/issue-suggest")
    public Result<List<String>> issueSuggest(@RequestParam(defaultValue = "") String q) {
        return Result.success(predictService.suggestIssues(q));
    }
}
