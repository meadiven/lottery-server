package com.lottery.config;

import com.lottery.config.properties.LotteryCollectProperties;
import com.lottery.config.properties.LotteryDataSourceProperties;
import com.lottery.config.properties.LotterySimulationProperties;
import com.lottery.config.properties.WechatMiniappProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@EnableAsync
@EnableConfigurationProperties({
        LotteryCollectProperties.class,
        LotteryDataSourceProperties.class,
        WechatMiniappProperties.class,
        LotterySimulationProperties.class
})
@RequiredArgsConstructor
public class AppConfig {

    private final LotteryCollectProperties collectProperties;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(collectProperties.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(collectProperties.getReadTimeoutMs()))
                .build();
    }
}
