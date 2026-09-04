-- ============================================================
-- 微信小程序用户表（增量脚本，可单独执行）
-- ============================================================
USE lottery_db;

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
