package com.lottery.service.simulation;

import com.lottery.dto.response.AlgorithmSelectVO;
import com.lottery.dto.response.SimulationBatchVO;
import com.lottery.dto.response.SimulationEvaluateVO;
import com.lottery.dto.response.SimulationVerifyVO;

import java.util.List;

/**
 * 开奖日凌晨自动模拟号码（落库）与实开对照择优，当前仅大乐透。
 */
public interface LotterySimulationService {

    /**
     * 开奖日凌晨：择优算法后按「最新已开奖期号 + 1」生成本期模拟。
     */
    SimulationBatchVO generateForDrawDayMorning(String lotteryCode);

    /**
     * 手动补生成（与凌晨任务相同逻辑）。
     */
    SimulationBatchVO generateForLatest(String lotteryCode);

    /**
     * 查询该彩种最新一批模拟结果。
     */
    SimulationBatchVO latest(String lotteryCode);

    /**
     * 用大量蒙特卡洛抽样做卡方检验，验证均匀抽样是否贴合公平开奖零假设。
     */
    SimulationVerifyVO verifyUniform(String lotteryCode, int trials);

    /**
     * 对照评估：落库模拟 vs 同号实开；结果写入 lottery_simulation_eval。
     */
    SimulationEvaluateVO evaluateIssue(String lotteryCode, String issueNo);

    /**
     * 批量评估最近有「模拟+实开」的期。
     */
    List<SimulationEvaluateVO> evaluateHistory(String lotteryCode, int limit);

    /**
     * 候选算法 walk-forward 回测择优。
     */
    AlgorithmSelectVO selectAlgorithm(String lotteryCode);
}
