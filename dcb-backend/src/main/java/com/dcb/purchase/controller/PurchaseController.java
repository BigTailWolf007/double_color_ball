package com.dcb.purchase.controller;

import com.dcb.common.exception.BizException;
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

import javax.servlet.http.HttpServletRequest;

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
    private final HttpServletRequest request;

    /** 批量录入购买记录，自动计算中奖等级 */
    @PostMapping("/add")
    public Result<Void> add(@RequestBody @Valid List<PurchaseAddDTO> dtoList) {
        purchaseService.add(dtoList, getUserId());  // 写入用真实 userId，不能为 null
        return Result.success();
    }

    /** 手动触发指定期号的中奖等级补算，返回更新条数 */
    @PostMapping("/calc/{issue}")
    public Result<Integer> calc(@PathVariable String issue) {
        return Result.success(purchaseService.calc(issue));
    }

    /** 按 ID 列表强制重算中奖等级。管理员可重算全部，普通用户仅能重算自己的 */
    @PostMapping("/recalc")
    public Result<Integer> recalc(@RequestBody List<Long> ids) {
        return Result.success(purchaseService.recalcByIds(ids, getUserIdForFilter(), getRole()));
    }

    /** 编辑购买记录（仅允许修改注数和备注），期号不可修改 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid PurchaseUpdateDTO dto) {
        purchaseService.update(id, dto, getUserIdForFilter(), getRole());
        return Result.success();
    }

    /** 删除购买记录 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        purchaseService.delete(id, getUserIdForFilter(), getRole());
        return Result.success();
    }

    /** 分页查询购买记录，支持按期号、中奖等级、用户筛选。前端传入 userId 需与 Token 一致 */
    @GetMapping("/list")
    public Result<PageResult<PurchaseRecordVO>> list(
            @RequestParam(required = false) String issue,
            @RequestParam(required = false) String prizeLevels,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        List<Integer> levelList = null;
        if (prizeLevels != null && !prizeLevels.isEmpty()) {
            levelList = new ArrayList<>();
            for (String s : prizeLevels.split("_")) {
                try { levelList.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
            }
        }
        Long filterUserId = userId != null ? validateAndResolveUserId(userId) : getUserIdForFilter();
        log.info("查询购买记录：issue={}, prizeLevels={}, userId={}, filterUserId={}", issue, prizeLevels, userId, filterUserId);
        return Result.success(purchaseService.list(issue, levelList, page, size, filterUserId));
    }

    /** 汇总统计：总投入、总奖金、盈亏。前端传入 userId 需与 Token 一致 */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(
            @RequestParam(required = false) String issue,
            @RequestParam(required = false) String prizeLevels,
            @RequestParam(required = false) Long userId) {
        List<Integer> levelList = null;
        if (prizeLevels != null && !prizeLevels.isEmpty()) {
            levelList = new ArrayList<>();
            for (String s : prizeLevels.split("_")) {
                try { levelList.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
            }
        }
        Long filterUserId = userId != null ? validateAndResolveUserId(userId) : getUserIdForFilter();
        return Result.success(purchaseService.summary(issue, levelList, filterUserId));
    }

    /** 按期号删除该期所有购买记录。管理员删除全部，普通用户仅删除自己的 */
    @DeleteMapping("/issue/{issue}")
    public Result<Integer> deleteByIssue(@PathVariable String issue) {
        return Result.success(purchaseService.deleteByIssue(issue, getUserIdForFilter()));
    }

    /** 按 ID 列表批量删除购买记录。管理员可删全部，普通用户仅能删自己的 */
    @PostMapping("/batch-delete")
    public Result<Integer> deleteByIds(@RequestBody @NotEmpty(message = "ID列表不能为空") List<Long> ids) {
        return Result.success(purchaseService.deleteByIds(ids, getUserIdForFilter(), getRole()));
    }

    /** 模糊查询期号，倒序返回最多10个，用于输入框下拉提示 */
    @GetMapping("/issue-suggest")
    public Result<List<String>> issueSuggest(@RequestParam(defaultValue = "") String q) {
        return Result.success(purchaseService.suggestIssues(q));
    }

    /** 模糊查询用户名，返回最多10个，用于用户筛选下拉提示（仅管理员可用） */
    @GetMapping("/user-suggest")
    public Result<List<Map<String, Object>>> userSuggest(@RequestParam(defaultValue = "") String q) {
        if (!"ADMIN".equals(getRole())) return Result.success(new ArrayList<>());
        return Result.success(purchaseService.suggestUsers(q));
    }

    // ========== 权限辅助 ==========

    private Long validateAndResolveUserId(Long reqUserId) {
        Long jwtUserId = getUserId();
        boolean isAdmin = "ADMIN".equals(getRole());
        if (isAdmin) {
            if (reqUserId == null) return jwtUserId;
            if (reqUserId == -1L) return null;
            return reqUserId;
        }
        if (reqUserId == null) return jwtUserId;
        if (!jwtUserId.equals(reqUserId)) {
            throw new BizException("无权操作：用户ID不匹配");
        }
        return reqUserId;
    }

    /** ADMIN 返回 null（查询不过滤），USER 返回自身 userId */
    private Long getUserIdForFilter() {
        String role = (String) request.getAttribute("role");
        if ("ADMIN".equals(role)) return null;
        return getUserId();
    }

    /** 获取当前登录用户ID（始终返回真实值） */
    private Long getUserId() {
        return (Long) request.getAttribute("userId");
    }

    private String getRole() {
        String role = (String) request.getAttribute("role");
        return role != null ? role : "USER";
    }
}
