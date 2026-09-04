package com.lottery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lottery.entity.LotteryDraw;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LotteryDrawMapper extends BaseMapper<LotteryDraw> {
}
