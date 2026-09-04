package com.lottery.service.simulation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lottery.dto.response.SimulationVerifyVO;
import com.lottery.entity.LotteryDraw;
import com.lottery.mapper.LotteryDrawMapper;
import com.lottery.mapper.LotterySimulationMapper;
import com.lottery.service.analysis.impl.LotteryAnalysisServiceImpl;
import com.lottery.service.simulation.impl.LotterySimulationServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LotterySimulationUniformVerifyTest {

    @Test
    void uniformSampler_shouldPassChiSquareAtAlpha05() {
        LotteryDrawMapper drawMapper = mock(LotteryDrawMapper.class);
        when(drawMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        LotterySimulationServiceImpl service = new LotterySimulationServiceImpl(
                mock(LotterySimulationMapper.class),
                drawMapper,
                new LotteryAnalysisServiceImpl(drawMapper)
        );

        SimulationVerifyVO vo = service.verifyUniform("DLT", 10000);
        assertNotNull(vo);
        assertTrue(vo.isPassed(), () -> vo.getSummary()
                + " frontChi2=" + vo.getFront().getChiSquare()
                + " backChi2=" + vo.getBack().getChiSquare());
    }
}
