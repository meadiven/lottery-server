package com.lottery.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("lottery_prize_detail")
public class LotteryPrizeDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long drawId;
    private String lotteryCode;
    private String issueNo;
    private String prizeLevel;
    private Integer prizeLevelNo;
    private Integer winnerCount;
    private BigDecimal prizeAmount;
    private BigDecimal totalAmount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
