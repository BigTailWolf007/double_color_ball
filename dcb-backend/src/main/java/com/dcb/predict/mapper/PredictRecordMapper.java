package com.dcb.predict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcb.predict.entity.PredictRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 预测号码 Mapper
 */
@Mapper
public interface PredictRecordMapper extends BaseMapper<PredictRecord> {

    /**
     * 分页查询预测号码
     */
    IPage<PredictRecord> selectPageByIssue(Page<PredictRecord> page,
                                            @Param("issue") String issue);

    /**
     * 批量更新命中结果
     */
    void batchUpdateHitResult(@Param("list") List<PredictRecord> list);
}
