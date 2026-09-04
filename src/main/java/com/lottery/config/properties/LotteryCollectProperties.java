package com.lottery.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lottery.collect")
public class LotteryCollectProperties {

    /** 是否启用定时采集 */
    private boolean enabled = true;

    /** 采集 cron */
    private String cron = "0 35 21 * * ?";

    /** 彩种间请求间隔（毫秒） */
    private long requestIntervalMs = 3000L;

    private int maxRetry = 3;

    private long retryBackoffMs = 2000L;

    private int connectTimeoutMs = 5000;

    private int readTimeoutMs = 10000;

    private long lockTtlSeconds = 600L;

    /** 历史全量采集锁 TTL（秒），任务较长 */
    private long historyLockTtlSeconds = 7200L;

    private int dedupTtlDays = 30;

    /** 历史分页每页条数 */
    private int historyPageSize = 30;

    /** 历史最大页数，0 表示不限制（拉到数据源无更多为止） */
    private int historyMaxPages = 0;
}
