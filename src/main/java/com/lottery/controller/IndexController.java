package com.lottery.controller;

import com.lottery.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 根路径健康检查 / 接口索引，避免访问 /api 时落入静态资源 404。
 */
@RestController
public class IndexController {

    @GetMapping({"/", "/health"})
    public Result<Map<String, Object>> index() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", "lottery-server");
        data.put("status", "UP");
        data.put("hint", "示例: GET /api/lottery/types");
        return Result.ok(data);
    }
}
