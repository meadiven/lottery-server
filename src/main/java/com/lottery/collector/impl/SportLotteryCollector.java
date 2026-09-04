package com.lottery.collector.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lottery.collector.LotteryCollector;
import com.lottery.collector.http.CollectHttpClient;
import com.lottery.collector.rate.CollectRateLimiter;
import com.lottery.common.enums.LotteryCodeEnum;
import com.lottery.config.properties.LotteryDataSourceProperties;
import com.lottery.dto.model.DrawParseResult;
import com.lottery.parser.DrawParseHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 体彩公开接口采集器（大乐透 / 排列3 / 排列5）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SportLotteryCollector implements LotteryCollector {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_ONLY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CollectHttpClient httpClient;
    private final CollectRateLimiter rateLimiter;
    private final LotteryDataSourceProperties dataSourceProperties;

    @Override
    public boolean supports(LotteryCodeEnum lotteryCode) {
        return !lotteryCode.isWelfare();
    }

    @Override
    public List<DrawParseResult> collectLatest(LotteryCodeEnum lotteryCode) {
        rateLimiter.awaitRequestInterval();
        Map<String, Object> params = baseParams(lotteryCode, 1, 1);
        String body = httpClient.get(dataSourceProperties.getSportBaseUrl(), params);
        return parseRecords(lotteryCode, body);
    }

    @Override
    public void collectFullHistory(LotteryCodeEnum lotteryCode, int pageSize, Consumer<List<DrawParseResult>> batchConsumer) {
        int size = Math.max(1, Math.min(pageSize, 100));
        int pageNo = 1;
        while (true) {
            rateLimiter.awaitRequestInterval();
            Map<String, Object> params = baseParams(lotteryCode, pageNo, size);
            String body = httpClient.get(dataSourceProperties.getSportBaseUrl(), params);
            List<DrawParseResult> records = parseRecords(lotteryCode, body);
            if (records.isEmpty()) {
                break;
            }
            batchConsumer.accept(records);

            JSONObject value = JSONUtil.parseObj(body).getJSONObject("value");
            int total = value == null ? -1 : value.getInt("total", -1);
            int pages = value == null ? -1 : value.getInt("pages", -1);
            log.info("体彩历史进度 lottery={} page={}/{} size={} total={}",
                    lotteryCode.getCode(), pageNo, pages > 0 ? pages : "?", records.size(), total);

            boolean hasMore;
            if (pages > 0) {
                hasMore = pageNo < pages;
            } else if (total > 0) {
                hasMore = (long) pageNo * size < total;
            } else {
                hasMore = records.size() >= size;
            }
            if (!hasMore) {
                break;
            }
            pageNo++;
        }
    }

    private Map<String, Object> baseParams(LotteryCodeEnum lotteryCode, int pageNo, int pageSize) {
        Map<String, Object> params = new HashMap<>();
        params.put("gameNo", lotteryCode.getSourceKey());
        params.put("provinceId", "0");
        params.put("pageSize", pageSize);
        params.put("isVerify", 1);
        params.put("pageNo", pageNo);
        return params;
    }

    private List<DrawParseResult> parseRecords(LotteryCodeEnum lotteryCode, String body) {
        List<DrawParseResult> results = new ArrayList<>();
        if (StrUtil.isBlank(body) || !JSONUtil.isTypeJSON(body)) {
            log.warn("体彩返回非JSON: {}", StrUtil.sub(body, 0, 200));
            return results;
        }
        JSONObject value = JSONUtil.parseObj(body).getJSONObject("value");
        if (value == null) {
            return results;
        }
        JSONArray list = value.getJSONArray("list");
        if (list == null || list.isEmpty()) {
            return results;
        }
        for (int i = 0; i < list.size(); i++) {
            DrawParseResult parsed = mapItem(lotteryCode, list.getJSONObject(i));
            if (parsed != null) {
                results.add(parsed);
            }
        }
        return results;
    }

    private DrawParseResult mapItem(LotteryCodeEnum lotteryCode, JSONObject item) {
        String issueNo = item.getStr("lotteryDrawNum");
        if (StrUtil.isBlank(issueNo)) {
            return null;
        }

        String drawResult = item.getStr("lotteryDrawResult");
        String red;
        String blue;
        if (lotteryCode == LotteryCodeEnum.DLT) {
            String[] parts = StrUtil.blankToDefault(drawResult, "").trim().split("\\s+");
            List<String> front = new ArrayList<>();
            List<String> back = new ArrayList<>();
            for (int i = 0; i < parts.length; i++) {
                if (i < 5) {
                    front.add(parts[i]);
                } else {
                    back.add(parts[i]);
                }
            }
            red = DrawParseHelper.normalizeBalls(String.join(" ", front));
            blue = DrawParseHelper.normalizeBalls(String.join(" ", back));
        } else {
            red = DrawParseHelper.normalizeBalls(drawResult);
            blue = null;
        }

        LocalDateTime drawTime = parseDateTime(item.getStr("lotteryDrawTime"));
        LocalDate drawDate = drawTime != null ? drawTime.toLocalDate() : null;

        DrawParseResult.DrawParseResultBuilder builder = DrawParseResult.builder()
                .lotteryCode(lotteryCode.getCode())
                .issueNo(issueNo)
                .drawTime(drawTime)
                .drawDate(drawDate)
                .redBalls(red)
                .blueBalls(blue)
                .drawNumbers(DrawParseHelper.buildDrawNumbers(red, blue))
                .salesAmount(DrawParseHelper.toDecimal(item.get("totalSaleAmount")))
                .poolAmount(DrawParseHelper.toDecimal(item.get("poolBalanceAfterdraw")))
                .rawPayload(item.toString())
                .source("SPORT_OFFICIAL");

        List<DrawParseResult.PrizeItem> prizes = new ArrayList<>();
        JSONArray prizeLevelList = item.getJSONArray("prizeLevelList");
        if (prizeLevelList != null) {
            for (int i = 0; i < prizeLevelList.size(); i++) {
                JSONObject p = prizeLevelList.getJSONObject(i);
                String level = p.getStr("prizeLevel");
                if (StrUtil.isBlank(level)) {
                    continue;
                }
                prizes.add(DrawParseResult.PrizeItem.builder()
                        .prizeLevel(level)
                        .prizeLevelNo(i + 1)
                        .winnerCount(DrawParseHelper.toInt(p.get("stakeCount")))
                        .prizeAmount(DrawParseHelper.toDecimal(p.get("stakeAmount")))
                        .totalAmount(DrawParseHelper.toDecimal(p.get("totalPrizeAmount")))
                        .build());
            }
        }
        builder.prizes(prizes);
        return builder.build();
    }

    private LocalDateTime parseDateTime(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        String t = text.trim();
        try {
            if (t.length() >= 19) {
                return LocalDateTime.parse(t.substring(0, 19), DATE_TIME);
            }
            if (t.length() >= 10) {
                return LocalDate.parse(t.substring(0, 10), DATE_ONLY).atStartOfDay();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }
}
