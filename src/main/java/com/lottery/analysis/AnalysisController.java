package com.lottery.analysis;

import com.lottery.common.result.Result;
import com.lottery.dto.response.FrequencyStatVO;
import com.lottery.dto.response.RandomSetsVO;
import com.lottery.dto.response.SimulationBatchVO;
import com.lottery.dto.response.SimulationVerifyVO;
import com.lottery.service.analysis.LotteryAnalysisService;
import com.lottery.service.simulation.LotterySimulationService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计分析接口。
 * <p>
 * 仅做历史公示数据的描述性统计、随机抽样模拟与可视化数据输出，
 * 不提供购彩荐号或走势预测结论。
 */
@Validated
@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final LotteryAnalysisService analysisService;
    private final LotterySimulationService simulationService;

    /**
     * 号码出现频次统计（描述性统计，非预测）。
     */
    @GetMapping("/{lotteryCode}/frequency")
    public Result<FrequencyStatVO> frequency(
            @PathVariable @NotBlank String lotteryCode,
            @RequestParam(defaultValue = "0") int recentIssues) {
        return Result.ok(analysisService.frequencyStats(lotteryCode.toUpperCase(), recentIssues));
    }

    /**
     * 基于历史频次加权随机生成若干组模拟号码（即时，不落库；教学对比用）。
     */
    @GetMapping("/{lotteryCode}/random-sets")
    public Result<RandomSetsVO> randomSets(
            @PathVariable @NotBlank String lotteryCode,
            @RequestParam(defaultValue = "5") int count,
            @RequestParam(defaultValue = "0") int recentIssues) {
        return Result.ok(analysisService.randomSets(lotteryCode.toUpperCase(), count, recentIssues));
    }

    /**
     * 均匀独立无放回抽样（即时，不落库；贴合公平开奖零假设）。
     */
    @GetMapping("/{lotteryCode}/uniform-sets")
    public Result<RandomSetsVO> uniformSets(
            @PathVariable @NotBlank String lotteryCode,
            @RequestParam(defaultValue = "5") int count) {
        return Result.ok(analysisService.uniformRandomSets(lotteryCode.toUpperCase(), count));
    }

    /**
     * 开奖后自动生成的最新模拟批次（落库）。
     */
    @GetMapping("/{lotteryCode}/simulations/latest")
    public Result<SimulationBatchVO> latestSimulation(@PathVariable @NotBlank String lotteryCode) {
        return Result.ok(simulationService.latest(lotteryCode.toUpperCase()));
    }

    /**
     * 按开奖日凌晨逻辑补生成（最新已开奖期号 + 1）。
     */
    @PostMapping("/{lotteryCode}/simulations/generate")
    public Result<SimulationBatchVO> generateSimulation(@PathVariable @NotBlank String lotteryCode) {
        return Result.ok(simulationService.generateForDrawDayMorning(lotteryCode.toUpperCase()));
    }

    /**
     * 蒙特卡洛 + 卡方检验，验证均匀抽样是否贴合公平开奖零假设。
     */
    @GetMapping("/{lotteryCode}/simulations/verify")
    public Result<SimulationVerifyVO> verifySimulation(
            @PathVariable @NotBlank String lotteryCode,
            @RequestParam(defaultValue = "10000") int trials) {
        return Result.ok(simulationService.verifyUniform(lotteryCode.toUpperCase(), trials));
    }
}
