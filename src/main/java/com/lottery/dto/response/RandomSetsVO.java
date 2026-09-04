package com.lottery.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 基于历史频次的随机抽样模拟结果（仅供开奖后对比学习）。
 */
@Data
@Builder
public class RandomSetsVO {

    private String lotteryCode;
    private String lotteryName;
    /** 参与抽样的历史期数 */
    private int sampleIssues;
    private int setCount;
    private String method;
    private String disclaimer;
    private List<RandomSetItem> sets;

    @Data
    @Builder
    public static class RandomSetItem {
        private int index;
        private String redBalls;
        private String blueBalls;
        private String drawNumbers;
    }
}
