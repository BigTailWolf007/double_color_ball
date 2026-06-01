package com.dcb.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

/**
 * 彩票接口同步请求体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LotterySyncDTO {

    /** 期号 */
    @NotBlank(message = "期号不能为空")
    private String issue;
}
