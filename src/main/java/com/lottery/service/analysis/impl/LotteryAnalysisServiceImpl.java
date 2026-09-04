package com.lottery.service.analysis.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lottery.common.enums.LotteryCodeEnum;
import com.lottery.common.exception.BusinessException;
import com.lottery.common.result.ResultCode;
import com.lottery.dto.response.FrequencyStatVO;
import com.lottery.dto.response.RandomSetsVO;
import com.lottery.entity.LotteryDraw;
import com.lottery.mapper.LotteryDrawMapper;
import com.lottery.parser.DrawParseHelper;
import com.lottery.service.analysis.LotteryAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class LotteryAnalysisServiceImpl implements LotteryAnalysisService {

    private static final String DISCLAIMER_FREQ =
            "本组号码为基于历史频次的加权随机抽样，仅供对比学习，不构成荐号、预测或投注建议。";
    private static final String DISCLAIMER_UNIFORM =
            "本组号码为均匀独立无放回随机抽样（贴合公平开奖零假设），仅供开奖后对比学习，不构成荐号、预测或投注建议。";

    private final LotteryDrawMapper lotteryDrawMapper;

    @Override
    public FrequencyStatVO frequencyStats(String lotteryCode, int recentIssues) {
        LotteryCodeEnum code = LotteryCodeEnum.of(lotteryCode);
        List<LotteryDraw> draws = loadDraws(code.getCode(), recentIssues);
        Map<String, Long> redFreq = new HashMap<>();
        Map<String, Long> blueFreq = new HashMap<>();
        for (LotteryDraw draw : draws) {
            countBalls(draw.getRedBalls(), redFreq);
            countBalls(draw.getBlueBalls(), blueFreq);
        }
        return FrequencyStatVO.builder()
                .lotteryCode(code.getCode())
                .sampleIssues(draws.size())
                .redFrequency(redFreq)
                .blueFrequency(blueFreq)
                .redRank(toRank(redFreq))
                .blueRank(toRank(blueFreq))
                .build();
    }

    @Override
    public RandomSetsVO randomSets(String lotteryCode, int count, int recentIssues) {
        LotteryCodeEnum code = LotteryCodeEnum.of(lotteryCode);
        int setCount = Math.min(Math.max(count, 1), 10);
        List<LotteryDraw> draws = loadDraws(code.getCode(), recentIssues);

        Map<String, Long> redFreq = new HashMap<>();
        Map<String, Long> blueFreq = new HashMap<>();
        for (LotteryDraw draw : draws) {
            countBalls(draw.getRedBalls(), redFreq);
            countBalls(draw.getBlueBalls(), blueFreq);
        }

        List<RandomSetsVO.RandomSetItem> sets = new ArrayList<>();
        for (int i = 1; i <= setCount; i++) {
            sets.add(buildOneSet(code, i, redFreq, blueFreq, false));
        }

        return RandomSetsVO.builder()
                .lotteryCode(code.getCode())
                .lotteryName(code.getName())
                .sampleIssues(draws.size())
                .setCount(setCount)
                .method("历史频次加权随机抽样（无放回组合）")
                .disclaimer(DISCLAIMER_FREQ)
                .sets(sets)
                .build();
    }

    @Override
    public RandomSetsVO uniformRandomSets(String lotteryCode, int count) {
        LotteryCodeEnum code = LotteryCodeEnum.of(lotteryCode);
        int setCount = Math.min(Math.max(count, 1), 10);
        Long historyCount = lotteryDrawMapper.selectCount(new LambdaQueryWrapper<LotteryDraw>()
                .eq(LotteryDraw::getLotteryCode, code.getCode()));
        int sampleIssues = historyCount == null ? 0 : historyCount.intValue();

        List<RandomSetsVO.RandomSetItem> sets = new ArrayList<>();
        for (int i = 1; i <= setCount; i++) {
            sets.add(buildOneSet(code, i, Map.of(), Map.of(), true));
        }

        return RandomSetsVO.builder()
                .lotteryCode(code.getCode())
                .lotteryName(code.getName())
                .sampleIssues(sampleIssues)
                .setCount(setCount)
                .method("均匀独立无放回抽样（公平开奖零假设）")
                .disclaimer(DISCLAIMER_UNIFORM)
                .sets(sets)
                .build();
    }

    private RandomSetsVO.RandomSetItem buildOneSet(LotteryCodeEnum code,
                                                   int index,
                                                   Map<String, Long> redFreq,
                                                   Map<String, Long> blueFreq,
                                                   boolean uniform) {
        return switch (code) {
            case DLT -> comboSet(index, redFreq, blueFreq, 1, 35, 5, 1, 12, 2, true, uniform);
            case SSQ -> comboSet(index, redFreq, blueFreq, 1, 33, 6, 1, 16, 1, false, uniform);
            case QLC -> qlcSet(index, redFreq, blueFreq, uniform);
            case FC3D, PL3 -> digitSet(index, redFreq, 3, uniform);
            case PL5 -> digitSet(index, redFreq, 5, uniform);
        };
    }

    private RandomSetsVO.RandomSetItem comboSet(int index,
                                                Map<String, Long> redFreq,
                                                Map<String, Long> blueFreq,
                                                int redMin, int redMax, int redCount,
                                                int blueMin, int blueMax, int blueCount,
                                                boolean blueDistinct,
                                                boolean uniform) {
        List<String> redPool = numberPool(redMin, redMax);
        List<String> bluePool = numberPool(blueMin, blueMax);
        List<String> red = sampleDistinct(redPool, redFreq, redCount, uniform);
        List<String> blue;
        if (blueDistinct) {
            blue = sampleDistinct(bluePool, blueFreq, blueCount, uniform);
        } else {
            blue = List.of(pickOne(bluePool, blueFreq, uniform));
        }
        String redText = String.join(" ", red);
        String blueText = String.join(" ", blue);
        return RandomSetsVO.RandomSetItem.builder()
                .index(index)
                .redBalls(redText)
                .blueBalls(blueText)
                .drawNumbers(DrawParseHelper.buildDrawNumbers(redText, blueText))
                .build();
    }

    private RandomSetsVO.RandomSetItem qlcSet(int index,
                                             Map<String, Long> redFreq,
                                             Map<String, Long> blueFreq,
                                             boolean uniform) {
        List<String> pool = numberPool(1, 30);
        List<String> basic = sampleDistinct(pool, redFreq, 7, uniform);
        Set<String> used = new HashSet<>(basic);
        List<String> remain = pool.stream().filter(b -> !used.contains(b)).collect(Collectors.toList());
        Map<String, Long> specialFreq = blueFreq.isEmpty() ? redFreq : blueFreq;
        String special = pickOne(remain, specialFreq, uniform);
        String redText = String.join(" ", basic);
        return RandomSetsVO.RandomSetItem.builder()
                .index(index)
                .redBalls(redText)
                .blueBalls(special)
                .drawNumbers(DrawParseHelper.buildDrawNumbers(redText, special))
                .build();
    }

    private RandomSetsVO.RandomSetItem digitSet(int index,
                                                Map<String, Long> digitFreq,
                                                int digits,
                                                boolean uniform) {
        List<String> pool = IntStream.rangeClosed(0, 9)
                .mapToObj(String::valueOf)
                .collect(Collectors.toList());
        List<String> picked = new ArrayList<>();
        for (int i = 0; i < digits; i++) {
            picked.add(pickOne(pool, digitFreq, uniform));
        }
        String text = String.join(" ", picked);
        return RandomSetsVO.RandomSetItem.builder()
                .index(index)
                .redBalls(text)
                .blueBalls(null)
                .drawNumbers(text)
                .build();
    }

    private List<String> numberPool(int min, int max) {
        return IntStream.rangeClosed(min, max)
                .mapToObj(n -> n < 10 ? "0" + n : String.valueOf(n))
                .collect(Collectors.toList());
    }

    private List<String> sampleDistinct(List<String> pool,
                                        Map<String, Long> freq,
                                        int count,
                                        boolean uniform) {
        List<String> candidates = new ArrayList<>(pool);
        List<String> picked = new ArrayList<>();
        int n = Math.min(count, candidates.size());
        for (int i = 0; i < n; i++) {
            String ball = pickOne(candidates, freq, uniform);
            picked.add(ball);
            candidates.remove(ball);
        }
        picked.sort(Comparator.naturalOrder());
        return picked;
    }

    private String pickOne(List<String> pool, Map<String, Long> freq, boolean uniform) {
        if (pool == null || pool.isEmpty()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "号码池为空，无法抽样");
        }
        if (uniform) {
            return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        }
        double[] weights = new double[pool.size()];
        double sum = 0;
        for (int i = 0; i < pool.size(); i++) {
            long w = freq.getOrDefault(pool.get(i), 0L);
            weights[i] = Math.max(1.0, w);
            sum += weights[i];
        }
        double r = ThreadLocalRandom.current().nextDouble(sum);
        double acc = 0;
        for (int i = 0; i < pool.size(); i++) {
            acc += weights[i];
            if (r <= acc) {
                return pool.get(i);
            }
        }
        return pool.get(pool.size() - 1);
    }

    private List<LotteryDraw> loadDraws(String lotteryCode, int recentIssues) {
        LambdaQueryWrapper<LotteryDraw> wrapper = new LambdaQueryWrapper<LotteryDraw>()
                .eq(LotteryDraw::getLotteryCode, lotteryCode)
                .orderByDesc(LotteryDraw::getIssueNo);
        if (recentIssues > 0) {
            wrapper.last("LIMIT " + Math.min(recentIssues, 5000));
        }
        List<LotteryDraw> draws = lotteryDrawMapper.selectList(wrapper);
        if (draws.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "暂无可用于抽样的历史开奖数据");
        }
        return draws;
    }

    private void countBalls(String balls, Map<String, Long> counter) {
        if (StrUtil.isBlank(balls)) {
            return;
        }
        Arrays.stream(balls.trim().split("[\\s,]+"))
                .filter(StrUtil::isNotBlank)
                .forEach(b -> counter.merge(b, 1L, Long::sum));
    }

    private List<FrequencyStatVO.BallCount> toRank(Map<String, Long> freq) {
        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(e -> FrequencyStatVO.BallCount.builder()
                        .ball(e.getKey())
                        .count(e.getValue())
                        .build())
                .collect(Collectors.toList());
    }
}
