package com.lottery.service.simulation.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lottery.common.enums.LotteryCodeEnum;
import com.lottery.common.exception.BusinessException;
import com.lottery.common.result.ResultCode;
import com.lottery.dto.response.RandomSetsVO;
import com.lottery.dto.response.SimulationBatchVO;
import com.lottery.dto.response.SimulationVerifyVO;
import com.lottery.entity.LotteryDraw;
import com.lottery.entity.LotterySimulation;
import com.lottery.mapper.LotteryDrawMapper;
import com.lottery.mapper.LotterySimulationMapper;
import com.lottery.service.analysis.LotteryAnalysisService;
import com.lottery.service.simulation.LotterySimulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LotterySimulationServiceImpl implements LotterySimulationService {

    /** 均匀独立无放回，贴合公平开奖零假设 */
    public static final String ALGO_VERSION = "v2-uniform-iid";
    public static final int DEFAULT_SET_COUNT = 5;

    public static final String FIXED_RED = "13 15 16 28 31";
    public static final String FIXED_BLUE = "03 08";

    private static final double ALPHA = 0.05;
    /** χ² 临界值（α=0.05）：df=34（前区）、df=11（后区） */
    private static final double CHI2_CRIT_DF_34 = 48.602;
    private static final double CHI2_CRIT_DF_11 = 19.675;

    private final LotterySimulationMapper simulationMapper;
    private final LotteryDrawMapper lotteryDrawMapper;
    private final LotteryAnalysisService analysisService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimulationBatchVO generateForDrawDayMorning(String lotteryCode) {
        LotteryCodeEnum code = LotteryCodeEnum.of(lotteryCode);
        if (code != LotteryCodeEnum.DLT) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前仅支持大乐透自动模拟");
        }
        LotteryDraw latest = lotteryDrawMapper.selectOne(new LambdaQueryWrapper<LotteryDraw>()
                .eq(LotteryDraw::getLotteryCode, code.getCode())
                .orderByDesc(LotteryDraw::getIssueNo)
                .last("LIMIT 1"));
        if (latest == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "暂无开奖数据，无法推算本期期号");
        }
        String upcomingIssue = nextIssueNo(latest.getIssueNo());
        log.info("开奖日凌晨生成本期模拟 latestIssue={} upcomingIssue={}",
                latest.getIssueNo(), upcomingIssue);
        return toVo(saveOrUpgrade(code.getCode(), upcomingIssue), code);
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
        LotteryCodeEnum code = LotteryCodeEnum.of(lotteryCode);
        if (code != LotteryCodeEnum.DLT) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前仅支持大乐透均匀性验证");
        }
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

        SimulationVerifyVO.ZoneStat front = zoneStat(
                "前区", 35, 5, n, frontObs, 34, CHI2_CRIT_DF_34);
        SimulationVerifyVO.ZoneStat back = zoneStat(
                "后区", 12, 2, n, backObs, 11, CHI2_CRIT_DF_11);
        boolean passed = front.isAcceptUniform() && back.isAcceptUniform();

        return SimulationVerifyVO.builder()
                .lotteryCode(code.getCode())
                .algorithmVersion(ALGO_VERSION)
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

    private LotterySimulation saveOrUpgrade(String lotteryCode, String baseIssueNo) {
        LotterySimulation existing = findByIssue(lotteryCode, baseIssueNo);
        if (existing != null && ALGO_VERSION.equals(existing.getAlgorithmVersion())) {
            return existing;
        }

        RandomSetsVO random = analysisService.uniformRandomSets(lotteryCode, DEFAULT_SET_COUNT);
        if (existing != null) {
            existing.setSampleIssues(random.getSampleIssues());
            existing.setSetCount(random.getSetCount());
            existing.setAlgorithmVersion(ALGO_VERSION);
            existing.setMethod(random.getMethod());
            existing.setFixedRed(FIXED_RED);
            existing.setFixedBlue(FIXED_BLUE);
            existing.setSetsJson(JSONUtil.toJsonStr(random.getSets()));
            existing.setDisclaimer(random.getDisclaimer());
            simulationMapper.updateById(existing);
            log.info("模拟批次已升级 code={} baseIssue={} algo={}",
                    lotteryCode, baseIssueNo, ALGO_VERSION);
            return existing;
        }

        LotterySimulation row = new LotterySimulation();
        row.setLotteryCode(lotteryCode);
        row.setBaseIssueNo(baseIssueNo);
        row.setSampleIssues(random.getSampleIssues());
        row.setSetCount(random.getSetCount());
        row.setAlgorithmVersion(ALGO_VERSION);
        row.setMethod(random.getMethod());
        row.setFixedRed(FIXED_RED);
        row.setFixedBlue(FIXED_BLUE);
        row.setSetsJson(JSONUtil.toJsonStr(random.getSets()));
        row.setDisclaimer(random.getDisclaimer());

        try {
            simulationMapper.insert(row);
            log.info("模拟批次已入库 code={} baseIssue={} algo={} sets={}",
                    lotteryCode, baseIssueNo, ALGO_VERSION, row.getSetCount());
        } catch (DuplicateKeyException ex) {
            LotterySimulation again = findByIssue(lotteryCode, baseIssueNo);
            if (again != null) {
                return again;
            }
            throw ex;
        }
        return row;
    }

    private LotterySimulation findByIssue(String lotteryCode, String baseIssueNo) {
        return simulationMapper.selectOne(new LambdaQueryWrapper<LotterySimulation>()
                .eq(LotterySimulation::getLotteryCode, lotteryCode)
                .eq(LotterySimulation::getBaseIssueNo, baseIssueNo)
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
        boolean accept = chi2 < critical;
        return SimulationVerifyVO.ZoneStat.builder()
                .name(name)
                .poolSize(poolSize)
                .pickCount(pickCount)
                .expectedPerBall(expected)
                .chiSquare(round4(chi2))
                .degreesOfFreedom(df)
                .criticalValue(critical)
                .acceptUniform(accept)
                .maxAbsRelativeDeviation(round4(maxRel))
                .build();
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

    /**
     * 由最新已开奖期号推算下一期（开奖日本期）。纯数字期号 +1，保留前导零宽度。
     */
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
