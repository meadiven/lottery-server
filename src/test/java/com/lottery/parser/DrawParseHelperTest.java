package com.lottery.parser;

import com.lottery.dto.model.DrawParseResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DrawParseHelperTest {

    @Test
    void normalizeAndHash() {
        assertEquals("01 02 03", DrawParseHelper.normalizeBalls("1,2,3"));
        assertEquals("01 02 + 03", DrawParseHelper.buildDrawNumbers("01 02", "03"));
        assertEquals(new BigDecimal("1234.56"), DrawParseHelper.toDecimal("1,234.56元"));

        DrawParseResult result = DrawParseResult.builder()
                .lotteryCode("SSQ")
                .issueNo("2026001")
                .drawNumbers("01 02 03 04 05 06 + 07")
                .poolAmount(new BigDecimal("100"))
                .salesAmount(new BigDecimal("200"))
                .build();
        assertNotNull(DrawParseHelper.contentHash(result));
        assertEquals(64, DrawParseHelper.contentHash(result).length());
    }
}
