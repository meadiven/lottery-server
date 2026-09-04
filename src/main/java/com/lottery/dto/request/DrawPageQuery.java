package com.lottery.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DrawPageQuery {

    /** 彩种编码，如 SSQ */
    @NotBlank(message = "彩种编码不能为空")
    private String lotteryCode;

    @Min(value = 1, message = "页码最小为1")
    private long pageNum = 1;

    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 100, message = "每页条数最大为100")
    private long pageSize = 10;
}
