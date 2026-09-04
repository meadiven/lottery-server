package com.lottery.collector.rate;

import com.lottery.config.properties.LotteryCollectProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 采集限流与分布式锁：防止多实例并发、禁止高频请求。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectRateLimiter {

    private static final String LOCK_KEY = "lottery:collect:lock";
    private static final String INTERVAL_KEY = "lottery:collect:last_request_at";
    private static final String DEDUP_KEY_PREFIX = "lottery:dedup:";

    private final StringRedisTemplate stringRedisTemplate;
    private final LotteryCollectProperties collectProperties;

    public boolean tryLock(String batchNo) {
        return tryLock(batchNo, collectProperties.getLockTtlSeconds());
    }

    public boolean tryLock(String batchNo, long ttlSeconds) {
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(
                LOCK_KEY, batchNo, Duration.ofSeconds(Math.max(60, ttlSeconds)));
        return Boolean.TRUE.equals(ok);
    }

    /** 历史长任务进行中续期，避免锁提前过期 */
    public void renewLock(String batchNo, long ttlSeconds) {
        String val = stringRedisTemplate.opsForValue().get(LOCK_KEY);
        if (batchNo.equals(val)) {
            stringRedisTemplate.expire(LOCK_KEY, Duration.ofSeconds(Math.max(60, ttlSeconds)));
        }
    }

    public void unlock(String batchNo) {
        String val = stringRedisTemplate.opsForValue().get(LOCK_KEY);
        if (batchNo.equals(val)) {
            stringRedisTemplate.delete(LOCK_KEY);
        }
    }

    /**
     * 保证两次对外请求之间至少间隔配置毫秒数。
     */
    public void awaitRequestInterval() {
        long interval = collectProperties.getRequestIntervalMs();
        String last = stringRedisTemplate.opsForValue().get(INTERVAL_KEY);
        if (last != null) {
            long elapsed = System.currentTimeMillis() - Long.parseLong(last);
            if (elapsed < interval) {
                long sleepMs = interval - elapsed;
                log.debug("请求间隔限流，等待 {} ms", sleepMs);
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        stringRedisTemplate.opsForValue().set(
                INTERVAL_KEY, String.valueOf(System.currentTimeMillis()), 1, TimeUnit.HOURS);
    }

    public boolean markIfAbsent(String lotteryCode, String issueNo) {
        String key = DEDUP_KEY_PREFIX + lotteryCode + ":" + issueNo;
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(
                key, "1", Duration.ofDays(collectProperties.getDedupTtlDays()));
        return Boolean.TRUE.equals(ok);
    }

    public void clearDedupMark(String lotteryCode, String issueNo) {
        stringRedisTemplate.delete(DEDUP_KEY_PREFIX + lotteryCode + ":" + issueNo);
    }
}
