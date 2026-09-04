package com.lottery.schedule;

import com.lottery.config.properties.LotteryCollectProperties;
import com.lottery.service.CollectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时采集任务。周期由 lottery.collect.cron 配置。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lottery.collect", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LotteryCollectScheduler {

    private final CollectService collectService;
    private final LotteryCollectProperties collectProperties;

    @Scheduled(cron = "${lottery.collect.cron:0 35 21 * * ?}")
    public void scheduledCollect() {
        if (!collectProperties.isEnabled()) {
            return;
        }
        log.info("定时采集任务启动 cron={}", collectProperties.getCron());
        String batchNo = collectService.collectAll();
        log.info("定时采集任务结束 batchNo={}", batchNo);
    }
}
