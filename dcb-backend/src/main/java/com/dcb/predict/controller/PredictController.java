package com.dcb.predict.controller;

import com.dcb.auth.entity.User;
import com.dcb.auth.mapper.UserMapper;
import com.dcb.common.exception.BizException;
import com.dcb.common.result.PageResult;
import com.dcb.common.result.Result;
import com.dcb.common.service.AsyncCalcService;
import com.dcb.predict.dto.PredictSaveDTO;
import com.dcb.predict.entity.PredictRecord;
import com.dcb.predict.service.PredictService;
import com.dcb.predict.vo.PredictRecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.http.HttpServletRequest;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final UserMapper userMapper;
    private final HttpServletRequest request;

    /** 保存规则推荐模块生成的预测号码，若该期已开奖则立即计算命中结果 */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody @Valid List<PredictSaveDTO> dtoList) {
        predictService.save(dtoList, getUserId());
        return Result.success();
    }

    /** 手动触发重新计算（异步多线程）。普通用户只能重算自己有待计算记录的期号 */
    @PostMapping("/calc/{issue}")
    public Result<String> calc(@PathVariable String issue) {
        if (!"ADMIN".equals(getRole())) {
            // 普通用户校验：该期号是否有自己的预测记录
            Long userId = getUserId();
            List<PredictRecord> records = predictService.listByIssueAndUser(issue, userId);
            if (records.isEmpty()) {
                throw new BizException("该期号下没有您的预测记录");
            }
        }
        log.info("提交异步重新计算预测命中结果，期号：{}", issue);
        asyncCalcService.asyncRecalcPredict(issue);
        return Result.success("已提交异步重新计算任务");
    }

    /** 分页查询预测号码，支持按期号、用户筛选。前端传入 userId 需与 Token 一致 */
    @GetMapping("/list")
    public Result<PageResult<PredictRecordVO>> list(
            @RequestParam(required = false) String issue,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Long filterUserId = userId != null ? validateAndResolveUserId(userId) : getUserIdForFilter();
        return Result.success(predictService.list(issue, page, size, filterUserId));
    }

    /** 按 ID 删除单条预测记录 */
    @DeleteMapping("/{id}")
    public Result<Void> deleteById(@PathVariable Long id) {
        predictService.deleteById(id, getUserIdForFilter(), getRole());
        return Result.success();
    }

    /** 按期号删除该期所有预测记录。前端传入 userId 需与 Token 一致 */
    @DeleteMapping("/issue/{issue}")
    public Result<Integer> deleteByIssue(@PathVariable String issue,
                                          @RequestParam Long userId) {
        Long filterUserId = validateAndResolveUserId(userId);
        return Result.success(predictService.deleteByIssue(issue, filterUserId));
    }

    /** 查询所有不重复的期号列表 */
    @GetMapping("/issues")
    public Result<List<String>> listIssues() {
        return Result.success(predictService.listIssues());
    }

    /** 流式导出指定期号的预测号码为 TXT 文件。前端传入 userId 需与 Token 一致 */
    @PostMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestBody Map<String, Object> body) throws IOException {
        @SuppressWarnings("unchecked")
        List<String> issues = (List<String>) body.get("issues");
        if (issues == null || issues.isEmpty()) throw new BizException("期号列表不能为空");
        Long reqUserId = body.get("userId") != null ? ((Number) body.get("userId")).longValue() : null;
        Long filterUserId = validateAndResolveUserId(reqUserId);
        String filename = URLEncoder.encode("预测号码_" + issues.size() + "期.txt", StandardCharsets.UTF_8.name());
        StreamingResponseBody streamBody = outputStream -> predictService.exportTxt(issues, filterUserId, outputStream);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .body(streamBody);
    }

    /** 按期号列表将预测号码同步到购买记录。前端传入 userId 需与 Token 一致 */
    @PostMapping("/sync-by-issues")
    public Result<Integer> syncByIssues(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> issues = (List<String>) body.get("issues");
        if (issues == null || issues.isEmpty()) throw new BizException("期号列表不能为空");
        Long reqUserId = body.get("userId") != null ? ((Number) body.get("userId")).longValue() : null;
        Long filterUserId = validateAndResolveUserId(reqUserId);
        return Result.success(predictService.syncToPurchaseByIssues(issues, filterUserId));
    }

    /** 按 ID 列表将预测号码同步到购买记录。前端传入 userId 需与 Token 一致 */
    @PostMapping("/sync-by-ids")
    public Result<Integer> syncByIds(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) body.get("ids");
        if (rawIds == null || rawIds.isEmpty()) throw new BizException("ID列表不能为空");
        List<Long> ids = rawIds.stream().map(Long::valueOf).collect(Collectors.toList());
        Long reqUserId = body.get("userId") != null ? ((Number) body.get("userId")).longValue() : null;
        Long filterUserId = validateAndResolveUserId(reqUserId);
        return Result.success(predictService.syncToPurchaseByIds(ids, filterUserId, getRole()));
    }

    /** 模糊查询期号，倒序返回最多10个，用于输入框下拉提示 */
    @GetMapping("/issue-suggest")
    public Result<List<String>> issueSuggest(@RequestParam(defaultValue = "") String q) {
        return Result.success(predictService.suggestIssues(q));
    }

    /** 模糊查询用户名，返回最多10个，用于用户筛选下拉提示 */
    @GetMapping("/user-suggest")
    public Result<List<Map<String, Object>>> userSuggest(@RequestParam(defaultValue = "") String q) {
        if (!"ADMIN".equals(getRole())) return Result.success(Collections.emptyList());
        return Result.success(predictService.suggestUsers(q));
    }

    /** 查询有预测记录的用户列表。管理员可看到所有用户，普通用户只看到自己 */
    @GetMapping("/users")
    public Result<List<Map<String, Object>>> listUsers() {
        if ("ADMIN".equals(getRole())) {
            return Result.success(predictService.listUsers());
        }
        Long userId = getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) return Result.success(Collections.emptyList());
        Map<String, Object> m = new HashMap<>();
        m.put("id", user.getId());
        m.put("username", user.getUsername());
        m.put("nickname", user.getNickname());
        return Result.success(Collections.singletonList(m));
    }

    // ========== 权限辅助 ==========

    /**
     * 校验前端传入的 userId 与 JWT Token 中的 userId 是否匹配。
     * 管理员传 -1 表示"全部"(返回 null)，传具体值则使用该值。
     * 普通用户只能传自己的 ID，否则抛出权限异常。
     */
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

    private Long getUserIdForFilter() {
        String role = (String) request.getAttribute("role");
        if ("ADMIN".equals(role)) return null;
        return getUserId();
    }

    private Long getUserId() {
        return (Long) request.getAttribute("userId");
    }

    private String getRole() {
        String role = (String) request.getAttribute("role");
        return role != null ? role : "USER";
    }
}
