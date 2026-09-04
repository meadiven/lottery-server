package com.lottery.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DrawDetailVO {

    private Long id;
    private String lotteryCode;
    private String lotteryName;
    private String issueNo;
    private LocalDateTime drawTime;
    private LocalDate drawDate;
    private String redBalls;
    private String blueBalls;
    private String drawNumbers;
    private BigDecimal salesAmount;
    private BigDecimal poolAmount;
    private String source;
    private List<PrizeDetailVO> prizes;

    @Data
    @Builder
    public static class PrizeDetailVO {
        private String prizeLevel;
        private Integer prizeLevelNo;
        private Integer winnerCount;
        private BigDecimal prizeAmount;
        private BigDecimal totalAmount;
    }
}
