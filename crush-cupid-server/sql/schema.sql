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
