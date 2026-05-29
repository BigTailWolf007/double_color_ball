package com.dcb.lottery.controller;

import com.dcb.common.result.PageResult;
import com.dcb.common.result.Result;
import com.dcb.lottery.dto.LotteryAddDTO;
import com.dcb.lottery.service.LotteryService;
import com.dcb.lottery.vo.LotteryResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.time.LocalDate;
import java.util.Map;

/**
 * 开奖号码接口
 */
@Validated
@RestController
@RequestMapping("/api/lottery")
@RequiredArgsConstructor
public class LotteryController {

    private final LotteryService lotteryService;

    /** TXT 文件批量导入开奖号码 */
    @PostMapping("/import")
    public Result<Map<String, Object>> importTxt(@RequestParam("file") MultipartFile file) {
        return Result.success(lotteryService.importFromTxt(file));
    }

    /** 手动录入单条开奖号码 */
    @PostMapping("/add")
    public Result<Void> add(@Validated @RequestBody LotteryAddDTO dto) {
        lotteryService.add(dto);
        return Result.success();
    }

    /** 删除开奖号码 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
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
}
