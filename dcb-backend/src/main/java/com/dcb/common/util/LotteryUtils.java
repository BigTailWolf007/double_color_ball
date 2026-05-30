package com.dcb.common.util;

import com.dcb.common.enums.PrizeLevel;
import com.dcb.common.exception.BizException;
import com.dcb.lottery.dto.LotteryAddDTO;
import com.dcb.predict.dto.PredictSaveDTO;
import com.dcb.purchase.dto.PurchaseAddDTO;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 双色球号码工具类
 */
public class LotteryUtils {

    private LotteryUtils() {}

    /**
     * 校验红球合法性：6个、范围1-33、不重复
     */
    public static void validateRed(List<Integer> reds) {
        if (reds == null || reds.size() != 6) {
            throw new BizException("红球必须选择6个");
        }
        Set<Integer> set = new HashSet<>();
        for (Integer red : reds) {
            if (red < 1 || red > 33) {
                throw new BizException("红球范围为1-33，当前值：" + red);
            }
            if (!set.add(red)) {
                throw new BizException("红球不能重复，重复值：" + red);
            }
        }
    }

    /**
     * 校验蓝球合法性：范围1-16
     */
    public static void validateBlue(int blue) {
        if (blue < 1 || blue > 16) {
            throw new BizException("蓝球范围为1-16，当前值：" + blue);
        }
    }

    /**
     * 计算中奖等级
     *
     * @param buyReds   购买红球列表
     * @param buyBlue   购买蓝球
     * @param drawReds  开奖红球列表
     * @param drawBlue  开奖蓝球
     * @return 中奖等级
     */
    public static PrizeLevel calcPrize(List<Integer> buyReds, int buyBlue,
                                       List<Integer> drawReds, int drawBlue) {
        Set<Integer> drawRedSet = new HashSet<>(drawReds);
        int hitRed = 0;
        for (Integer red : buyReds) {
            if (drawRedSet.contains(red)) {
                hitRed++;
            }
        }
        boolean hitBlue = (buyBlue == drawBlue);

        if (hitRed == 6 && hitBlue) return PrizeLevel.FIRST;
        if (hitRed == 6)            return PrizeLevel.SECOND;
        if (hitRed == 5 && hitBlue) return PrizeLevel.THIRD;
        if (hitRed == 5 || (hitRed == 4 && hitBlue)) return PrizeLevel.FOURTH;
        if (hitRed == 4 || (hitRed == 3 && hitBlue)) return PrizeLevel.FIFTH;
        if (hitBlue)                return PrizeLevel.SIXTH;
        return PrizeLevel.NO_PRIZE;
    }

    /**
     * 构建号码复合键：期号-红1-红2-红3-红4-红5-红6-蓝（红球已排序）
     */
    public static String buildBallKey(PredictSaveDTO dto) {
        return buildBallKey(dto.getIssue(),
                dto.getRed1(), dto.getRed2(), dto.getRed3(),
                dto.getRed4(), dto.getRed5(), dto.getRed6(), dto.getBlue());
    }

    /**
     * 构建号码复合键：期号-红1-红2-红3-红4-红5-红6-蓝（红球已排序）
     */
    public static String buildBallKey(LotteryAddDTO dto) {
        return buildBallKey(dto.getIssue(),
                dto.getRed1(), dto.getRed2(), dto.getRed3(),
                dto.getRed4(), dto.getRed5(), dto.getRed6(), dto.getBlue());
    }
    /**
     * 构建号码复合键：期号-红1-红2-红3-红4-红5-红6-蓝（红球已排序）
     */
    public static String buildBallKey(PurchaseAddDTO dto) {
        return buildBallKey(dto.getIssue(),
                dto.getRed1(), dto.getRed2(), dto.getRed3(),
                dto.getRed4(), dto.getRed5(), dto.getRed6(), dto.getBlue());
    }

    /**
     * 构建号码复合键：期号-红1-红2-红3-红4-红5-红6-蓝（红球已排序）
     */
    public static String buildBallKey(String issue, int r1, int r2, int r3, int r4, int r5, int r6, int blue) {
        int[] reds = {r1, r2, r3, r4, r5, r6};
        Arrays.sort(reds);
        return issue + "-" + reds[0] + "-" + reds[1] + "-" + reds[2] + "-" + reds[3] + "-" + reds[4] + "-" + reds[5] + "-" + blue;
    }

    /**
     * 将红球数组转为有序列表（用于展示）
     */
    public static List<Integer> toRedList(int r1, int r2, int r3, int r4, int r5, int r6) {
        Integer[] arr = {r1, r2, r3, r4, r5, r6};
        Arrays.sort(arr);
        return Arrays.asList(arr);
    }
}
