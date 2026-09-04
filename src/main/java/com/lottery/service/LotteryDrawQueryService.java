package com.lottery.service;

import com.lottery.dto.request.DrawPageQuery;
import com.lottery.dto.response.DrawDetailVO;
import com.lottery.dto.response.PageResult;
import com.lottery.entity.LotteryType;

import java.util.List;

public interface LotteryDrawQueryService {

    List<LotteryType> listTypes();

    PageResult<DrawDetailVO> pageByLottery(DrawPageQuery query);

    DrawDetailVO getByIssue(String lotteryCode, String issueNo);

    DrawDetailVO getLatest(String lotteryCode);
}
