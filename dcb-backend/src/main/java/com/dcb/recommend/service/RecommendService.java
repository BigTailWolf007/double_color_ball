package com.dcb.recommend.service;

import com.dcb.common.exception.BizException;
import com.dcb.predict.dto.PredictSaveDTO;
import com.dcb.predict.service.PredictService;
import com.dcb.recommend.dto.RecommendQueryDTO;
import com.dcb.recommend.dto.RecommendSavePredictDTO;
import com.dcb.recommend.vo.RecommendCacheVO;
import com.dcb.recommend.vo.RecommendResultVO;
import com.dcb.recommend.vo.RecommendResultVO.NumberGroupVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 号码推荐服务：负责参数校验、编排缓存查询、分页切片、保存预测
 * <p>
 * 核心计算与缓存逻辑委托给 {@link RecommendCacheService}，
 * 确保 @Cacheable 通过 Spring AOP 代理生效。
 */
@Service
@RequiredArgsConstructor
public class RecommendService {

    private final RecommendCacheService recommendCacheService;
    private final PredictService predictService;

    /**
     * 根据过滤条件生成符合条件的号码组合（带缓存）
     */
    public RecommendResultVO generate(RecommendQueryDTO dto) {
        validateParams(dto);

        // 用查询条件（不含分页）生成缓存 key，命中缓存则跳过全量计算
        String cacheKey = recommendCacheService.buildCacheKey(dto);
        RecommendCacheVO cached = recommendCacheService.computeAllGroups(cacheKey, dto);

        // 分页切片
        int page = dto.getPage() != null && dto.getPage() > 0 ? dto.getPage() : 1;
        int pageSize = dto.getPageSize() != null && dto.getPageSize() > 0 ? dto.getPageSize() : 20;
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, cached.getGroups().size());
        List<NumberGroupVO> pageList = fromIndex < cached.getGroups().size()
                ? cached.getGroups().subList(fromIndex, toIndex) : new ArrayList<>();

        return RecommendResultVO.builder()
                .total(cached.getTotal())
                .truncated(cached.isTruncated())
                .list(pageList)
                .build();
    }

    /**
     * 从缓存取全量数据，批量保存为预测记录
     */
    public int savePredict(RecommendSavePredictDTO dto, Long userId) {
        RecommendQueryDTO queryDTO = RecommendQueryDTO.builder()
                .sumMin(dto.getSumMin())
                .sumMax(dto.getSumMax())
                .zoneRatio(dto.getZoneRatio())
                .oddEvenRatio(dto.getOddEvenRatio())
                .excludeRed(dto.getExcludeRed())
                .includeBlue(dto.getIncludeBlue())
                .build();
        validateParams(queryDTO);

        String cacheKey = recommendCacheService.buildCacheKey(queryDTO);
        RecommendCacheVO cached = recommendCacheService.computeAllGroups(cacheKey, queryDTO);

        List<PredictSaveDTO> payload = new ArrayList<>(cached.getGroups().size());
        for (NumberGroupVO g : cached.getGroups()) {
            payload.add(PredictSaveDTO.builder()
                    .issue(dto.getIssue())
                    .red1(g.getRed().get(0)).red2(g.getRed().get(1)).red3(g.getRed().get(2))
                    .red4(g.getRed().get(3)).red5(g.getRed().get(4)).red6(g.getRed().get(5))
                    .blue(g.getBlue())
                    .build());
        }

        predictService.save(payload, userId);
        return payload.size();
    }

    private void validateParams(RecommendQueryDTO dto) {
        if (dto.getSumMin() != null && dto.getSumMax() != null
                && dto.getSumMin() > dto.getSumMax()) {
            throw new BizException("和值最小值不能大于最大值");
        }
        if (dto.getExcludeRed() != null) {
            for (int n : dto.getExcludeRed()) {
                if (n < 1 || n > 33) throw new BizException("剔除红球号码范围为1~33");
            }
        }
        for (int n : dto.getIncludeBlue()) {
            if (n < 1 || n > 16) throw new BizException("蓝球号码范围为1~16");
        }
    }
}
