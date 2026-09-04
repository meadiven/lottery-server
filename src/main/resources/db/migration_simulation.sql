-- 大乐透开奖后自动模拟批次（仅学习对比，非荐号）
CREATE TABLE IF NOT EXISTS lottery_simulation (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    lottery_code        VARCHAR(32)  NOT NULL COMMENT '彩种编码，当前仅 DLT',
    base_issue_no       VARCHAR(32)  NOT NULL COMMENT '触发生成的已开奖期号',
    sample_issues       INT          NOT NULL DEFAULT 0 COMMENT '抽样参考历史期数',
    set_count           INT          NOT NULL DEFAULT 5 COMMENT '模拟组数',
    algorithm_version   VARCHAR(64)  NOT NULL DEFAULT 'v2-uniform-iid' COMMENT '算法版本，便于后续升级',
    method              VARCHAR(128) DEFAULT NULL COMMENT '生成方法说明',
    fixed_red           VARCHAR(64)  DEFAULT NULL COMMENT '固定组前区',
    fixed_blue          VARCHAR(32)  DEFAULT NULL COMMENT '固定组后区',
    sets_json           TEXT         NOT NULL COMMENT '模拟组 JSON 数组',
    disclaimer          VARCHAR(512) DEFAULT NULL COMMENT '合规声明',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sim_code_issue (lottery_code, base_issue_no),
    KEY idx_sim_code_created (lottery_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开奖后自动模拟号码批次';
