package com.dcb.purchase.controller;

import com.dcb.common.result.PageResult;
import com.dcb.common.result.Result;
import com.dcb.purchase.dto.PurchaseAddDTO;
import com.dcb.purchase.dto.PurchaseUpdateDTO;
import com.dcb.purchase.service.PurchaseService;
import com.dcb.purchase.vo.PurchaseRecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 购买记录接口
 */
@Slf4j
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

    /** 按 ID 列表强制重算中奖等级，返回更新条数 */
    @PostMapping("/recalc")
    public Result<Integer> recalc(@RequestBody List<Long> ids) {
        return Result.success(purchaseService.recalcByIds(ids));
    }

    /** 编辑购买记录（仅允许修改注数和备注），期号不可修改 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid PurchaseUpdateDTO dto) {
        purchaseService.update(id, dto);
        return Result.success();
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
            @RequestParam(required = false) String prizeLevels,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        List<Integer> levelList = null;
        if (prizeLevels != null && !prizeLevels.isEmpty()) {
            levelList = new ArrayList<>();
            for (String s : prizeLevels.split("_")) {
                try { levelList.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
            }
        }
        log.info("查询购买记录：issue={}, prizeLevels={}, parsed={}", issue, prizeLevels, levelList);
        return Result.success(purchaseService.list(issue, levelList, page, size));
    }

    /** 汇总统计：总投入、总奖金、盈亏 */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(
            @RequestParam(required = false) String issue,
            @RequestParam(required = false) String prizeLevels) {
        List<Integer> levelList = null;
        if (prizeLevels != null && !prizeLevels.isEmpty()) {
            levelList = new ArrayList<>();
            for (String s : prizeLevels.split("_")) {
                try { levelList.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
            }
        }
        return Result.success(purchaseService.summary(issue, levelList));
    }

    /** 按期号删除该期所有购买记录，返回删除条数 */
    @DeleteMapping("/issue/{issue}")
    public Result<Integer> deleteByIssue(@PathVariable String issue) {
        return Result.success(purchaseService.deleteByIssue(issue));
    }

    /** 按 ID 列表批量删除购买记录，返回删除条数 */
    @PostMapping("/batch-delete")
    public Result<Integer> deleteByIds(@RequestBody @NotEmpty(message = "ID列表不能为空") List<Long> ids) {
        return Result.success(purchaseService.deleteByIds(ids));
    }

    /** 模糊查询期号，倒序返回最多10个，用于输入框下拉提示 */
    @GetMapping("/issue-suggest")
    public Result<List<String>> issueSuggest(@RequestParam(defaultValue = "") String q) {
        return Result.success(purchaseService.suggestIssues(q));
    }
}
