package com.lottery.collector.http;

import com.lottery.config.properties.LotteryCollectProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * 带重试与基础请求头的 HTTP 客户端。
 * 注意：调用方必须配合请求间隔，禁止高频请求。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectHttpClient {

    private final RestTemplate restTemplate;
    private final LotteryCollectProperties collectProperties;

    public String get(String url, Map<String, ?> queryParams) {
        URI uri = buildUri(url, queryParams);
        int maxRetry = Math.max(1, collectProperties.getMaxRetry());
        RestClientException last = null;

        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                HttpHeaders headers = defaultHeaders(url);
                ResponseEntity<String> response = restTemplate.exchange(
                        uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    throw new RestClientException("HTTP状态异常: " + response.getStatusCode());
                }
                return response.getBody();
            } catch (RestClientException ex) {
                last = ex;
                log.warn("采集请求失败 attempt={}/{} url={} err={}",
                        attempt, maxRetry, uri, ex.getMessage());
                if (attempt < maxRetry) {
                    sleepQuietly(collectProperties.getRetryBackoffMs() * attempt);
                }
            }
        }
        throw new RestClientException("采集请求重试耗尽: " + uri, last);
    }

    private URI buildUri(String url, Map<String, ?> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        if (queryParams != null) {
            queryParams.forEach((k, v) -> {
                if (v != null) {
                    builder.queryParam(k, v);
                }
            });
        }
        return builder.encode().build().toUri();
    }

    private HttpHeaders defaultHeaders() {
        return defaultHeaders(null);
    }

    private HttpHeaders defaultHeaders(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9");
        if (url != null && url.contains("cwl.gov.cn")) {
            headers.set(HttpHeaders.REFERER, "https://www.cwl.gov.cn/");
        } else if (url != null && url.contains("sporttery.cn")) {
            headers.set(HttpHeaders.REFERER, "https://www.lottery.gov.cn/");
        }
        return headers;
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
