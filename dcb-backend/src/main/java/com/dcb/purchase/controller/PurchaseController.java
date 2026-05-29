package com.dcb.purchase.controller;

import com.dcb.common.result.PageResult;
import com.dcb.common.result.Result;
import com.dcb.purchase.dto.PurchaseAddDTO;
import com.dcb.purchase.service.PurchaseService;
import com.dcb.purchase.vo.PurchaseRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.List;
import java.util.Map;

/**
 * 购买记录接口
 */
@RestController
@RequestMapping("/api/purchase")
@RequiredArgsConstructor
@Validated
public class PurchaseController {

    private final PurchaseService purchaseService;

    /** 批量录入购买记录，自动计算中奖等级 */
    @PostMapping("/add")
    public Result<Void> add(@RequestBody @Valid List<PurchaseAddDTO> dtoList) {
        purchaseService.add(dtoList);
        return Result.success();
    }

    /** 手动触发指定期号的中奖等级补算，返回更新条数 */
    @PostMapping("/calc/{issue}")
    public Result<Integer> calc(@PathVariable String issue) {
        return Result.success(purchaseService.calc(issue));
    }

    /** 删除购买记录 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        purchaseService.delete(id);
        return Result.success();
    }

    /** 分页查询购买记录，支持按期号、中奖等级筛选 */
    @GetMapping("/list")
    public Result<PageResult<PurchaseRecordVO>> list(
            @RequestParam(required = false) String issue,
            @RequestParam(required = false) Integer prizeLevel,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.success(purchaseService.list(issue, prizeLevel, page, size));
    }

    /** 汇总统计：总投入、总奖金、盈亏 */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.success(purchaseService.summary());
    }
}
