package com.lottery.service.analysis;

import com.lottery.dto.response.FrequencyStatVO;
import com.lottery.dto.response.RandomSetsVO;

/**
 * 统计分析扩展服务。
 * 仅描述性统计与随机抽样模拟，不提供荐号/预测结论。
 */
public interface LotteryAnalysisService {

    /**
     * 基于历史开奖数据做号码出现频次统计。
     * recentIssues &lt;= 0 时统计全部已入库期数。
     */
    FrequencyStatVO frequencyStats(String lotteryCode, int recentIssues);

    /**
     * 按历史出现频次做加权随机抽样（教学对比用，非官方开奖模型）。
     */
    RandomSetsVO randomSets(String lotteryCode, int count, int recentIssues);

    /**
     * 均匀独立无放回抽样，贴合公平开奖零假设（各合法号码等权）。
     */
    RandomSetsVO uniformRandomSets(String lotteryCode, int count);

    /**
     * 仅使用 issueNo 严格小于 beforeIssueNo 的历史做频次加权抽样（walk-forward，防泄漏）。
     */
    RandomSetsVO randomSetsBeforeIssue(String lotteryCode, int count, String beforeIssueNo);
}
