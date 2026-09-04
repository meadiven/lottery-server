package com.lottery.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 支持采集的主流彩种（官方公开公示）。
 */
@Getter
@RequiredArgsConstructor
public enum LotteryCodeEnum {

    SSQ("SSQ", "双色球", "WELFARE", "ssq"),
    QLC("QLC", "七乐彩", "WELFARE", "qlc"),
    FC3D("FC3D", "福彩3D", "WELFARE", "3d"),
    DLT("DLT", "超级大乐透", "SPORT", "85"),
    PL3("PL3", "排列3", "SPORT", "35"),
    PL5("PL5", "排列5", "SPORT", "350133");

    private final String code;
    private final String name;
    /** WELFARE / SPORT */
    private final String category;
    /** 数据源侧参数标识 */
    private final String sourceKey;

    public static LotteryCodeEnum of(String code) {
        for (LotteryCodeEnum value : values()) {
            if (value.code.equalsIgnoreCase(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("不支持的彩种编码: " + code);
    }

    public boolean isWelfare() {
        return "WELFARE".equals(category);
    }
}
