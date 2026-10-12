package com.lottery.service.simulation.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lottery.common.enums.LotteryCodeEnum;
import com.lottery.common.exception.BusinessException;
import com.lottery.common.result.ResultCode;
import com.lottery.config.properties.LotterySimulationProperties;
import com.lottery.dto.response.AlgorithmSelectVO;
import com.lottery.dto.response.RandomSetsVO;
import com.lottery.dto.response.SimulationBatchVO;
import com.lottery.dto.response.SimulationEvaluateVO;
import com.lottery.dto.response.SimulationVerifyVO;
import com.lottery.entity.LotteryDraw;
import com.lottery.entity.LotterySimulation;
import com.lottery.entity.LotterySimulationEval;
import com.lottery.mapper.LotteryDrawMapper;
import com.lottery.mapper.LotterySimulationEvalMapper;
import com.lottery.mapper.LotterySimulationMapper;
import com.lottery.parser.DrawParseHelper;
import com.lottery.service.analysis.LotteryAnalysisService;
import com.lottery.service.simulation.LotterySimulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LotterySimulationServiceImpl implements LotterySimulationService {

    public static final String ALGO_UNIFORM = "v2-uniform-iid";
    public static final String ALGO_FREQ = "v3-freq-weighted-all";
    public static final int DEFAULT_SET_COUNT = 5;

    public static final String FIXED_RED = "13 15 16 28 31";
    public static final String FIXED_BLUE = "03 08";

    private static final double ALPHA = 0.05;
    private static final double CHI2_CRIT_DF_34 = 48.602;
    private static final double CHI2_CRIT_DF_11 = 19.675;
    private static final String SCORE_RULE = "max前区命中 + 0.5 * max后区命中；平局选均匀";
    private static final String SELECT_DISCLAIMER =
            "择优基于历史模拟对照实开的描述性回测，仅供学习对比，不构成荐号或提高中奖率承诺。";

    private final LotterySimulationMapper simulationMapper;
    private final LotterySimulationEvalMapper evalMapper;
    private final LotteryDrawMapper lotteryDrawMapper;
    private final LotteryAnalysisService analysisService;
    private final LotterySimulationProperties simulationProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimulationBatchVO generateForDrawDayMorning(String lotteryCode) {
        LotteryCodeEnum code = requireDlt(lotteryCode);
        LotteryDraw latest = requireLatestDraw(code.getCode());
        String upcomingIssue = nextIssueNo(latest.getIssueNo());
        AlgorithmSelectVO select = selectAlgorithm(code.getCode());
        String algo = select.getSelectedVersion();
        log.info("开奖日凌晨生成本期模拟 latestIssue={} upcomingIssue={} algo={}",
                latest.getIssueNo(), upcomingIssue, algo);
        return toVo(saveOrUpgrade(code.getCode(), upcomingIssue, algo), code);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimulationBatchVO generateForLatest(String lotteryCode) {
        return generateForDrawDayMorning(lotteryCode);
    }

    @Override
    public SimulationBatchVO latest(String lotteryCode) {
        LotteryCodeEnum code = LotteryCodeEnum.of(lotteryCode);
        if (code != LotteryCodeEnum.DLT) {
            return null;
        }
        LotterySimulation row = simulationMapper.selectOne(new LambdaQueryWrapper<LotterySimulation>()
                .eq(LotterySimulation::getLotteryCode, code.getCode())
                .orderByDesc(LotterySimulation::getBaseIssueNo)
                .last("LIMIT 1"));
        if (row == null) {
            return null;
        }
        return toVo(row, code);
    }

    @Override
    public SimulationVerifyVO verifyUniform(String lotteryCode, int trials) {
        LotteryCodeEnum code = requireDlt(lotteryCode);
        int n = Math.min(Math.max(trials, 1000), 50000);

        Map<String, Long> frontObs = new HashMap<>();
        Map<String, Long> backObs = new HashMap<>();
        for (int i = 1; i <= 35; i++) {
            frontObs.put(pad2(i), 0L);
        }
        for (int i = 1; i <= 12; i++) {
            backObs.put(pad2(i), 0L);
        }

        for (int t = 0; t < n; t++) {
            RandomSetsVO one = analysisService.uniformRandomSets(code.getCode(), 1);
            RandomSetsVO.RandomSetItem item = one.getSets().get(0);
            countBalls(item.getRedBalls(), frontObs);
            countBalls(item.getBlueBalls(), backObs);
        }

        SimulationVerifyVO.ZoneStat front = zoneStat("前区", 35, 5, n, frontObs, 34, CHI2_CRIT_DF_34);
        SimulationVerifyVO.ZoneStat back = zoneStat("后区", 12, 2, n, backObs, 11, CHI2_CRIT_DF_11);
        boolean passed = front.isAcceptUniform() && back.isAcceptUniform();

        return SimulationVerifyVO.builder()
                .lotteryCode(code.getCode())
                .algorithmVersion(ALGO_UNIFORM)
                .nullHypothesis("各期独立；前区/后区在各自号码池上等权无放回抽取")
                .trials(n)
                .significanceLevel(ALPHA)
                .front(front)
                .back(back)
                .passed(passed)
                .summary(passed
                        ? "在 α=0.05 下未拒绝均匀零假设，模拟器与公平开奖模型一致。"
                        : "在 α=0.05 下拒绝均匀零假设，请检查随机实现或增大试验次数后复测。")
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimulationEvaluateVO evaluateIssue(String lotteryCode, String issueNo) {
        LotteryCodeEnum code = requireDlt(lotteryCode);
        if (StrUtil.isBlank(issueNo)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "期号不能为空");
        }
        LotterySimulation sim = findByIssue(code.getCode(), issueNo);
        if (sim == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "未找到期号 " + issueNo + " 的模拟批次");
        }
        LotteryDraw draw = findDraw(code.getCode(), issueNo);
        if (draw == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "未找到期号 " + issueNo + " 的开奖结果");
        }
        SimulationEvaluateVO vo = scoreAgainstDraw(sim, draw);
        persistEval(vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SimulationEvaluateVO> evaluateHistory(String lotteryCode, int limit) {
        LotteryCodeEnum code = requireDlt(lotteryCode);
        int n = Math.min(Math.max(limit, 1), 100);
        List<LotterySimulation> sims = simulationMapper.selectList(new LambdaQueryWrapper<LotterySimulation>()
                .eq(LotterySimulation::getLotteryCode, code.getCode())
                .orderByDesc(LotterySimulation::getBaseIssueNo)
                .last("LIMIT " + n));
        List<SimulationEvaluateVO> list = new ArrayList<>();
        for (LotterySimulation sim : sims) {
            LotteryDraw draw = findDraw(code.getCode(), sim.getBaseIssueNo());
            if (draw == null) {
                continue;
            }
            SimulationEvaluateVO vo = scoreAgainstDraw(sim, draw);
            persistEval(vo);
            list.add(vo);
        }
        return list;
    }

    @Override
    public AlgorithmSelectVO selectAlgorithm(String lotteryCode) {
        LotteryCodeEnum code = requireDlt(lotteryCode);
        int lookback = Math.max(1, simulationProperties.getLookbackIssues());
        List<String> candidates = simulationProperties.getCandidates();
        if (candidates == null || candidates.isEmpty()) {
            candidates = List.of(ALGO_UNIFORM, ALGO_FREQ);
        }

        List<LotteryDraw> recentDraws = lotteryDrawMapper.selectList(new LambdaQueryWrapper<LotteryDraw>()
                .eq(LotteryDraw::getLotteryCode, code.getCode())
                .orderByDesc(LotteryDraw::getIssueNo)
                .last("LIMIT " + lookback));

        List<AlgorithmSelectVO.AlgoScore> scores = new ArrayList<>();
        String selected = ALGO_UNIFORM;
        double bestAvg = -1;

        for (String algo : candidates) {
            List<Double> batchScores = new ArrayList<>();
            for (LotteryDraw draw : recentDraws) {
                try {
                    RandomSetsVO generated = generateByAlgo(code.getCode(), algo, draw.getIssueNo());
                    SimulationEvaluateVO scored = scoreSets(
                            code.getCode(),
                            draw.getIssueNo(),
                            algo,
                            FIXED_RED,
                            FIXED_BLUE,
                            generated.getSets(),
                            draw.getRedBalls(),
                            draw.getBlueBalls());
                    batchScores.add(scored.getBatchScore());
                } catch (Exception ex) {
                    log.debug("walk-forward 跳过 issue={} algo={}: {}", draw.getIssueNo(), algo, ex.getMessage());
                }
            }
            double avg = batchScores.isEmpty()
                    ? 0
                    : batchScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            avg = round4(avg);
            scores.add(AlgorithmSelectVO.AlgoScore.builder()
                    .algorithmVersion(algo)
                    .sampleCount(batchScores.size())
                    .averageBatchScore(avg)
                    .selected(false)
                    .build());
            if (batchScores.isEmpty()) {
                continue;
            }
            if (bestAvg < 0 || avg > bestAvg + 1e-9) {
                bestAvg = avg;
                selected = algo;
            } else if (Math.abs(avg - bestAvg) <= 1e-9 && ALGO_UNIFORM.equals(algo)) {
                selected = ALGO_UNIFORM;
            }
        }

        // 若所有候选无样本，默认均匀
        if (scores.stream().allMatch(s -> s.getSampleCount() == 0)) {
            selected = ALGO_UNIFORM;
        }
        String finalSelected = selected;
        scores.forEach(s -> s.setSelected(finalSelected.equals(s.getAlgorithmVersion())));

        return AlgorithmSelectVO.builder()
                .lotteryCode(code.getCode())
                .selectedVersion(finalSelected)
                .lookbackIssues(lookback)
                .scoreRule(SCORE_RULE)
                .disclaimer(SELECT_DISCLAIMER)
                .candidates(scores)
                .build();
    }

    private LotterySimulation saveOrUpgrade(String lotteryCode, String baseIssueNo, String algorithmVersion) {
        LotterySimulation existing = findByIssue(lotteryCode, baseIssueNo);
        if (existing != null && algorithmVersion.equals(existing.getAlgorithmVersion())) {
            return existing;
        }

        RandomSetsVO random = generateByAlgo(lotteryCode, algorithmVersion, baseIssueNo);
        if (existing != null) {
            existing.setSampleIssues(random.getSampleIssues());
            existing.setSetCount(random.getSetCount());
            existing.setAlgorithmVersion(algorithmVersion);
            existing.setMethod(random.getMethod());
            existing.setFixedRed(FIXED_RED);
            existing.setFixedBlue(FIXED_BLUE);
            existing.setSetsJson(JSONUtil.toJsonStr(random.getSets()));
            existing.setDisclaimer(random.getDisclaimer());
            simulationMapper.updateById(existing);
            log.info("模拟批次已升级 code={} baseIssue={} algo={}",
                    lotteryCode, baseIssueNo, algorithmVersion);
            return existing;
        }

        LotterySimulation row = new LotterySimulation();
        row.setLotteryCode(lotteryCode);
        row.setBaseIssueNo(baseIssueNo);
        row.setSampleIssues(random.getSampleIssues());
        row.setSetCount(random.getSetCount());
        row.setAlgorithmVersion(algorithmVersion);
        row.setMethod(random.getMethod());
        row.setFixedRed(FIXED_RED);
        row.setFixedBlue(FIXED_BLUE);
        row.setSetsJson(JSONUtil.toJsonStr(random.getSets()));
        row.setDisclaimer(random.getDisclaimer());

        try {
            simulationMapper.insert(row);
            log.info("模拟批次已入库 code={} baseIssue={} algo={} sets={}",
                    lotteryCode, baseIssueNo, algorithmVersion, row.getSetCount());
        } catch (DuplicateKeyException ex) {
            LotterySimulation again = findByIssue(lotteryCode, baseIssueNo);
            if (again != null) {
                return again;
            }
            throw ex;
        }
        return row;
    }

    private RandomSetsVO generateByAlgo(String lotteryCode, String algorithmVersion, String targetIssueNo) {
        if (ALGO_FREQ.equals(algorithmVersion) || algorithmVersion.startsWith("v3-freq")) {
            return analysisService.randomSetsBeforeIssue(lotteryCode, DEFAULT_SET_COUNT, targetIssueNo);
        }
        // 默认均匀；targetIssueNo 不参与抽样，仅作 walk-forward 对齐
        return analysisService.uniformRandomSets(lotteryCode, DEFAULT_SET_COUNT);
    }

    private SimulationEvaluateVO scoreAgainstDraw(LotterySimulation sim, LotteryDraw draw) {
        List<RandomSetsVO.RandomSetItem> sets = JSONUtil.toList(sim.getSetsJson(), RandomSetsVO.RandomSetItem.class);
        return scoreSets(
                sim.getLotteryCode(),
                sim.getBaseIssueNo(),
                sim.getAlgorithmVersion(),
                sim.getFixedRed(),
                sim.getFixedBlue(),
                sets,
                draw.getRedBalls(),
                draw.getBlueBalls());
    }

    private SimulationEvaluateVO scoreSets(String lotteryCode,
                                           String issueNo,
                                           String algorithmVersion,
                                           String fixedRed,
                                           String fixedBlue,
                                           List<RandomSetsVO.RandomSetItem> sets,
                                           String actualRed,
                                           String actualBlue) {
        Set<String> actFront = toBallSet(actualRed);
        Set<String> actBack = toBallSet(actualBlue);

        List<SimulationEvaluateVO.SetScore> scores = new ArrayList<>();
        int maxFront = 0;
        int maxBack = 0;

        int fixedFront = hits(toBallSet(fixedRed), actFront);
        int fixedBack = hits(toBallSet(fixedBlue), actBack);
        scores.add(SimulationEvaluateVO.SetScore.builder()
                .index(0)
                .redBalls(fixedRed)
                .blueBalls(fixedBlue)
                .frontHits(fixedFront)
                .backHits(fixedBack)
                .fixed(true)
                .build());

        if (sets != null) {
            for (RandomSetsVO.RandomSetItem item : sets) {
                int fh = hits(toBallSet(item.getRedBalls()), actFront);
                int bh = hits(toBallSet(item.getBlueBalls()), actBack);
                maxFront = Math.max(maxFront, fh);
                maxBack = Math.max(maxBack, bh);
                scores.add(SimulationEvaluateVO.SetScore.builder()
                        .index(item.getIndex())
                        .redBalls(item.getRedBalls())
                        .blueBalls(item.getBlueBalls())
                        .frontHits(fh)
                        .backHits(bh)
                        .fixed(false)
                        .build());
            }
        }

        double batchScore = round4(maxFront + 0.5 * maxBack);
        return SimulationEvaluateVO.builder()
                .lotteryCode(lotteryCode)
                .issueNo(issueNo)
                .algorithmVersion(algorithmVersion)
                .actualRed(DrawParseHelper.normalizeBalls(actualRed))
                .actualBlue(DrawParseHelper.normalizeBalls(actualBlue))
                .fixedFrontHits(fixedFront)
                .fixedBackHits(fixedBack)
                .batchScore(batchScore)
                .sets(scores)
                .build();
    }

    private void persistEval(SimulationEvaluateVO vo) {
        LotterySimulationEval existing = evalMapper.selectOne(new LambdaQueryWrapper<LotterySimulationEval>()
                .eq(LotterySimulationEval::getLotteryCode, vo.getLotteryCode())
                .eq(LotterySimulationEval::getIssueNo, vo.getIssueNo())
                .eq(LotterySimulationEval::getAlgorithmVersion, vo.getAlgorithmVersion())
                .last("LIMIT 1"));
        String scoresJson = JSONUtil.toJsonStr(vo.getSets());
        BigDecimal score = BigDecimal.valueOf(vo.getBatchScore()).setScale(4, RoundingMode.HALF_UP);
        if (existing != null) {
            existing.setSetScoresJson(scoresJson);
            existing.setBatchScore(score);
            existing.setFixedFrontHits(vo.getFixedFrontHits());
            existing.setFixedBackHits(vo.getFixedBackHits());
            evalMapper.updateById(existing);
            return;
        }
        LotterySimulationEval row = new LotterySimulationEval();
        row.setLotteryCode(vo.getLotteryCode());
        row.setIssueNo(vo.getIssueNo());
        row.setAlgorithmVersion(vo.getAlgorithmVersion());
        row.setSetScoresJson(scoresJson);
        row.setBatchScore(score);
        row.setFixedFrontHits(vo.getFixedFrontHits());
        row.setFixedBackHits(vo.getFixedBackHits());
        try {
            evalMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            log.debug("评估记录已存在 code={} issue={} algo={}",
                    vo.getLotteryCode(), vo.getIssueNo(), vo.getAlgorithmVersion());
        }
    }

    private LotteryCodeEnum requireDlt(String lotteryCode) {
        LotteryCodeEnum code = LotteryCodeEnum.of(lotteryCode);
        if (code != LotteryCodeEnum.DLT) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前仅支持大乐透");
        }
        return code;
    }

    private LotteryDraw requireLatestDraw(String lotteryCode) {
        LotteryDraw latest = lotteryDrawMapper.selectOne(new LambdaQueryWrapper<LotteryDraw>()
                .eq(LotteryDraw::getLotteryCode, lotteryCode)
                .orderByDesc(LotteryDraw::getIssueNo)
                .last("LIMIT 1"));
        if (latest == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "暂无开奖数据，无法推算本期期号");
        }
        return latest;
    }

    private LotterySimulation findByIssue(String lotteryCode, String baseIssueNo) {
        return simulationMapper.selectOne(new LambdaQueryWrapper<LotterySimulation>()
                .eq(LotterySimulation::getLotteryCode, lotteryCode)
                .eq(LotterySimulation::getBaseIssueNo, baseIssueNo)
                .last("LIMIT 1"));
    }

    private LotteryDraw findDraw(String lotteryCode, String issueNo) {
        return lotteryDrawMapper.selectOne(new LambdaQueryWrapper<LotteryDraw>()
                .eq(LotteryDraw::getLotteryCode, lotteryCode)
                .eq(LotteryDraw::getIssueNo, issueNo)
                .last("LIMIT 1"));
    }

    private SimulationBatchVO toVo(LotterySimulation row, LotteryCodeEnum code) {
        return SimulationBatchVO.builder()
                .id(row.getId())
                .lotteryCode(row.getLotteryCode())
                .lotteryName(code.getName())
                .baseIssueNo(row.getBaseIssueNo())
                .sampleIssues(row.getSampleIssues() == null ? 0 : row.getSampleIssues())
                .setCount(row.getSetCount() == null ? 0 : row.getSetCount())
                .algorithmVersion(row.getAlgorithmVersion())
                .method(row.getMethod())
                .disclaimer(row.getDisclaimer())
                .fixedRed(row.getFixedRed())
                .fixedBlue(row.getFixedBlue())
                .sets(JSONUtil.toList(row.getSetsJson(), RandomSetsVO.RandomSetItem.class))
                .createdAt(row.getCreatedAt())
                .build();
    }

    private static SimulationVerifyVO.ZoneStat zoneStat(String name,
                                                        int poolSize,
                                                        int pickCount,
                                                        int trials,
                                                        Map<String, Long> obs,
                                                        int df,
                                                        double critical) {
        double expected = trials * (pickCount / (double) poolSize);
        double chi2 = 0;
        double maxRel = 0;
        for (long o : obs.values()) {
            double diff = o - expected;
            chi2 += diff * diff / expected;
            maxRel = Math.max(maxRel, Math.abs(diff) / expected);
        }
        return SimulationVerifyVO.ZoneStat.builder()
                .name(name)
                .poolSize(poolSize)
                .pickCount(pickCount)
                .expectedPerBall(expected)
                .chiSquare(round4(chi2))
                .degreesOfFreedom(df)
                .criticalValue(critical)
                .acceptUniform(chi2 < critical)
                .maxAbsRelativeDeviation(round4(maxRel))
                .build();
    }

    private static Set<String> toBallSet(String balls) {
        String normalized = DrawParseHelper.normalizeBalls(balls);
        if (StrUtil.isBlank(normalized)) {
            return Set.of();
        }
        return Arrays.stream(normalized.split("\\s+"))
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static int hits(Set<String> sim, Set<String> actual) {
        if (sim.isEmpty() || actual.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (String b : sim) {
            if (actual.contains(b)) {
                n++;
            }
        }
        return n;
    }

    private static void countBalls(String balls, Map<String, Long> counter) {
        if (balls == null || balls.isBlank()) {
            return;
        }
        Arrays.stream(balls.trim().split("[\\s,]+"))
                .filter(s -> !s.isBlank())
                .forEach(b -> counter.merge(b, 1L, Long::sum));
    }

    private static String pad2(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    static String nextIssueNo(String current) {
        if (current == null || !current.matches("\\d+")) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "无法解析期号: " + current);
        }
        int width = current.length();
        long value = Long.parseLong(current) + 1;
        String next = Long.toString(value);
        if (next.length() >= width) {
            return next;
        }
        return String.format("%0" + width + "d", value);
    }
}
