# she-love-me repository instructions

When the user asks to import or analyze WeChat/QQ chat history, read and follow `.agents/skills/she-love-me/SKILL.md` from the repository root.

For a new Windows WeChat user, execute the complete exporter workflow in `.agents/skills/she-love-me/references/data-sources.md`: check/install weflow-cli, initialize it, list sessions, export JSON, convert it, and continue through analysis/report generation. Fall back to CipherTalk CLI or its official desktop MCP automatically. Pause only for required user login, approval, token, or contact selection.

Keep sensitive inputs and generated outputs under `vendor/`, `data/`, or `reports/`. Never copy chat data into tracked files.
