package com.lottery.analysis;

import com.lottery.common.result.Result;
import com.lottery.dto.response.AlgorithmSelectVO;
import com.lottery.dto.response.FrequencyStatVO;
import com.lottery.dto.response.RandomSetsVO;
import com.lottery.dto.response.SimulationBatchVO;
import com.lottery.dto.response.SimulationEvaluateVO;
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

import java.util.List;

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

    @GetMapping("/{lotteryCode}/frequency")
    public Result<FrequencyStatVO> frequency(
            @PathVariable @NotBlank String lotteryCode,
            @RequestParam(defaultValue = "0") int recentIssues) {
        return Result.ok(analysisService.frequencyStats(lotteryCode.toUpperCase(), recentIssues));
    }

    @GetMapping("/{lotteryCode}/random-sets")
    public Result<RandomSetsVO> randomSets(
            @PathVariable @NotBlank String lotteryCode,
            @RequestParam(defaultValue = "5") int count,
            @RequestParam(defaultValue = "0") int recentIssues) {
        return Result.ok(analysisService.randomSets(lotteryCode.toUpperCase(), count, recentIssues));
    }

    @GetMapping("/{lotteryCode}/uniform-sets")
    public Result<RandomSetsVO> uniformSets(
            @PathVariable @NotBlank String lotteryCode,
            @RequestParam(defaultValue = "5") int count) {
        return Result.ok(analysisService.uniformRandomSets(lotteryCode.toUpperCase(), count));
    }

    @GetMapping("/{lotteryCode}/simulations/latest")
    public Result<SimulationBatchVO> latestSimulation(@PathVariable @NotBlank String lotteryCode) {
        return Result.ok(simulationService.latest(lotteryCode.toUpperCase()));
    }

    @PostMapping("/{lotteryCode}/simulations/generate")
    public Result<SimulationBatchVO> generateSimulation(@PathVariable @NotBlank String lotteryCode) {
        return Result.ok(simulationService.generateForDrawDayMorning(lotteryCode.toUpperCase()));
    }

    @GetMapping("/{lotteryCode}/simulations/verify")
    public Result<SimulationVerifyVO> verifySimulation(
            @PathVariable @NotBlank String lotteryCode,
            @RequestParam(defaultValue = "10000") int trials) {
        return Result.ok(simulationService.verifyUniform(lotteryCode.toUpperCase(), trials));
    }

    @GetMapping("/{lotteryCode}/simulations/evaluate/history")
    public Result<List<SimulationEvaluateVO>> evaluateHistory(
            @PathVariable @NotBlank String lotteryCode,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(simulationService.evaluateHistory(lotteryCode.toUpperCase(), limit));
    }

    @GetMapping("/{lotteryCode}/simulations/select")
    public Result<AlgorithmSelectVO> selectAlgorithm(@PathVariable @NotBlank String lotteryCode) {
        return Result.ok(simulationService.selectAlgorithm(lotteryCode.toUpperCase()));
    }

    @GetMapping("/{lotteryCode}/simulations/{issueNo}/evaluate")
    public Result<SimulationEvaluateVO> evaluateIssue(
            @PathVariable @NotBlank String lotteryCode,
            @PathVariable @NotBlank String issueNo) {
        return Result.ok(simulationService.evaluateIssue(lotteryCode.toUpperCase(), issueNo));
    }
}
