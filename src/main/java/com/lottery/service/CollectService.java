package com.lottery.service;

import com.lottery.common.enums.LotteryCodeEnum;
import com.lottery.dto.model.DrawParseResult;

/**
 * 采集编排服务。
 */
public interface CollectService {

    /** 采集全部已启用彩种最近一期公开数据 */
    String collectAll();

    /** 采集指定彩种最近一期 */
    String collectOne(LotteryCodeEnum lotteryCode);

    /**
     * 异步拉取历史全量（或指定彩种历史）。
     * @param lotteryCode 为空则全部启用彩种
     * @return 任务批次号
     */
    String collectHistoryAsync(String lotteryCode);

    /** 持久化解析结果（含去重） */
    boolean persistIfAbsent(DrawParseResult result);
}
