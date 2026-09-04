package com.lottery.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PageResult<T> {

    private long pageNum;
    private long pageSize;
    private long total;
    private List<T> records;
}
