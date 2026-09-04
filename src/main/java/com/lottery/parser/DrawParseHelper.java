package com.lottery.parser;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.lottery.dto.model.DrawParseResult;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 解析辅助：号码规范化、金额解析、内容哈希。
 */
public final class DrawParseHelper {

    private DrawParseHelper() {
    }

    public static String normalizeBalls(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        return Arrays.stream(raw.trim().split("[\\s,|，、]+"))
                .filter(StrUtil::isNotBlank)
                .map(s -> s.length() == 1 && Character.isDigit(s.charAt(0)) ? "0" + s : s)
                .collect(Collectors.joining(" "));
    }

    public static String buildDrawNumbers(String red, String blue) {
        if (StrUtil.isBlank(blue)) {
            return StrUtil.blankToDefault(red, "");
        }
        return StrUtil.blankToDefault(red, "") + " + " + blue;
    }

    public static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).replace(",", "").replace("元", "").trim();
        if (StrUtil.isBlank(text) || "-".equals(text) || "null".equalsIgnoreCase(text)) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).replace(",", "").trim();
        if (StrUtil.isBlank(text) || "-".equals(text)) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String contentHash(DrawParseResult result) {
        String base = result.getLotteryCode() + "|" + result.getIssueNo() + "|"
                + result.getDrawNumbers() + "|" + result.getPoolAmount() + "|"
                + result.getSalesAmount();
        return DigestUtil.sha256Hex(base);
    }
}
