package com.lottery.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 单期模拟 vs 实开对照评估。
 */
@Data
@Builder
public class SimulationEvaluateVO {

    private String lotteryCode;
    private String issueNo;
    private String algorithmVersion;
    private String actualRed;
    private String actualBlue;
    private int fixedFrontHits;
    private int fixedBackHits;
    private double batchScore;
    private List<SetScore> sets;

    @Data
    @Builder
    public static class SetScore {
        private int index;
        private String redBalls;
        private String blueBalls;
        private int frontHits;
        private int backHits;
        private boolean fixed;
    }
}
