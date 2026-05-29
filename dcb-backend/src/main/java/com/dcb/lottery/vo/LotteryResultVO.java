package com.dcb.lottery.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 开奖号码VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LotteryResultVO {

    /** 主键 */
    private Long id;

    /** 期号 */
    private String issue;

    /** 开奖日期 */
    private LocalDate drawDate;

    /** 红球列表（已排序） */
    private List<Integer> reds;

    /** 蓝球 */
    private Integer blue;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
