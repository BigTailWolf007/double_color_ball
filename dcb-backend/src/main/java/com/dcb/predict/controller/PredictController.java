package com.dcb.predict.controller;

import com.dcb.common.result.PageResult;
import com.dcb.common.result.Result;
import com.dcb.predict.dto.PredictSaveDTO;
import com.dcb.predict.service.PredictService;
import com.dcb.predict.vo.PredictRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.List;

/**
 * 预测号码接口
 */
@RestController
@RequestMapping("/api/predict")
@RequiredArgsConstructor
@Validated
public class PredictController {

    private final PredictService predictService;

    /** 保存规则推荐模块生成的预测号码，若该期已开奖则立即计算命中结果 */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody @Valid List<PredictSaveDTO> dtoList) {
        predictService.save(dtoList);
        return Result.success();
    }

    /** 手动触发指定期号的预测命中结果补算，返回更新条数 */
    @PostMapping("/calc/{issue}")
    public Result<Integer> calc(@PathVariable String issue) {
        return Result.success(predictService.calc(issue));
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
}
