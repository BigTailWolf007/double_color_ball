package com.dcb.predict.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预测号码VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictRecordVO {

    /** 主键 */
    private Long id;

    /** 目标期号 */
    private String issue;

    /** 红球列表（已排序） */
    private List<Integer> reds;

    /** 蓝球 */
    private Integer blue;

    /** 命中红球数(NULL=待开奖) */
    private Integer hitRed;

    /** 是否命中蓝球(0/1,NULL=待开奖) */
    private Integer hitBlue;

    /** 命中等级值(0-6,NULL=待开奖) */
    private Integer prizeLevel;

    /** 命中等级描述 */
    private String prizeLevelDesc;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
