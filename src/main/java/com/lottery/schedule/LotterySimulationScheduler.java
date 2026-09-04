package com.lottery.schedule;

import com.lottery.config.properties.LotterySimulationProperties;
import com.lottery.dto.response.SimulationBatchVO;
import com.lottery.service.simulation.LotterySimulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 大乐透开奖日凌晨生成本期模拟号码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lottery.simulation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LotterySimulationScheduler {

    private final LotterySimulationService simulationService;
    private final LotterySimulationProperties simulationProperties;

    @Scheduled(cron = "${lottery.simulation.cron:0 5 0 ? * MON,WED,SAT}", zone = "Asia/Shanghai")
    public void scheduledGenerateOnDrawMorning() {
        if (!simulationProperties.isEnabled()) {
            return;
        }
        log.info("开奖日凌晨模拟生成任务启动 cron={}", simulationProperties.getCron());
        try {
            SimulationBatchVO batch = simulationService.generateForDrawDayMorning("DLT");
            log.info("开奖日凌晨模拟生成完成 baseIssue={} algo={}",
                    batch.getBaseIssueNo(), batch.getAlgorithmVersion());
        } catch (Exception ex) {
            log.error("开奖日凌晨模拟生成失败", ex);
        }
    }
}
