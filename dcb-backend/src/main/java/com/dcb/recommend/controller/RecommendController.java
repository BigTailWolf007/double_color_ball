package com.dcb.recommend.controller;

import com.dcb.common.result.Result;
import com.dcb.recommend.dto.RecommendQueryDTO;
import com.dcb.recommend.service.RecommendService;
import com.dcb.recommend.vo.RecommendResultVO;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 号码推荐接口
 */
@RestController
@RequestMapping("/api/recommend")
public class RecommendController {

    private final RecommendService recommendService;

    public RecommendController(RecommendService recommendService) {
        this.recommendService = recommendService;
    }

    /** 根据过滤条件生成符合条件的号码组合 */
    @PostMapping("/generate")
    public Result<RecommendResultVO> generate(@RequestBody @Valid RecommendQueryDTO dto) {
        return Result.success(recommendService.generate(dto));
    }
}
