package com.lottery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lottery.common.enums.LotteryCodeEnum;
import com.lottery.common.exception.BusinessException;
import com.lottery.common.result.ResultCode;
import com.lottery.dto.request.DrawPageQuery;
import com.lottery.dto.response.DrawDetailVO;
import com.lottery.dto.response.PageResult;
import com.lottery.entity.LotteryDraw;
import com.lottery.entity.LotteryPrizeDetail;
import com.lottery.entity.LotteryType;
import com.lottery.mapper.LotteryDrawMapper;
import com.lottery.mapper.LotteryPrizeDetailMapper;
import com.lottery.mapper.LotteryTypeMapper;
import com.lottery.service.LotteryDrawQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LotteryDrawQueryServiceImpl implements LotteryDrawQueryService {

    private final LotteryTypeMapper lotteryTypeMapper;
    private final LotteryDrawMapper lotteryDrawMapper;
    private final LotteryPrizeDetailMapper prizeDetailMapper;

    @Override
    public List<LotteryType> listTypes() {
        return lotteryTypeMapper.selectList(new LambdaQueryWrapper<LotteryType>()
                .eq(LotteryType::getEnabled, 1)
                .orderByAsc(LotteryType::getSortOrder));
    }

    @Override
    public PageResult<DrawDetailVO> pageByLottery(DrawPageQuery query) {
        validateCode(query.getLotteryCode());
        Page<LotteryDraw> page = lotteryDrawMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<LotteryDraw>()
                        .eq(LotteryDraw::getLotteryCode, query.getLotteryCode())
                        .orderByDesc(LotteryDraw::getIssueNo));

        List<DrawDetailVO> records = page.getRecords().stream()
                .map(d -> toVo(d, false))
                .collect(Collectors.toList());

        return PageResult.<DrawDetailVO>builder()
                .pageNum(page.getCurrent())
                .pageSize(page.getSize())
                .total(page.getTotal())
                .records(records)
                .build();
    }

    @Override
    public DrawDetailVO getByIssue(String lotteryCode, String issueNo) {
        validateCode(lotteryCode);
        LotteryDraw draw = lotteryDrawMapper.selectOne(new LambdaQueryWrapper<LotteryDraw>()
                .eq(LotteryDraw::getLotteryCode, lotteryCode)
                .eq(LotteryDraw::getIssueNo, issueNo)
                .last("LIMIT 1"));
        if (draw == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "未找到该期开奖数据");
        }
        return toVo(draw, true);
    }

    @Override
    public DrawDetailVO getLatest(String lotteryCode) {
        validateCode(lotteryCode);
        LotteryDraw draw = lotteryDrawMapper.selectOne(new LambdaQueryWrapper<LotteryDraw>()
                .eq(LotteryDraw::getLotteryCode, lotteryCode)
                .orderByDesc(LotteryDraw::getIssueNo)
                .last("LIMIT 1"));
        if (draw == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "暂无开奖数据");
        }
        return toVo(draw, true);
    }

    private DrawDetailVO toVo(LotteryDraw draw, boolean withPrizes) {
        String name;
        try {
            name = LotteryCodeEnum.of(draw.getLotteryCode()).getName();
        } catch (Exception e) {
            name = draw.getLotteryCode();
        }

        List<DrawDetailVO.PrizeDetailVO> prizes = Collections.emptyList();
        if (withPrizes) {
            List<LotteryPrizeDetail> details = prizeDetailMapper.selectList(
                    new LambdaQueryWrapper<LotteryPrizeDetail>()
                            .eq(LotteryPrizeDetail::getDrawId, draw.getId())
                            .orderByAsc(LotteryPrizeDetail::getPrizeLevelNo));
            prizes = details.stream()
                    .map(p -> DrawDetailVO.PrizeDetailVO.builder()
                            .prizeLevel(p.getPrizeLevel())
                            .prizeLevelNo(p.getPrizeLevelNo())
                            .winnerCount(p.getWinnerCount())
                            .prizeAmount(p.getPrizeAmount())
                            .totalAmount(p.getTotalAmount())
                            .build())
                    .collect(Collectors.toList());
        }

        return DrawDetailVO.builder()
                .id(draw.getId())
                .lotteryCode(draw.getLotteryCode())
                .lotteryName(name)
                .issueNo(draw.getIssueNo())
                .drawTime(draw.getDrawTime())
                .drawDate(draw.getDrawDate())
                .redBalls(draw.getRedBalls())
                .blueBalls(draw.getBlueBalls())
                .drawNumbers(draw.getDrawNumbers())
                .salesAmount(draw.getSalesAmount())
                .poolAmount(draw.getPoolAmount())
                .source(draw.getSource())
                .prizes(prizes)
                .build();
    }

    private void validateCode(String code) {
        LotteryCodeEnum.of(code);
        Map<String, LotteryType> typeMap = listTypes().stream()
                .collect(Collectors.toMap(LotteryType::getCode, t -> t, (a, b) -> a));
        if (!typeMap.containsKey(code.toUpperCase()) && !typeMap.containsKey(code)) {
            // 枚举合法即可查询，表中未启用也允许读历史
        }
    }
}
