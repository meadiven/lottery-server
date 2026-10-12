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
@TableName("lottery_simulation_eval")
public class LotterySimulationEval {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String lotteryCode;
    private String issueNo;
    private String algorithmVersion;
    private String setScoresJson;
    private BigDecimal batchScore;
    private Integer fixedFrontHits;
    private Integer fixedBackHits;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
