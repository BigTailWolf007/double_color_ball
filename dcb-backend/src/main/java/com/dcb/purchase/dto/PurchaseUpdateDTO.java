package com.dcb.purchase.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 编辑购买记录DTO（仅允许修改注数和备注）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseUpdateDTO {

    /** 注数 */
    @NotNull(message = "注数不能为空")
    @Min(value = 1, message = "注数最少1注")
    @Max(value = 9999, message = "注数不能超过9999")
    private Integer quantity;

    /** 备注 */
    @Size(max = 500, message = "备注不能超过500字")
    private String remark;
}
