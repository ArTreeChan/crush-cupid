-- Cupid 数据库设计
-- PostgreSQL

-- ============================================
-- 1. crush — 暗恋对象
-- ============================================
CREATE TABLE IF NOT EXISTS crush (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    slug                VARCHAR(100) UNIQUE NOT NULL,

    mbti                VARCHAR(10),
    zodiac              VARCHAR(20),
    occupation          VARCHAR(100),
    gender              VARCHAR(20),
    know_duration       VARCHAR(50),
    relationship_status VARCHAR(50),
    impression          TEXT,

    persona_layer0      TEXT,
    persona_layer1      TEXT,
    persona_layer2      TEXT,
    persona_layer3      TEXT,
    persona_layer4      TEXT,

    memory_overview     TEXT,
    memory_timeline     JSONB,
    memory_sweet        TEXT,
    memory_interaction  TEXT,

    current_stage       SMALLINT DEFAULT 1,
    status              VARCHAR(20) DEFAULT 'DRAFT',
    total_messages      INT DEFAULT 0,
    last_chat_date      TIMESTAMPTZ,
    voice_id            VARCHAR(100),           -- CosyVoice 专属音色 voice_id（声音设计/复刻产生）
    voice_instruction   VARCHAR(200),           -- 语音风格指令（instruction）：控制情感、语气、语速、性格，最大100字符

    -- ===== 主动消息调度（LLM 决策 + 定时探测）=====
    proactive_enabled   BOOLEAN DEFAULT TRUE,  -- 是否允许 crush 主动发消息
    next_proactive_at   TIMESTAMPTZ,           -- 下次主动发言窗口开启时间（LLM 决策写入）
    last_proactive_at   TIMESTAMPTZ,           -- 上次主动发言时间（用于冷却）
    proactive_date      DATE,                  -- 主动计数归属日（用于每日上限）
    proactive_count     INT DEFAULT 0,         -- 当日主动发言次数

    version             INT DEFAULT 1,
    created_at          TIMESTAMPTZ DEFAULT NOW(),
    updated_at          TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================
-- 2. chat_source — 原材料
-- ============================================
CREATE TABLE IF NOT EXISTS chat_source (
    id              BIGSERIAL PRIMARY KEY,
    crush_id        BIGINT NOT NULL REFERENCES crush(id) ON DELETE CASCADE,
    file_name       VARCHAR(255),
    file_path       VARCHAR(500),
    file_type       VARCHAR(50),
    file_format     VARCHAR(20),
    content         TEXT,
    message_count   INT DEFAULT 0,
    raw_analysis    JSONB,
    parsed_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_source_crush ON chat_source(crush_id);

-- ============================================
-- 3. conversation — 对话记录
-- ============================================
CREATE TABLE IF NOT EXISTS conversation (
    id          BIGSERIAL PRIMARY KEY,
    crush_id    BIGINT NOT NULL REFERENCES crush(id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL,
    content     TEXT NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_conversation_crush ON conversation(crush_id, created_at);

-- ============================================
-- 3.1 chat_media — 对话图片/媒体 URL 独立存储
-- ============================================
-- 独立于 conversation 表，不设 FK 到 conversation(id)，
-- 因为 PgChatMemoryRepository.saveAll 采用「先清空再批量插入」覆盖语义，
-- FK 级联删除会导致图片记录丢失。改为按 crush_id + created_at 独立管理。
CREATE TABLE IF NOT EXISTS chat_media (
    id          BIGSERIAL PRIMARY KEY,
    crush_id    BIGINT NOT NULL REFERENCES crush(id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL DEFAULT 'user',
    media_url   TEXT NOT NULL,
    media_type  VARCHAR(50) DEFAULT 'image',
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_media_crush ON chat_media(crush_id, created_at);

-- ============================================
-- 4. crush_version — 版本快照
-- ============================================
CREATE TABLE IF NOT EXISTS crush_version (
    id          BIGSERIAL PRIMARY KEY,
    crush_id    BIGINT NOT NULL REFERENCES crush(id) ON DELETE CASCADE,
    version     INT NOT NULL,
    snapshot    JSONB NOT NULL,
    reason      VARCHAR(500),
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_version_crush ON crush_version(crush_id, version DESC);

-- ============================================
-- 5. crush_report — 关系进展报告（军师 LLM 生成，落库供历史管理）
-- ============================================
CREATE TABLE IF NOT EXISTS crush_report (
    id          BIGSERIAL PRIMARY KEY,
    crush_id    BIGINT NOT NULL REFERENCES crush(id) ON DELETE CASCADE,
    crush_name  VARCHAR(100),
    title       VARCHAR(255),              -- 报告标题（含日期，如「关系进展报告：小美」）
    markdown    TEXT NOT NULL,             -- 报告 Markdown 全文
    source      VARCHAR(20) DEFAULT 'manual',  -- 生成来源：manual(手动) / scheduled(定时)
    report_date DATE,                      -- 报告归属日期（用于每日去重）
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_crush_report_crush ON crush_report(crush_id, report_date DESC);

-- ============================================
-- 6. ai_provider — 自定义大模型 API 供应商（运行时增删改查，无需改配置/重启）
--    统一走 OpenAI 兼容协议；与 YAML 系统供应商合并后供 ChatModelRegistry 动态注册
--    capabilities: 逗号分隔的能力列表（vision=视觉看图, audio=音频输入听语音）
--    文本(text)是所有 LLM 基本能力，无需声明
-- ============================================
CREATE TABLE IF NOT EXISTS ai_provider (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,         -- 显示名，如「自定义 OpenAI」
    provider_key    VARCHAR(50) UNIQUE NOT NULL,   -- 供应商代号（路由 key）
    base_url        VARCHAR(500),                  -- OpenAI 兼容 base-url
    api_key         VARCHAR(500),                  -- API Key（允许空，走环境密钥）
    model           VARCHAR(100),                  -- 模型名
    temperature     DOUBLE PRECISION DEFAULT 0.7,
    top_p           DOUBLE PRECISION,
    max_tokens      INT,
    capabilities    VARCHAR(200) DEFAULT '',        -- 能力：逗号分隔 vision / audio（如 'vision', 'vision,audio'）
    type            VARCHAR(20) DEFAULT 'chat',      -- 供应商类型：chat=对话大模型 / voice=语音大模型
    voice           VARCHAR(200),                     -- 语音合成默认音色（仅 type=voice 时用，如 longyingling_v3）
    is_default      BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

