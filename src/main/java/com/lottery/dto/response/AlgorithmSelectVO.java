package com.lottery.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 候选算法滚动回测择优结果。
 */
@Data
@Builder
public class AlgorithmSelectVO {

    private String lotteryCode;
    /** 胜出算法版本 */
    private String selectedVersion;
    private int lookbackIssues;
    private String scoreRule;
    private String disclaimer;
    private List<AlgoScore> candidates;

    @Data
    @Builder
    public static class AlgoScore {
        private String algorithmVersion;
        private int sampleCount;
        private double averageBatchScore;
        private boolean selected;
    }
}
