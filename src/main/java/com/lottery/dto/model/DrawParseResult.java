package com.lottery.dto.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 采集解析后的统一开奖模型（跨彩种）。
 */
@Data
@Builder
public class DrawParseResult {

    private String lotteryCode;
    private String issueNo;
    private LocalDateTime drawTime;
    private LocalDate drawDate;
    private String redBalls;
    private String blueBalls;
    private String drawNumbers;
    private BigDecimal salesAmount;
    private BigDecimal poolAmount;
    private String rawPayload;
    private String source;

    @Builder.Default
    private List<PrizeItem> prizes = new ArrayList<>();

    @Data
    @Builder
    public static class PrizeItem {
        private String prizeLevel;
        private Integer prizeLevelNo;
        private Integer winnerCount;
        private BigDecimal prizeAmount;
        private BigDecimal totalAmount;
    }
}
