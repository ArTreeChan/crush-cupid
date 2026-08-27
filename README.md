# Cupid

> *"每一支射出的箭，都是一次未说出口的喜欢。"*

**把暗恋蒸馏成 AI 引擎 —— 通过 GitHub 远端的 Skill，生成一个真正像 ta 的智能 agent。**

[![Java 17](https://img.shields.io/badge/Java-17-blue.svg)](https://www.oracle.com/java/)
[![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-green.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.2-purple.svg)](https://spring.io/projects/spring-ai)
[![Spring AI Alibaba](https://img.shields.io/badge/Spring%20AI%20Alibaba-1.1.2.3-orange.svg)](https://java2.cn.alibaba.com/)
[![DeepSeek](https://img.shields.io/badge/DeepSeek-chat-orange.svg)](https://www.deepseek.com/)
[![通义千问](https://img.shields.io/badge/Qwen-DashScope-blue.svg)](https://dashscope.aliyun.com/)
[![Vue 3](https://img.shields.io/badge/Vue-3-brightgreen.svg)](https://vuejs.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[crush-skills](https://github.com/xiaoheizi8/crush-skills) 的 Java 服务端实现。它把「暗恋对象」蒸馏成可对话的 AI：从 GitHub 远端拉取 Skill 提示词，用 ta 的语气说话，记得你们之间的每一个心动瞬间。

⚠️ **本项目仅用于个人情感分析与回忆，不用于骚扰、跟踪或侵犯他人隐私。**

[它能做什么](#它能做什么) · [架构](#架构) · [设计模式](#设计模式) · [快速开始](#快速开始) · [配置](#配置) · [接口](#接口)

---

## 它能做什么

| 功能 | 说明 |
|------|------|
| Skill 远程调用 | 通过 GitHub raw URL 拉取 SKILL.md 元信息 + prompts 模板，本地 TTL 缓存 |
| 多供应商路由 | DeepSeek / 通义千问 / OpenAI / Qwen-Native（DashScope 原生）一键切换，按 `provider` 字段路由 |
| 多模态对话 | 支持图像 / 音频输入（qwen-vl / gpt-4o 等），自动校验供应商多模态能力 |
| 多条消息 | LLM 用 `\|\|\|` 分隔连发短消息，像真人微信一样「在吗？」「哈哈哈」「你猜」 |
| 主动消息 | crush 不依赖用户输入主动连发（「等 ta 主动找我」按钮），可带场景暗示 |
| 语音合成 | 接入 Spring AI Alibaba CosyVoice，每条回复可一键转 mp3 播放 |
| Persona 建模 | 5 层人格模型（硬规则 → 身份 → 说话风格 → 情感模式 → 关系行为） |
| 对话记忆入库 | 对话历史落库 PostgreSQL（自封装 `PgChatMemoryRepository`），跨刷新/重启续上下文 |
| 历史加载 | 进入对话页自动拉取该 crush 的历史消息渲染气泡 |
| 工具调用 | 封装 `@Tool`（列出暗恋对象 / 查画像 / 拉远端 prompt） |
| Advisor 封装 | 安全边界 / Persona / Memory 三个自定义 advisor 注入系统提示 |

---

## 架构

```
                    ┌─────────────────────────────────────────────────────┐
   Vue3 + Antd       │                  crush-cupid-server                 │
   (SSE 流式)  ─────▶│  controller ──▶ CupidAgent(Facade)                  │
                    │                   ├── ChatClientProvider             │
                    │                   │     └─ 路由 deepseek/qwen/openai │
                    │                   │        /qwen-native(Alibaba)     │
                    │                   ├── Advisors                       │
                    │                   │     ├─ SafetyAdvisor              │
                    │                   │     ├─ PersonaAdvisor             │
                    │                   │     └─ MessageChatMemoryAdvisor  │
                    │                   ├── Tools (@Tool)                  │
                    │                   ├── MessageSeparator (多条切分)     │
                    │                   └── PgChatMemoryRepository          │
                    │                         │ (基于 conversation 表)     │
                    │  VoiceService ── DashScopeAudioSpeechModel ──▶ mp3   │
                    │  skill/ ── GitHubRawSkillClient ──▶ GitHub raw       │
                    │        └─ CachingSkillResourceClient(TTL)             │
                    │  PostgreSQL  ◀── MyBatis-Plus                        │
                    └─────────────────────────────────────────────────────┘
```

**核心链路**：`SkillResourceClient`（Adapter）从 GitHub raw 拉取 `SKILL.md` + `prompts/*.md`，`CachingSkillResourceClient`（Decorator）加 TTL 缓存；`CupidAgent`（Facade）按 `provider` 路由到对应 `ChatClient`，编排 advisor + tool + memory，输出经 `MessageSeparator` 切成多条消息流式回传；`PgChatMemoryRepository` 把对话历史落库 PG。

---

## 设计模式

项目用 Adapter / Decorator（Skill 资源取用 + 缓存）、Facade（Agent 编排）、Strategy（多 LLM 供应商路由）、Template Method（advisor 注入提示骨架）、Factory（AI 装配）、DTO/VO 分层等常见模式组织代码，不在此逐一展开。

---

## 技术栈

```
Java 17 · Spring Boot 3.5 · Spring AI 1.1.2 · Spring AI Alibaba 1.1.2.3
DeepSeek / 通义千问(DashScope) / OpenAI · CosyVoice 语音合成
MyBatis-Plus · PostgreSQL · Hutool
Vue 3 + Ant Design Vue · Vite
```

---

## 快速开始

```bash
# 1. 后端（依赖：JDK17 + Maven + PostgreSQL）
export DEEPSEEK_API_KEY=sk-xxx        # 默认供应商 DeepSeek
export DASHSCOPE_API_KEY=sk-xxx       # 通义千问 + 语音合成（可选，缺省跳过该供应商）
cd crush-cupid-server
mvn -DskipTests spring-boot:run        # 启动在 http://localhost:91

# 2. 前端
cd crush-cupid-web
npm install
npm run dev                            # 打开 http://localhost:5173
```

> 前端通过 Vite 代理把 `/api` 转发到 `localhost:91`；后端也配了 CORS，跨域直连同样可用。

---

## 配置

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/crushCupid
    username: postgres
    password: 123456
  ai:
    # Alibaba DashScope：通义千问原生 + CosyVoice 语音
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-plus
      audio:
        speech:
          voice: longxiaochun          # CosyVoice 女声

# 多供应商路由（自封装，按 provider 字段切换）
crush:
  ai:
    default-provider: deepseek
    providers:
      deepseek:
        base-url: https://api.deepseek.com
        api-key: ${DEEPSEEK_API_KEY}
        model: deepseek-chat
      qwen:                            # OpenAI 兼容协议走通义
        base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
        api-key: ${DASHSCOPE_API_KEY}
        model: qwen-plus
      qwen-vl:                         # 多模态视觉
        base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
        api-key: ${DASHSCOPE_API_KEY}
        model: qwen-vl-plus
        multimodal: true
      openai:
        base-url: https://api.openai.com
        api-key: ${OPENAI_API_KEY}
        model: gpt-4o
        multimodal: true
  skill:
    # GitHub raw 基础地址，兼容多种写法（raw 目录 / github.com/tree / 指向 SKILL.md 的完整地址）
    base-url: https://raw.githubusercontent.com/xiaoheizi8/crush-skills/main
    cache-ttl: 3600                    # 本地缓存过期秒数
```

> `qwen-native` 供应商由 Spring AI Alibaba 自动注册（DashScope 原生协议），无需在此声明。缺 `api-key` 的供应商启动时自动跳过，不阻塞。

---

## 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 对话（SSE 流式，`{crushSlug, message, provider?, media?}`） |
| POST | `/api/chat/proactive` | 主动消息（SSE 流式，`{crushSlug, provider?, contextHint?}`） |
| POST | `/api/chat/voice` | 语音合成（`{text, voice?}` → `Result<String>` base64 mp3） |
| GET | `/api/chat/history?crushSlug=xxx` | 加载历史对话（`Result<List<ChatHistoryVO>>`） |
| GET | `/api/crush` | 列出暗恋对象 |
| POST | `/api/crush` | 新建暗恋对象 |
| PUT | `/api/crush/{id}` | 更新暗恋对象 |
| DELETE | `/api/crush/{id}` | 删除暗恋对象 |
| GET | `/api/skill/catalog` | 远端 Skill 元信息 + 可用 prompts |
| GET | `/api/skill/prompt/{name}` | 拉取指定 prompt 模板 |

**对话请求示例**：

```jsonc
// 纯文本
{ "crushSlug": "xiaomei", "message": "今天好累" }

// 切到千问
{ "crushSlug": "xiaomei", "message": "今天好累", "provider": "qwen" }

// 多模态（自动校验供应商 multimodal: true）
{
  "crushSlug": "xiaomei",
  "message": "这张图让我想到你",
  "provider": "qwen-vl",
  "media": [{ "type": "IMAGE_URL", "mimeType": "image/jpeg", "data": "https://..." }]
}
```

**SSE 多条消息协议**：后端按 `index` 切气泡，每条 `{index, content, done}`，前端按 index 跳变即新气泡。

---

## 项目结构

```
crush-cupid-server/                      crush-cupid-web/
├── src/main/java/cn/yzfy/crushcupidserver/
│   ├── agent/        # CupidAgent 门面 + Tools + Advisors + MessageSeparator + VoiceService
│   ├── skill/        # GitHub 远端 Skill 调用（Adapter + Decorator）
│   ├── model/        # entity / dto / vo / mapper / service / converter
│   ├── controller/   # REST + SSE 接口
│   ├── config/       # AiConfig / LlmProperties / ChatModelRegistry / ChatClientProvider / PgChatMemoryRepository
│   ├── common/       # 统一返回结构 Result<T>
│   └── exception/    # 全局异常处理
└── src/main/resources/
    └── application.yml
```

---

## 注意事项

- **DeepSeek Function Calling 不稳定**：架构以 advisor 注入 skill prompt 为主链路，工具调用只作可选增强，happy path 不依赖模型调工具。
- **本机到 GitHub raw 仅 IPv6 可达**：`spring-boot-maven-plugin` 里已配 `-Djava.net.preferIPv6Addresses=true`，用别的启动方式时需自行带上。
- **Spring AI 版本对齐**：项目用 Spring AI 1.1.2（而非最新 1.1.8），是为了对齐 Spring AI Alibaba 1.1.2.3 的硬依赖；所用 API 均为 1.1 GA 起即有，降级安全。
- **多模态 Media 不入库**：`PgChatMemoryRepository` 只存 `message.getText()` 纯文本，图像/音频 Media 下次对话需重发；历史记忆主要靠文本上下文。

---

## 相关项目

- [crush-skills](https://github.com/xiaoheizi8/crush-skills) — Skill 资源仓库（SKILL.md + Prompt 模板 + Python 工具）

## License

MIT
## Star History

[![RepoStars](https://repostars.dev/api/embed?repo=xiaoheizi8/crush-skills)](https://repostars.dev/?repos=xiaoheizi8/crush-skills)