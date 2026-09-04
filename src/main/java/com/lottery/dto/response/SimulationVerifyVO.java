package com.lottery.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 均匀抽样算法相对「公平开奖零假设」的卡方拟合优度验证结果。
 */
@Data
@Builder
public class SimulationVerifyVO {

    private String lotteryCode;
    private String algorithmVersion;
    private String nullHypothesis;
    /** 蒙特卡洛试验组数 */
    private int trials;
    private double significanceLevel;

    private ZoneStat front;
    private ZoneStat back;

    /** 前区、后区均未拒绝零假设则为 true */
    private boolean passed;
    private String summary;

    @Data
    @Builder
    public static class ZoneStat {
        private String name;
        private int poolSize;
        private int pickCount;
        private double expectedPerBall;
        private double chiSquare;
        private int degreesOfFreedom;
        private double criticalValue;
        /** chiSquare &lt; criticalValue 则接受均匀零假设 */
        private boolean acceptUniform;
        private double maxAbsRelativeDeviation;
    }
}
