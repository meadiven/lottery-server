package com.lottery.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 开奖后自动生成的模拟批次（仅供学习对比）。
 */
@Data
@Builder
public class SimulationBatchVO {

    private Long id;
    private String lotteryCode;
    private String lotteryName;
    /** 触发生成的已开奖期号 */
    private String baseIssueNo;
    private int sampleIssues;
    private int setCount;
    private String algorithmVersion;
    private String method;
    private String disclaimer;
    private String fixedRed;
    private String fixedBlue;
    private List<RandomSetsVO.RandomSetItem> sets;
    private LocalDateTime createdAt;
}
