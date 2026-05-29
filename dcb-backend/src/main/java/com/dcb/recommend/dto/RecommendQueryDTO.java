package com.dcb.recommend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 号码推荐查询条件DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendQueryDTO {

    /** 和值最小值（可选，红球之和下限，范围21~183） */
    @Min(value = 21, message = "和值最小值不能小于21")
    @Max(value = 183, message = "和值最小值不能大于183")
    private Integer sumMin;

    /** 和值最大值（可选，红球之和上限，范围21~183） */
    @Min(value = 21, message = "和值最大值不能小于21")
    @Max(value = 183, message = "和值最大值不能大于183")
    private Integer sumMax;

    /** 区间比，格式"低:中:高"，如"2:2:2"，三数之和须为6（可选） */
    private String zoneRatio;

    /** 奇偶比，格式"奇:偶"，如"3:3"，两数之和须为6（可选） */
    private String oddEvenRatio;

    /** 剔除红球号码列表，范围1~33（可选） */
    private List<Integer> excludeRed;

    /** 指定蓝球号码列表，范围1~16，至少1个最多3个（必填） */
    @NotEmpty(message = "蓝球至少选择1个")
    @Size(max = 3, message = "蓝球最多选择3个")
    private List<Integer> includeBlue;

    /** 页码，默认1 */
    @Builder.Default
    private Integer page = 1;

    /** 每页条数，默认20 */
    @Builder.Default
    private Integer pageSize = 20;
}
