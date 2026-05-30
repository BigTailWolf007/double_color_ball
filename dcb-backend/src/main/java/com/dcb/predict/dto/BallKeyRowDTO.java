package com.dcb.predict.dto;

import lombok.Data;

/**
 * 批量查询 ballKey 的结果行
 */
@Data
public class BallKeyRowDTO {

    /** 期号 */
    private String issue;

    /** 号码复合键 */
    private String ballKey;
}
