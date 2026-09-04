package com.lottery.collector;

import com.lottery.common.enums.LotteryCodeEnum;
import com.lottery.dto.model.DrawParseResult;

import java.util.List;
import java.util.function.Consumer;

/**
 * 数据源采集器抽象：按彩种类别扩展。
 */
public interface LotteryCollector {

    boolean supports(LotteryCodeEnum lotteryCode);

    /**
     * 拉取最近一期公开开奖数据。
     */
    List<DrawParseResult> collectLatest(LotteryCodeEnum lotteryCode);

    /**
     * 拉取该彩种全部可公开历史数据（内部自行分页/分年，遵守限流）。
     * 每拿到一批数据回调一次，便于边采边入库。
     */
    void collectFullHistory(LotteryCodeEnum lotteryCode, int pageSize, Consumer<List<DrawParseResult>> batchConsumer);
}
