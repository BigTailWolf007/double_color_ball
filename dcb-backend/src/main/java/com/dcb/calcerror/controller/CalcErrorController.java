package com.dcb.calcerror.controller;

import com.dcb.calcerror.entity.CalcErrorLog;
import com.dcb.calcerror.mapper.CalcErrorLogMapper;
import com.dcb.common.result.PageResult;
import com.dcb.common.result.Result;
import com.dcb.common.service.AsyncCalcService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 计算错误日志管理接口
 */
@RestController
@RequestMapping("/api/calc-error")
@RequiredArgsConstructor
@Validated
public class CalcErrorController {

    private final CalcErrorLogMapper calcErrorLogMapper;
    private final AsyncCalcService asyncCalcService;

    /**
     * 分页查询错误日志
     */
    @GetMapping("/list")
    public Result<PageResult<CalcErrorLog>> list(
            @RequestParam(required = false) String issue,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Page<CalcErrorLog> pageParam = new Page<>(page, size);
        Page<CalcErrorLog> result = (Page<CalcErrorLog>) calcErrorLogMapper.selectPage(pageParam, issue);
        return Result.success(PageResult.of(result.getTotal(), result.getRecords()));
    }

    /**
     * 按 ID 列表重新计算（重试失败分片）
     */
    @PostMapping("/retry")
    public Result<String> retry(@RequestBody @NotEmpty(message = "ID列表不能为空") List<Long> ids) {
        for (Long id : ids) {
            asyncCalcService.retryErrorLog(id);
        }
        return Result.success("已提交 " + ids.size() + " 个重试任务");
    }

    /**
     * 忽略指定错误日志
     */
    @PostMapping("/ignore")
    public Result<String> ignore(@RequestBody @NotEmpty(message = "ID列表不能为空") List<Long> ids) {
        for (Long id : ids) {
            CalcErrorLog log = calcErrorLogMapper.selectById(id);
            if (log != null) {
                log.setStatus(2);
                calcErrorLogMapper.updateById(log);
            }
        }
        return Result.success("已忽略 " + ids.size() + " 条错误日志");
    }
}
