package com.dcb.recommend.controller;

import com.dcb.common.result.Result;
import com.dcb.recommend.dto.RecommendQueryDTO;
import com.dcb.recommend.dto.RecommendSavePredictDTO;
import com.dcb.recommend.service.RecommendService;
import com.dcb.recommend.vo.RecommendResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

/**
 * 号码推荐接口
 */
@RestController
@RequestMapping("/api/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;
    private final HttpServletRequest request;

    /** 根据过滤条件生成符合条件的号码组合 */
    @PostMapping("/generate")
    public Result<RecommendResultVO> generate(@RequestBody @Valid RecommendQueryDTO dto) {
        return Result.success(recommendService.generate(dto));
    }

    /** 将当前规则生成的全量号码保存为预测记录，返回保存条数 */
    @PostMapping("/save-predict")
    public Result<Integer> savePredict(@RequestBody @Valid RecommendSavePredictDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(recommendService.savePredict(dto, userId));
    }
}
