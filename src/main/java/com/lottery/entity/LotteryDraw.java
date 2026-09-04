package com.lottery.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("lottery_draw")
public class LotteryDraw {

    @TableId(type = IdType.AUTO)
    private Long id;

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
    private String contentHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
