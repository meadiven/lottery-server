package com.lottery.service.simulation;

import com.lottery.dto.response.SimulationBatchVO;
import com.lottery.dto.response.SimulationVerifyVO;

/**
 * 开奖日凌晨自动模拟号码（落库），当前仅大乐透。
 */
public interface LotterySimulationService {

    /**
     * 开奖日凌晨：按「最新已开奖期号 + 1」生成本期模拟；同算法已存在则跳过。
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
     * 用大量蒙特卡洛抽样做卡方检验，验证均匀算法是否贴合公平开奖零假设。
     */
    SimulationVerifyVO verifyUniform(String lotteryCode, int trials);
}
