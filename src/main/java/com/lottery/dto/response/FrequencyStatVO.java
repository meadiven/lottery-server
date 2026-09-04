package com.lottery.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 号码频次统计结果（描述性统计）。
 */
@Data
@Builder
public class FrequencyStatVO {

    private String lotteryCode;
    private int sampleIssues;
    /** 红球/前区号码 -> 出现次数 */
    private Map<String, Long> redFrequency;
    /** 蓝球/后区号码 -> 出现次数 */
    private Map<String, Long> blueFrequency;
    /** 便于前端画图的有序列表 */
    private List<BallCount> redRank;
    private List<BallCount> blueRank;

    @Data
    @Builder
    public static class BallCount {
        private String ball;
        private long count;
    }
}
