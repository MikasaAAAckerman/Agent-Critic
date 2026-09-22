-- ============================================================
-- AgentCritic 标准表（MySQL，M1）
-- 启动时自动执行（spring.sql.init.mode=always），幂等
-- ============================================================

-- 评测样本表：一条 llm_call = 一个 EvalSample（M1 粒度）
CREATE TABLE IF NOT EXISTS eval_sample (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    source      VARCHAR(32)  NOT NULL COMMENT '来源（travel_hub / sillytavern）',
    call_id     BIGINT       COMMENT '源表 llm_call_log.id',
    trace_id    VARCHAR(64)  COMMENT '会话级 traceId',
    call_type   VARCHAR(32)  COMMENT 'PLANNER / WORKER / ROUTER ...',
    agent_name  VARCHAR(64)  COMMENT 'Agent 名称',
    input       TEXT         COMMENT '发给 LLM 的输入',
    output      TEXT         COMMENT 'LLM 的回复',
    extra       TEXT         COMMENT '附加元数据（JSON：耗时/token/成功等）',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    KEY idx_source (source),
    KEY idx_call_id (call_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='评测样本';

-- 增量同步水位线：记录每个来源已同步到的最大源 id
CREATE TABLE IF NOT EXISTS ingest_watermark (
    source     VARCHAR(32) PRIMARY KEY,
    last_id    BIGINT NOT NULL DEFAULT 0 COMMENT '已同步的最大源表 id',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='Ingestor 增量水位线';
