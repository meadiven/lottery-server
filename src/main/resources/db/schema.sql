-- ============================================================
-- 彩票开奖数据采集库（仅用于官方公开公示数据的学习与统计研究）
-- MySQL 8.0+
-- ============================================================

CREATE DATABASE IF NOT EXISTS lottery_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE lottery_db;

-- ------------------------------------------------------------
-- 1. 彩种基础表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS lottery_prize_detail;
DROP TABLE IF EXISTS lottery_draw;
DROP TABLE IF EXISTS collect_log;
DROP TABLE IF EXISTS lottery_type;

CREATE TABLE lottery_type (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code            VARCHAR(32)  NOT NULL COMMENT '彩种编码：SSQ/DLT/PL3/PL5/QLC/FC3D',
    name            VARCHAR(64)  NOT NULL COMMENT '彩种名称',
    category        VARCHAR(16)  NOT NULL COMMENT '类别：WELFARE福彩 / SPORT体彩',
    issuer          VARCHAR(64)  DEFAULT NULL COMMENT '发行机构',
    description     VARCHAR(512) DEFAULT NULL COMMENT '规则简述（公开信息）',
    enabled         TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用采集：1是 0否',
    sort_order      INT          NOT NULL DEFAULT 0 COMMENT '排序',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lottery_type_code (code),
    KEY idx_lottery_type_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='彩种基础表';

-- ------------------------------------------------------------
-- 2. 开奖结果主表
-- ------------------------------------------------------------
CREATE TABLE lottery_draw (
    id              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    lottery_code    VARCHAR(32)    NOT NULL COMMENT '彩种编码',
    issue_no        VARCHAR(32)    NOT NULL COMMENT '开奖期号',
    draw_time       DATETIME       DEFAULT NULL COMMENT '开奖时间',
    draw_date       DATE           DEFAULT NULL COMMENT '开奖日期',
    red_balls       VARCHAR(64)    DEFAULT NULL COMMENT '红球/前区号码，空格或逗号分隔',
    blue_balls      VARCHAR(32)    DEFAULT NULL COMMENT '蓝球/后区号码',
    draw_numbers    VARCHAR(128)   NOT NULL COMMENT '完整开奖号码（规范化展示）',
    sales_amount    DECIMAL(18, 2) DEFAULT NULL COMMENT '销售额（元）',
    pool_amount     DECIMAL(18, 2) DEFAULT NULL COMMENT '奖池金额（元）',
    raw_payload     JSON           DEFAULT NULL COMMENT '原始接口报文（审计/回溯）',
    source          VARCHAR(64)    DEFAULT NULL COMMENT '数据来源标识',
    content_hash    VARCHAR(64)    NOT NULL COMMENT '内容哈希，用于去重',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_draw_code_issue (lottery_code, issue_no),
    KEY idx_draw_code_date (lottery_code, draw_date),
    KEY idx_draw_time (draw_time),
    KEY idx_draw_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开奖结果主表';

-- ------------------------------------------------------------
-- 3. 奖项明细分表
-- ------------------------------------------------------------
CREATE TABLE lottery_prize_detail (
    id              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    draw_id         BIGINT         NOT NULL COMMENT '开奖结果主表ID',
    lottery_code    VARCHAR(32)    NOT NULL COMMENT '彩种编码（冗余便于查询）',
    issue_no        VARCHAR(32)    NOT NULL COMMENT '期号（冗余）',
    prize_level     VARCHAR(32)    NOT NULL COMMENT '奖级名称，如一等奖',
    prize_level_no  INT            DEFAULT NULL COMMENT '奖级序号，便于排序',
    winner_count    INT            DEFAULT NULL COMMENT '中奖注数',
    prize_amount    DECIMAL(18, 2) DEFAULT NULL COMMENT '单注奖金（元）',
    total_amount    DECIMAL(18, 2) DEFAULT NULL COMMENT '该奖级奖金合计（元）',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    PRIMARY KEY (id),
    KEY idx_prize_draw_id (draw_id),
    KEY idx_prize_code_issue (lottery_code, issue_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开奖奖项明细分表';

-- ------------------------------------------------------------
-- 4. 采集任务日志表
-- ------------------------------------------------------------
CREATE TABLE collect_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    lottery_code    VARCHAR(32)  DEFAULT NULL COMMENT '彩种编码，空表示整轮任务',
    task_batch_no   VARCHAR(64)  NOT NULL COMMENT '任务批次号',
    status          VARCHAR(16)  NOT NULL COMMENT 'SUCCESS / FAIL / SKIP',
    message         VARCHAR(1024) DEFAULT NULL COMMENT '结果说明',
    http_status     INT          DEFAULT NULL COMMENT 'HTTP状态码',
    cost_ms         BIGINT       DEFAULT NULL COMMENT '耗时毫秒',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_collect_batch (task_batch_no),
    KEY idx_collect_code_time (lottery_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采集任务日志表';

-- ------------------------------------------------------------
-- 初始化彩种数据
-- ------------------------------------------------------------
INSERT INTO lottery_type (code, name, category, issuer, description, enabled, sort_order) VALUES
('DLT',  '超级大乐透', 'SPORT', '国家体育总局体育彩票管理中心', '前区5个+后区2个，官方公开开奖公示', 1, 1),
('SSQ',  '双色球', 'WELFARE', '中国福利彩票发行管理中心', '红球6个+蓝球1个，官方公开开奖公示', 1, 2),
('QLC',  '七乐彩', 'WELFARE', '中国福利彩票发行管理中心', '基本号7个+特别号1个，官方公开开奖公示', 1, 3),
('FC3D', '福彩3D', 'WELFARE', '中国福利彩票发行管理中心', '三位数字开奖公示', 1, 4),
('PL3',  '排列3', 'SPORT', '国家体育总局体育彩票管理中心', '三位数字开奖公示', 1, 5),
('PL5',  '排列5', 'SPORT', '国家体育总局体育彩票管理中心', '五位数字开奖公示', 1, 6);

-- ------------------------------------------------------------
-- 5. 微信小程序用户表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wx_mini_user (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    openid          VARCHAR(64)  NOT NULL COMMENT '微信 openid',
    unionid         VARCHAR(64)  DEFAULT NULL COMMENT '微信 unionid（未绑定开放平台时为空）',
    session_key     VARCHAR(128) DEFAULT NULL COMMENT '会话密钥（仅服务端保存，勿下发）',
    nickname        VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    avatar_url      VARCHAR(512) DEFAULT NULL COMMENT '头像 URL',
    gender          TINYINT      DEFAULT 0 COMMENT '性别：0未知 1男 2女',
    phone           VARCHAR(20)  DEFAULT NULL COMMENT '手机号（如后续授权获取）',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
    last_login_at   DATETIME     DEFAULT NULL COMMENT '最近登录时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wx_openid (openid),
    KEY idx_wx_unionid (unionid),
    KEY idx_wx_last_login (last_login_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='微信小程序用户表';

-- ------------------------------------------------------------
-- 6. 开奖后自动模拟批次（当前仅大乐透）
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 7. 模拟与实开对照评估
-- ------------------------------------------------------------
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
