package com.lottery.controller;

import com.lottery.common.enums.LotteryCodeEnum;
import com.lottery.common.result.Result;
import com.lottery.dto.request.DrawPageQuery;
import com.lottery.dto.response.DrawDetailVO;
import com.lottery.dto.response.PageResult;
import com.lottery.entity.LotteryType;
import com.lottery.service.CollectService;
import com.lottery.service.LotteryDrawQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 开奖数据查询与采集管理接口。
 * 仅提供历史公示数据查询与低频采集触发，不含任何购彩相关能力。
 */
@Validated
@RestController
@RequestMapping("/lottery")
@RequiredArgsConstructor
public class LotteryController {

    private final LotteryDrawQueryService drawQueryService;
    private final CollectService collectService;

    /** 彩种列表 */
    @GetMapping("/types")
    public Result<List<LotteryType>> listTypes() {
        return Result.ok(drawQueryService.listTypes());
    }

    /** 按彩种分页查询历史开奖 */
    @GetMapping("/draws")
    public Result<PageResult<DrawDetailVO>> pageDraws(@Valid DrawPageQuery query) {
        query.setLotteryCode(query.getLotteryCode().toUpperCase());
        return Result.ok(drawQueryService.pageByLottery(query));
    }

    /** 按期号查询单期开奖（含奖项明细） */
    @GetMapping("/draws/{lotteryCode}/{issueNo}")
    public Result<DrawDetailVO> getByIssue(
            @PathVariable @NotBlank String lotteryCode,
            @PathVariable @NotBlank String issueNo) {
        return Result.ok(drawQueryService.getByIssue(lotteryCode.toUpperCase(), issueNo));
    }

    /** 查询某彩种最新一期 */
    @GetMapping("/draws/{lotteryCode}/latest")
    public Result<DrawDetailVO> getLatest(@PathVariable @NotBlank String lotteryCode) {
        return Result.ok(drawQueryService.getLatest(lotteryCode.toUpperCase()));
    }

    /**
     * 手动触发采集最近一期（运维/调试用）。
     * 仍受 Redis 锁与请求间隔约束，禁止高频调用。
     */
    @PostMapping("/collect/trigger")
    public Result<Map<String, String>> triggerCollect(
            @RequestParam(required = false) String lotteryCode) {
        String batchNo;
        if (lotteryCode == null || lotteryCode.isBlank()) {
            batchNo = collectService.collectAll();
        } else {
            batchNo = collectService.collectOne(LotteryCodeEnum.of(lotteryCode.toUpperCase()));
        }
        Map<String, String> data = new HashMap<>();
        data.put("batchNo", batchNo);
        data.put("mode", "latest");
        return Result.ok(data);
    }

    /**
     * 异步拉取历史全量公开开奖数据（分页+限流）。
     * 立即返回批次号，任务在后台执行，耗时可能较长。
     */
    @PostMapping("/collect/history")
    public Result<Map<String, String>> triggerHistoryCollect(
            @RequestParam(required = false) String lotteryCode) {
        String code = (lotteryCode == null || lotteryCode.isBlank())
                ? null : lotteryCode.toUpperCase();
        String batchNo = collectService.collectHistoryAsync(code);
        Map<String, String> data = new HashMap<>();
        data.put("batchNo", batchNo);
        data.put("mode", "history");
        data.put("message", "历史全量采集已在后台启动，请稍后在首页/历史页查看数据");
        return Result.ok(data);
    }
}
