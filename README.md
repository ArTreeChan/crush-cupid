# Cupid

> *"每一支射出的箭，都是一次未说出口的喜欢。"*

**把暗恋蒸馏成 AI 引擎 —— 通过 GitHub 远端的 Skill，生成一个真正像 ta 的智能 agent。**

[![Java 17](https://img.shields.io/badge/Java-17-blue.svg)](https://www.oracle.com/java/)
[![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-green.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.8-purple.svg)](https://spring.io/projects/spring-ai)
[![DeepSeek](https://img.shields.io/badge/DeepSeek-chat-orange.svg)](https://www.deepseek.com/)
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
| 智能对话 | DeepSeek 流式对话，像 ta 一样回复，SSE 实时输出 |
| Persona 建模 | 5 层人格模型（硬规则 → 身份 → 说话风格 → 情感模式 → 关系行为） |
| 关系记忆 | 对话历史落库 PostgreSQL，跨会话记住你们的互动 |
| 工具调用 | 封装 `@Tool`（列出暗恋对象 / 查画像 / 拉远端 prompt） |
| Advisor 封装 | 安全边界 / Persona / Memory 三个自定义 advisor 注入系统提示 |

---

## 架构

```
                    ┌─────────────────────────────────────────────┐
   Vue3 + Antd       │                crush-cupid-server           │
   (SSE 流式)  ─────▶│  controller ──▶ CupidAgent(Facade)          │
                    │                   ├── ChatClient (DeepSeek)   │
                    │                   ├── Advisors                │
                    │                   │     ├─ SafetyAdvisor      │
                    │                   │     ├─ PersonaAdvisor     │
                    │                   │     └─ MemoryAdvisor      │
                    │                   ├── Tools (@Tool)           │
                    │                   └── PostgresChatMemory      │
                    │                         │                     │
                    │  skill/ ── GitHubRawSkillClient ──▶ GitHub raw │
                    │        └─ CachingSkillResourceClient(TTL)     │
                    │                                             │
                    │  PostgreSQL  ◀── MyBatis-Plus                │
                    └─────────────────────────────────────────────┘
```

**核心链路**：`SkillResourceClient`（Adapter）从 GitHub raw 拉取 `SKILL.md` + `prompts/*.md`，`CachingSkillResourceClient`（Decorator）加 TTL 缓存；`CupidAgent`（Facade）编排 `ChatClient` + 三个自定义 advisor + `@Tool` 工具；`PostgresChatMemory` 把对话历史落库。

---

## 设计模式

| 模式 | 落地位置 |
|------|----------|
| Adapter | `SkillResourceClient` + `GitHubRawSkillClient`，把 GitHub raw 适配成统一取资源接口 |
| Decorator | `CachingSkillResourceClient` 包一层 TTL 内存缓存 |
| Strategy | `PromptResolver` + `TemplatePromptResolver`，模板占位符解析 |
| Factory | `AiConfig` 用 `@Bean` 组装 ChatClient / advisor / 工具 |
| Facade | `CupidAgent.chat(...)` 屏蔽工具注册、advisor、记忆、持久化细节 |
| Template Method | `AbstractPromptAdvisor` 定义「注入系统提示」骨架，子类只提供文本与优先级 |
| DTO / VO 分层 | 入参 `model/dto`、出参 `model/vo`、`converter` 单向映射 |

---

## 技术栈

```
Java 17 · Spring Boot 3.5 · Spring AI 1.1.8 · DeepSeek(OpenAI 兼容)
MyBatis-Plus · PostgreSQL · Hutool · Vue 3 + Ant Design Vue · Vite
```

---

## 快速开始

```bash
# 1. 后端（依赖：JDK17 + Maven + PostgreSQL）
export DEEPSEEK_API_KEY=sk-xxx
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
    openai:
      api-key: ${DEEPSEEK_API_KEY}          # DeepSeek 官方 Key
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-chat

crush:
  skill:
    # GitHub raw 基础地址，兼容多种写法（raw 目录 / github.com/tree / 指向 SKILL.md 的完整地址）
    base-url: https://raw.githubusercontent.com/xiaoheizi8/crush-skills/main
    cache-ttl: 3600                          # 本地缓存过期秒数
```

---

## 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 对话（SSE 流式，`{crushSlug, message}`） |
| GET | `/api/crush` | 列出暗恋对象 |
| POST | `/api/crush` | 新建暗恋对象 |
| PUT | `/api/crush/{id}` | 更新暗恋对象 |
| DELETE | `/api/crush/{id}` | 删除暗恋对象 |
| GET | `/api/skill/catalog` | 远端 Skill 元信息 + 可用 prompts |
| GET | `/api/skill/prompt/{name}` | 拉取指定 prompt 模板 |

---

## 项目结构

```
crush-cupid-server/                      crush-cupid-web/
├── src/main/java/cn/yzfy/crushcupidserver/
│   ├── agent/        # 智能 agent 门面 + tools + advisors + memory
│   ├── skill/        # GitHub 远端 Skill 调用（Adapter + Decorator）
│   ├── model/        # entity / dto / vo / mapper / service / converter
│   ├── controller/   # REST + SSE 接口
│   ├── config/       # AI / MyBatis-Plus / CORS 装配
│   ├── common/       # 统一返回结构
│   └── exception/    # 全局异常处理
└── src/main/resources/
    └── application.yml
```

---

## 注意事项

- **DeepSeek Function Calling 不稳定**：架构以 advisor 注入 skill prompt 为主链路，工具调用只作可选增强，happy path 不依赖模型调工具。
- **本机到 GitHub raw 仅 IPv6 可达**：`spring-boot-maven-plugin` 里已配 `-Djava.net.preferIPv6Addresses=true`，用别的启动方式时需自行带上。

---

## 相关项目

- [crush-skills](https://github.com/xiaoheizi8/crush-skills) — Skill 资源仓库（SKILL.md + Prompt 模板 + Python 工具）

## License

MIT
