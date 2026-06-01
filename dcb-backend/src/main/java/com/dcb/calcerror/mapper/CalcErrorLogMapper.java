package com.dcb.calcerror.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcb.calcerror.entity.CalcErrorLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 计算错误日志 Mapper
 */
@Mapper
public interface CalcErrorLogMapper extends BaseMapper<CalcErrorLog> {

    /** 分页查询错误日志，支持按期号筛选 */
    IPage<CalcErrorLog> selectPage(Page<CalcErrorLog> page,
                                   @Param("issue") String issue);
}
