package com.dcb.common.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 总记录数 */
    private long total;

    /** 当前页数据 */
    private List<T> list;

    public static <T> PageResult<T> of(long total, List<T> list) {
        return new PageResult<>(total, list);
    }
}
