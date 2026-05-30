package com.dcb.predict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcb.predict.dto.BallKeyRowDTO;
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

    /**
     * 查询指定期号已存在的所有 ballKey
     */
    List<String> selectBallKeysByIssue(@Param("issue") String issue);

    /**
     * 批量插入预测号码
     */
    void batchInsert(@Param("list") List<PredictRecord> list);

    /**
     * 批量查询多个期号已存在的所有 ballKey，返回 {issue, ballKey} 列表
     */
    List<BallKeyRowDTO> selectBallKeysByIssues(@Param("issues") List<String> issues);
}
