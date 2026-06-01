package com.dcb.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcb.purchase.entity.PurchaseRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 购买记录 Mapper
 */
@Mapper
public interface PurchaseRecordMapper extends BaseMapper<PurchaseRecord> {

    /**
     * 分页查询购买记录
     */
    IPage<PurchaseRecord> selectPageByCondition(Page<PurchaseRecord> page,
                                                 @Param("issue") String issue,
                                                 @Param("prizeLevels") List<Integer> prizeLevels);

    /**
     * 汇总统计：总注数、总奖金
     */
    Map<String, Object> selectSummary(@Param("issue") String issue,
                                      @Param("prizeLevels") List<Integer> prizeLevels);

    /**
     * 批量更新中奖等级和奖金
     */
    void batchUpdatePrize(@Param("list") List<PurchaseRecord> list);

    /**
     * 查询指定期号已存在的所有 ballKey
     */
    List<String> selectBallKeysByIssue(@Param("issue") String issue);

    /**
     * 模糊查询期号，倒序返回最多 limit 个
     */
    List<String> selectIssuesByKeyword(@Param("keyword") String keyword, @Param("limit") int limit);
}
