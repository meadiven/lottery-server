package com.lottery.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("collect_log")
public class CollectLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String lotteryCode;
    private String taskBatchNo;
    private String status;
    private String message;
    private Integer httpStatus;
    private Long costMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
