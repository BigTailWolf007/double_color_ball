package com.dcb.common.enums;

import lombok.Getter;

/**
 * 中奖等级枚举
 */
@Getter
public enum PrizeLevel {

    /** 一等奖：6红+1蓝，奖金浮动 */
    FIRST(1, "一等奖", 0),
    /** 二等奖：6红+0蓝，奖金浮动 */
    SECOND(2, "二等奖", 0),
    /** 三等奖：5红+1蓝 */
    THIRD(3, "三等奖", 3000),
    /** 四等奖：5红+0蓝 或 4红+1蓝 */
    FOURTH(4, "四等奖", 200),
    /** 五等奖：4红+0蓝 或 3红+1蓝 */
    FIFTH(5, "五等奖", 10),
    /** 六等奖：任意红+1蓝 */
    SIXTH(6, "六等奖", 5),
    /** 未中奖 */
    NO_PRIZE(0, "未中奖", 0);

    /** 等级值 */
    private final int level;

    /** 等级描述 */
    private final String desc;

    /** 固定奖金（元），0表示浮动 */
    private final int fixedPrize;

    PrizeLevel(int level, String desc, int fixedPrize) {
        this.level = level;
        this.desc = desc;
        this.fixedPrize = fixedPrize;
    }

    public static PrizeLevel ofLevel(int level) {
        for (PrizeLevel p : values()) {
            if (p.level == level) {
                return p;
            }
        }
        return NO_PRIZE;
    }
}
