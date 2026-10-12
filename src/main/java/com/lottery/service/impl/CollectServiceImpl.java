package com.lottery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lottery.collector.LotteryCollector;
import com.lottery.collector.rate.CollectRateLimiter;
import com.lottery.common.enums.LotteryCodeEnum;
import com.lottery.config.properties.LotteryCollectProperties;
import com.lottery.dto.model.DrawParseResult;
import com.lottery.entity.CollectLog;
import com.lottery.entity.LotteryDraw;
import com.lottery.entity.LotteryPrizeDetail;
import com.lottery.entity.LotteryType;
import com.lottery.mapper.CollectLogMapper;
import com.lottery.mapper.LotteryDrawMapper;
import com.lottery.mapper.LotteryPrizeDetailMapper;
import com.lottery.mapper.LotteryTypeMapper;
import com.lottery.parser.DrawParseHelper;
import com.lottery.service.CollectService;
import com.lottery.service.simulation.LotterySimulationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class CollectServiceImpl implements CollectService {

    private final List<LotteryCollector> collectors;
    private final CollectRateLimiter rateLimiter;
    private final LotteryCollectProperties collectProperties;
    private final LotteryTypeMapper lotteryTypeMapper;
    private final LotteryDrawMapper lotteryDrawMapper;
    private final LotteryPrizeDetailMapper prizeDetailMapper;
    private final CollectLogMapper collectLogMapper;
    private final LotterySimulationService simulationService;
    private final Executor historyCollectExecutor;

    public CollectServiceImpl(List<LotteryCollector> collectors,
                              CollectRateLimiter rateLimiter,
                              LotteryCollectProperties collectProperties,
                              LotteryTypeMapper lotteryTypeMapper,
                              LotteryDrawMapper lotteryDrawMapper,
                              LotteryPrizeDetailMapper prizeDetailMapper,
                              CollectLogMapper collectLogMapper,
                              LotterySimulationService simulationService,
                              @Qualifier("historyCollectExecutor") Executor historyCollectExecutor) {
        this.collectors = collectors;
        this.rateLimiter = rateLimiter;
        this.collectProperties = collectProperties;
        this.lotteryTypeMapper = lotteryTypeMapper;
        this.lotteryDrawMapper = lotteryDrawMapper;
        this.prizeDetailMapper = prizeDetailMapper;
        this.collectLogMapper = collectLogMapper;
        this.simulationService = simulationService;
        this.historyCollectExecutor = historyCollectExecutor;
    }

    @Override
    public String collectAll() {
        String batchNo = UUID.randomUUID().toString().replace("-", "");
        if (!rateLimiter.tryLock(batchNo)) {
            log.warn("采集任务跳过：已有实例在执行");
            saveLog(null, batchNo, "SKIP", "分布式锁占用，跳过本轮", null, null);
            return batchNo;
        }

        long start = System.currentTimeMillis();
        try {
            for (LotteryType type : enabledTypes()) {
                try {
                    doCollectLatest(LotteryCodeEnum.of(type.getCode()), batchNo);
                } catch (Exception ex) {
                    log.error("彩种采集失败 code={} batch={}", type.getCode(), batchNo, ex);
                    saveLog(type.getCode(), batchNo, "FAIL", ex.getMessage(), null, null);
                }
            }
            saveLog(null, batchNo, "SUCCESS",
                    "整轮最新采集完成, costMs=" + (System.currentTimeMillis() - start),
                    null, System.currentTimeMillis() - start);
        } finally {
            rateLimiter.unlock(batchNo);
        }
        return batchNo;
    }

    @Override
    public String collectOne(LotteryCodeEnum lotteryCode) {
        String batchNo = UUID.randomUUID().toString().replace("-", "");
        if (!rateLimiter.tryLock(batchNo)) {
            saveLog(lotteryCode.getCode(), batchNo, "SKIP", "分布式锁占用，跳过", null, null);
            return batchNo;
        }
        try {
            doCollectLatest(lotteryCode, batchNo);
        } finally {
            rateLimiter.unlock(batchNo);
        }
        return batchNo;
    }

    @Override
    public String collectHistoryAsync(String lotteryCode) {
        String batchNo = UUID.randomUUID().toString().replace("-", "");
        long ttl = collectProperties.getHistoryLockTtlSeconds();
        if (!rateLimiter.tryLock(batchNo, ttl)) {
            saveLog(lotteryCode, batchNo, "SKIP", "已有采集任务在执行，请稍后再试", null, null);
            return batchNo;
        }

        saveLog(lotteryCode, batchNo, "SUCCESS", "历史全量采集任务已提交（后台执行）", null, null);
        historyCollectExecutor.execute(() -> {
            long start = System.currentTimeMillis();
            try {
                if (lotteryCode == null || lotteryCode.isBlank()) {
                    for (LotteryType type : enabledTypes()) {
                        try {
                            doCollectHistory(LotteryCodeEnum.of(type.getCode()), batchNo);
                            rateLimiter.renewLock(batchNo, ttl);
                        } catch (Exception ex) {
                            log.error("历史采集失败 code={} batch={}", type.getCode(), batchNo, ex);
                            saveLog(type.getCode(), batchNo, "FAIL", ex.getMessage(), null, null);
                        }
                    }
                } else {
                    doCollectHistory(LotteryCodeEnum.of(lotteryCode.toUpperCase()), batchNo);
                }
                saveLog(lotteryCode, batchNo, "SUCCESS",
                        "历史全量采集完成, costMs=" + (System.currentTimeMillis() - start),
                        null, System.currentTimeMillis() - start);
            } catch (Exception ex) {
                log.error("历史采集任务异常 batch={}", batchNo, ex);
                saveLog(lotteryCode, batchNo, "FAIL", ex.getMessage(), null, null);
            } finally {
                rateLimiter.unlock(batchNo);
            }
        });
        return batchNo;
    }

    private void doCollectLatest(LotteryCodeEnum lotteryCode, String batchNo) {
        long start = System.currentTimeMillis();
        LotteryCollector collector = requireCollector(lotteryCode);
        try {
            List<DrawParseResult> results = collector.collectLatest(lotteryCode);
            if (results.isEmpty()) {
                saveLog(lotteryCode.getCode(), batchNo, "SKIP", "未解析到开奖数据", null,
                        System.currentTimeMillis() - start);
                return;
            }
            int saved = persistAll(results);
            saveLog(lotteryCode.getCode(), batchNo, "SUCCESS",
                    "最新采集：解析" + results.size() + "条，新增" + saved + "条",
                    200, System.currentTimeMillis() - start);
        } catch (Exception ex) {
            log.error("采集异常 lottery={} batch={}", lotteryCode, batchNo, ex);
            saveLog(lotteryCode.getCode(), batchNo, "FAIL", ex.getMessage(), null,
                    System.currentTimeMillis() - start);
            throw ex;
        }
    }

    private void doCollectHistory(LotteryCodeEnum lotteryCode, String batchNo) {
        long start = System.currentTimeMillis();
        LotteryCollector collector = requireCollector(lotteryCode);
        int pageSize = Math.max(1, collectProperties.getHistoryPageSize());
        int[] counters = new int[]{0, 0}; // parsed, saved

        log.info("开始历史全量采集 lottery={} pageSize={}", lotteryCode, pageSize);
        collector.collectFullHistory(lotteryCode, pageSize, records -> {
            counters[0] += records.size();
            counters[1] += persistAll(records);
            rateLimiter.renewLock(batchNo, collectProperties.getHistoryLockTtlSeconds());
        });

        saveLog(lotteryCode.getCode(), batchNo, "SUCCESS",
                "历史采集完成：解析" + counters[0] + "条，新增" + counters[1] + "条",
                200, System.currentTimeMillis() - start);
    }

    private int persistAll(List<DrawParseResult> results) {
        int saved = 0;
        for (DrawParseResult result : results) {
            if (persistIfAbsent(result)) {
                saved++;
            }
        }
        return saved;
    }

    private LotteryCollector requireCollector(LotteryCodeEnum lotteryCode) {
        return collectors.stream()
                .filter(c -> c.supports(lotteryCode))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到采集器: " + lotteryCode));
    }

    private List<LotteryType> enabledTypes() {
        return lotteryTypeMapper.selectList(new LambdaQueryWrapper<LotteryType>()
                .eq(LotteryType::getEnabled, 1)
                .orderByAsc(LotteryType::getSortOrder));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean persistIfAbsent(DrawParseResult result) {
        if (!rateLimiter.markIfAbsent(result.getLotteryCode(), result.getIssueNo())) {
            return false;
        }

        Long exists = lotteryDrawMapper.selectCount(new LambdaQueryWrapper<LotteryDraw>()
                .eq(LotteryDraw::getLotteryCode, result.getLotteryCode())
                .eq(LotteryDraw::getIssueNo, result.getIssueNo()));
        if (exists != null && exists > 0) {
            return false;
        }

        LotteryDraw draw = new LotteryDraw();
        draw.setLotteryCode(result.getLotteryCode());
        draw.setIssueNo(result.getIssueNo());
        draw.setDrawTime(result.getDrawTime());
        draw.setDrawDate(result.getDrawDate());
        draw.setRedBalls(result.getRedBalls());
        draw.setBlueBalls(result.getBlueBalls());
        draw.setDrawNumbers(result.getDrawNumbers());
        draw.setSalesAmount(result.getSalesAmount());
        draw.setPoolAmount(result.getPoolAmount());
        draw.setRawPayload(result.getRawPayload());
        draw.setSource(result.getSource());
        draw.setContentHash(DrawParseHelper.contentHash(result));

        try {
            lotteryDrawMapper.insert(draw);
        } catch (DuplicateKeyException ex) {
            rateLimiter.clearDedupMark(result.getLotteryCode(), result.getIssueNo());
            return false;
        }

        if (result.getPrizes() != null) {
            for (DrawParseResult.PrizeItem prize : result.getPrizes()) {
                LotteryPrizeDetail detail = new LotteryPrizeDetail();
                detail.setDrawId(draw.getId());
                detail.setLotteryCode(result.getLotteryCode());
                detail.setIssueNo(result.getIssueNo());
                detail.setPrizeLevel(prize.getPrizeLevel());
                detail.setPrizeLevelNo(prize.getPrizeLevelNo());
                detail.setWinnerCount(prize.getWinnerCount());
                detail.setPrizeAmount(prize.getPrizeAmount());
                detail.setTotalAmount(prize.getTotalAmount());
                prizeDetailMapper.insert(detail);
            }
        }
        log.info("入库成功 {} 期号 {} 号码 {}",
                result.getLotteryCode(), result.getIssueNo(), result.getDrawNumbers());
        // 大乐透实开入库后，对照已有模拟批次做评估（失败不影响采集）
        if (LotteryCodeEnum.DLT.getCode().equalsIgnoreCase(result.getLotteryCode())) {
            try {
                simulationService.evaluateIssue(result.getLotteryCode(), result.getIssueNo());
            } catch (Exception ex) {
                log.warn("模拟对照评估跳过/失败 code={} issue={}: {}",
                        result.getLotteryCode(), result.getIssueNo(), ex.getMessage());
            }
        }
        return true;
    }

    private void saveLog(String lotteryCode, String batchNo, String status,
                         String message, Integer httpStatus, Long costMs) {
        CollectLog logEntity = new CollectLog();
        logEntity.setLotteryCode(lotteryCode);
        logEntity.setTaskBatchNo(batchNo);
        logEntity.setStatus(status);
        if (message != null && message.length() > 1000) {
            message = message.substring(0, 1000);
        }
        logEntity.setMessage(message);
        logEntity.setHttpStatus(httpStatus);
        logEntity.setCostMs(costMs);
        collectLogMapper.insert(logEntity);
    }
}
