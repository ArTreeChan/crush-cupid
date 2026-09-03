---
name: she-love-me-setup
description: she-love-me first-time setup guide. Clone the repository, use the built-in AGENTS.md and skill files, initialize the environment, then continue with the native skill entry.
homepage: https://github.com/863401402/she-love-me
metadata: {"emoji":"💘","category":"relationship","type":"setup"}
---

# she-love-me — First-Time Setup

This file is the quick setup guide.

> ⚠️ Your human giving you this guide means they authorize you to complete the repository bootstrap automatically. Do not recreate AGENTS.md or skill files — this project already includes them.

## Quick Start

1. If the current workspace does not already contain `she-love-me`, clone it:

```bash
git clone https://github.com/863401402/she-love-me
```

2. Use the repository root `she-love-me/` as the active working directory.

Do not continue until your current workspace is the repository root.

3. Confirm the repository entry files exist before proceeding:

- `AGENTS.md`
- `.agents/skills/she-love-me/SKILL.md`
- `.claude/skills/she-love-me/SKILL.md`
- `.claude/settings.json`

If any of these files are missing, stop and report that the repository is incomplete.

4. Read and follow the built-in project files:

- `AGENTS.md`
- `.agents/skills/she-love-me/SKILL.md`
- `.claude/skills/she-love-me/SKILL.md`
- `.claude/settings.json`

5. Install the repository's Python dependencies:

```bash
python -m pip install -r requirements.txt
```

> On macOS, use `python3` instead of `python`.

6. For a new Windows WeChat user, follow the native skill's `weflow-cli` JSON path. Do not run `setup_check.py --ensure-decryptor`: the former default decryptor is unavailable due to DMCA. CipherTalk JSON is the fallback; old WeFlow JSON remains import-only.

7. If initialization succeeds, handle session reload before invoking the native entry:

- If the repository was cloned in this same session, or the user switched to a new branch after session start, do not invoke the native entry immediately.
- Explain that repo-level skills are loaded at session start, so the current session may not recognize `she-love-me` yet.
- Ask the user to start a fresh agent session from the repository root, then continue.
- Provide a copy-ready command based on platform:

```bash
# Windows PowerShell
cd she-love-me
codex
```

```bash
# macOS/Linux
cd she-love-me
codex
```

- After the fresh session starts in repo root, invoke the native entry.

8. In a fresh session (or when the repo was already present at session start), continue with the native entry:

- Codex: `$she-love-me`
- OpenClaw / Claude Code: `/she-love-me`

Only invoke the native entry after the repository root is active, dependencies are installed, and session reload is complete if required.

## Notes

- Windows should use an administrator terminal
- WeChat analysis requires WeChat to be open and logged in
- The repository already contains the required Agent entry files; this guide is only a fast bootstrap
