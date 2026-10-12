package com.lottery.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "lottery.simulation")
public class LotterySimulationProperties {

    /** 是否启用开奖日凌晨自动生成 */
    private boolean enabled = true;

    /**
     * 默认：周一/三/六 00:05（大乐透开奖日凌晨，Asia/Shanghai）。
     * Spring cron：秒 分 时 日 月 周
     */
    private String cron = "0 5 0 ? * MON,WED,SAT";

    /** 择优回看期数 */
    private int lookbackIssues = 12;

    /**
     * 候选算法版本（平局优先列表靠前：均匀）。
     */
    private List<String> candidates = new ArrayList<>(List.of(
            "v2-uniform-iid",
            "v3-freq-weighted-all"
    ));
}
