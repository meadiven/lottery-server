package com.lottery.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lottery.datasource")
public class LotteryDataSourceProperties {

    /** 福彩公开查询接口 */
    private String cwlBaseUrl =
            "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice";

    /** 体彩历史开奖公开接口 */
    private String sportBaseUrl =
            "https://webapi.sporttery.cn/gateway/lottery/getHistoryPageListV1.qry";
}
