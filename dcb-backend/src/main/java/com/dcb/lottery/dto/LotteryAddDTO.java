package com.dcb.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 手动录入开奖号码DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LotteryAddDTO {

    /** 期号 */
    @NotBlank(message = "期号不能为空")
    private String issue;

    /** 开奖日期 */
    private LocalDate drawDate;

    /** 红球1 */
    @NotNull(message = "红球1不能为空")
    @Min(value = 1, message = "红球范围为1-33")
    @Max(value = 33, message = "红球范围为1-33")
    private Integer red1;

    /** 红球2 */
    @NotNull(message = "红球2不能为空")
    @Min(value = 1, message = "红球范围为1-33")
    @Max(value = 33, message = "红球范围为1-33")
    private Integer red2;

    /** 红球3 */
    @NotNull(message = "红球3不能为空")
    @Min(value = 1, message = "红球范围为1-33")
    @Max(value = 33, message = "红球范围为1-33")
    private Integer red3;

    /** 红球4 */
    @NotNull(message = "红球4不能为空")
    @Min(value = 1, message = "红球范围为1-33")
    @Max(value = 33, message = "红球范围为1-33")
    private Integer red4;

    /** 红球5 */
    @NotNull(message = "红球5不能为空")
    @Min(value = 1, message = "红球范围为1-33")
    @Max(value = 33, message = "红球范围为1-33")
    private Integer red5;

    /** 红球6 */
    @NotNull(message = "红球6不能为空")
    @Min(value = 1, message = "红球范围为1-33")
    @Max(value = 33, message = "红球范围为1-33")
    private Integer red6;

    /** 蓝球 */
    @NotNull(message = "蓝球不能为空")
    @Min(value = 1, message = "蓝球范围为1-16")
    @Max(value = 16, message = "蓝球范围为1-16")
    private Integer blue;
}
