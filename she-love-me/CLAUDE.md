# CLAUDE.md

本仓库提供 `she-love-me` 聊天关系分析技能。

## 唯一工作流

1. 始终在仓库根目录工作。
2. 读取并严格执行 `.agents/skills/she-love-me/SKILL.md`。
3. 数据获取与第三方导出工具的完整流程位于 `.agents/skills/she-love-me/references/data-sources.md`。
4. Windows 微信新用户由 Agent 端到端完成导出工具检查、安装、初始化、会话选择、JSON 导出和转换；首选 weflow-cli，失败依次回退 CipherTalk CLI 和官方桌面 MCP。
5. 只有登录/扫码、管理员或联网安装授权、联系人选择等必须由用户完成的环节才暂停。
6. 私密数据只能放在 `vendor/`、`data/`、`reports/`；联系人输出使用 `--output-dir data/contacts`。

Claude Code 可使用 `/she-love-me` 触发。`.claude/settings.json` 已注册统一技能目录；`.claude/skills/she-love-me/SKILL.md` 是兼容副本，必须与 `.agents/skills/she-love-me/SKILL.md` 保持完全一致。
