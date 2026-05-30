package com.dcb.recommend.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 号码推荐缓存结果（含真实总数和截断标记）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendCacheVO {

    /** 符合条件的真实总组合数（可能超过 MAX_RESULT） */
    private long total;

    /** 是否因超过上限被截断 */
    private boolean truncated;

    /** 截断后的全量列表（最多 MAX_RESULT 条） */
    private List<RecommendResultVO.NumberGroupVO> groups;
}
