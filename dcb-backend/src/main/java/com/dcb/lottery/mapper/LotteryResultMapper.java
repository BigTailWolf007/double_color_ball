package com.dcb.lottery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcb.lottery.entity.LotteryResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 开奖号码 Mapper
 */
@Mapper
public interface LotteryResultMapper extends BaseMapper<LotteryResult> {

    /**
     * 分页查询开奖号码
     */
    IPage<LotteryResult> selectPage(Page<LotteryResult> page,
                                    @Param("issue") String issue,
                                    @Param("startDate") LocalDate startDate,
                                    @Param("endDate") LocalDate endDate);

    /**
     * 按 ballKey 列表批量查询开奖号码
     */
    List<LotteryResult> selectByBallKeys(@Param("keys") List<String> keys);

    /**
     * 按期号列表批量查询开奖号码
     */
    List<LotteryResult> selectByIssues(@Param("issues") List<String> issues);
}
