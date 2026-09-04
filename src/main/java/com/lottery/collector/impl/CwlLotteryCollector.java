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
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 福彩公开接口采集器（双色球 / 七乐彩 / 福彩3D）。
 * 历史全量按「年份窗口 + 页码」拉取（官方 pageNo 无日期条件时不可靠）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CwlLotteryCollector implements LotteryCollector {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_ONLY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CollectHttpClient httpClient;
    private final CollectRateLimiter rateLimiter;
    private final LotteryDataSourceProperties dataSourceProperties;

    @Override
    public boolean supports(LotteryCodeEnum lotteryCode) {
        return lotteryCode.isWelfare();
    }

    @Override
    public List<DrawParseResult> collectLatest(LotteryCodeEnum lotteryCode) {
        rateLimiter.awaitRequestInterval();
        Map<String, Object> params = baseParams(lotteryCode);
        params.put("issueCount", 1);
        params.put("pageNo", 1);
        params.put("pageSize", 1);
        String body = httpClient.get(dataSourceProperties.getCwlBaseUrl(), params);
        return parseRecords(lotteryCode, body);
    }

    @Override
    public void collectFullHistory(LotteryCodeEnum lotteryCode, int pageSize, Consumer<List<DrawParseResult>> batchConsumer) {
        int size = Math.max(1, Math.min(pageSize, 100));
        int startYear = historyStartYear(lotteryCode);
        int endYear = Year.now().getValue();

        for (int year = startYear; year <= endYear; year++) {
            String dayStart = year + "-01-01";
            String dayEnd = year + "-12-31";
            int pageNo = 1;
            while (true) {
                rateLimiter.awaitRequestInterval();
                Map<String, Object> params = baseParams(lotteryCode);
                params.put("issueCount", "");
                params.put("dayStart", dayStart);
                params.put("dayEnd", dayEnd);
                params.put("pageNo", pageNo);
                params.put("pageSize", size);

                String body = httpClient.get(dataSourceProperties.getCwlBaseUrl(), params);
                List<DrawParseResult> records = parseRecords(lotteryCode, body);
                int total = parseTotal(body);

                if (records.isEmpty()) {
                    log.info("福彩历史空页 lottery={} year={} page={}", lotteryCode.getCode(), year, pageNo);
                    break;
                }
                batchConsumer.accept(records);
                log.info("福彩历史进度 lottery={} year={} page={} size={} yearTotal={}",
                        lotteryCode.getCode(), year, pageNo, records.size(), total);

                if (total > 0) {
                    if ((long) pageNo * size >= total) {
                        break;
                    }
                } else if (records.size() < size) {
                    break;
                }
                pageNo++;
            }
        }
    }

    private Map<String, Object> baseParams(LotteryCodeEnum lotteryCode) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", lotteryCode.getSourceKey());
        params.put("issueStart", "");
        params.put("issueEnd", "");
        params.put("dayStart", "");
        params.put("dayEnd", "");
        params.put("systemType", "PC");
        return params;
    }

    private int historyStartYear(LotteryCodeEnum lotteryCode) {
        return switch (lotteryCode) {
            case SSQ -> 2003;
            case QLC -> 2007;
            case FC3D -> 2002;
            default -> 2003;
        };
    }

    private int parseTotal(String body) {
        if (StrUtil.isBlank(body) || !JSONUtil.isTypeJSON(body)) {
            return -1;
        }
        return JSONUtil.parseObj(body).getInt("total", -1);
    }

    private List<DrawParseResult> parseRecords(LotteryCodeEnum lotteryCode, String body) {
        List<DrawParseResult> list = new ArrayList<>();
        if (StrUtil.isBlank(body) || !JSONUtil.isTypeJSON(body)) {
            log.warn("福彩返回非JSON: {}", StrUtil.sub(body, 0, 200));
            return list;
        }
        JSONArray result = JSONUtil.parseObj(body).getJSONArray("result");
        if (result == null || result.isEmpty()) {
            return list;
        }
        for (int i = 0; i < result.size(); i++) {
            DrawParseResult parsed = mapItem(lotteryCode, result.getJSONObject(i));
            if (parsed != null) {
                list.add(parsed);
            }
        }
        return list;
    }

    private DrawParseResult mapItem(LotteryCodeEnum lotteryCode, JSONObject item) {
        String issueNo = item.getStr("code");
        if (StrUtil.isBlank(issueNo)) {
            return null;
        }

        String red = DrawParseHelper.normalizeBalls(item.getStr("red"));
        String blue = lotteryCode == LotteryCodeEnum.FC3D
                ? null
                : DrawParseHelper.normalizeBalls(item.getStr("blue"));

        LocalDateTime drawTime = parseDateTime(item.getStr("date"));
        LocalDate drawDate = drawTime != null ? drawTime.toLocalDate() : parseDate(item.getStr("date"));

        DrawParseResult.DrawParseResultBuilder builder = DrawParseResult.builder()
                .lotteryCode(lotteryCode.getCode())
                .issueNo(issueNo)
                .drawTime(drawTime)
                .drawDate(drawDate)
                .redBalls(red)
                .blueBalls(blue)
                .drawNumbers(DrawParseHelper.buildDrawNumbers(red, blue))
                .salesAmount(DrawParseHelper.toDecimal(item.get("sales")))
                .poolAmount(DrawParseHelper.toDecimal(item.get("poolmoney")))
                .rawPayload(item.toString())
                .source("CWL_OFFICIAL");

        List<DrawParseResult.PrizeItem> prizes = new ArrayList<>();
        JSONArray prizegrades = item.getJSONArray("prizegrades");
        if (prizegrades != null) {
            for (int i = 0; i < prizegrades.size(); i++) {
                JSONObject p = prizegrades.getJSONObject(i);
                String typeName = p.getStr("type");
                if (StrUtil.isBlank(typeName)) {
                    continue;
                }
                prizes.add(DrawParseResult.PrizeItem.builder()
                        .prizeLevel(typeName)
                        .prizeLevelNo(i + 1)
                        .winnerCount(DrawParseHelper.toInt(p.get("typenum")))
                        .prizeAmount(DrawParseHelper.toDecimal(p.get("typemoney")))
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

    private LocalDate parseDate(String text) {
        if (StrUtil.isBlank(text) || text.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(text.substring(0, 10), DATE_ONLY);
        } catch (Exception e) {
            return null;
        }
    }
}
