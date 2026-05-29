package com.dcb.recommend.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 号码推荐结果VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendResultVO {

    /** 符合条件的总组合数 */
    private Long total;

    /** 是否因超过上限被截断 */
    private Boolean truncated;

    /** 当前页号码列表 */
    private List<NumberGroupVO> list;

    /**
     * 单组号码
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NumberGroupVO {

        /** 6个红球（升序） */
        private List<Integer> red;

        /** 蓝球 */
        private Integer blue;
    }
}
