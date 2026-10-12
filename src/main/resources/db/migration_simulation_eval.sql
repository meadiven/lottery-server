-- 模拟批次 vs 实开对照评估
CREATE TABLE IF NOT EXISTS lottery_simulation_eval (
    id                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    lottery_code        VARCHAR(32)   NOT NULL COMMENT '彩种编码',
    issue_no            VARCHAR(32)   NOT NULL COMMENT '期号（与模拟 base_issue_no / 开奖 issue_no 对齐）',
    algorithm_version   VARCHAR(64)   NOT NULL COMMENT '被评估的算法版本',
    set_scores_json     TEXT          NOT NULL COMMENT '每组命中 JSON',
    batch_score         DECIMAL(10,4) NOT NULL COMMENT '批次得分 max前区+0.5*max后区',
    fixed_front_hits    INT           NOT NULL DEFAULT 0 COMMENT '固定组前区命中',
    fixed_back_hits     INT           NOT NULL DEFAULT 0 COMMENT '固定组后区命中',
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sim_eval (lottery_code, issue_no, algorithm_version),
    KEY idx_sim_eval_code_issue (lottery_code, issue_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模拟与实开对照评估';
