package com.lottery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 官方公开开奖数据采集服务。
 * <p>
 * 合规声明：仅采集官方对外公开的开奖公示数据，用于个人学习与统计研究；
 * 不提供购彩、投注、荐号等任何相关功能。
 */
@EnableScheduling
@SpringBootApplication
public class LotteryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LotteryServerApplication.class, args);
    }
}
