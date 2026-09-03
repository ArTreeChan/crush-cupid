import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]


class AgentEntrypointTests(unittest.TestCase):
    def test_claude_skill_copy_matches_authoritative_skill(self):
        authoritative = REPO_ROOT / ".agents" / "skills" / "she-love-me" / "SKILL.md"
        claude_copy = REPO_ROOT / ".claude" / "skills" / "she-love-me" / "SKILL.md"
        self.assertEqual(
            authoritative.read_text(encoding="utf-8"),
            claude_copy.read_text(encoding="utf-8"),
        )

    def test_cross_agent_entry_files_exist(self):
        expected = (
            "AGENTS.md",
            "CLAUDE.md",
            "GEMINI.md",
            ".github/copilot-instructions.md",
            ".cursor/rules/she-love-me.mdc",
            ".agents/skills/she-love-me/agents/openai.yaml",
            ".agents/skills/she-love-me/references/data-sources.md",
        )
        for relative in expected:
            with self.subTest(relative=relative):
                self.assertTrue((REPO_ROOT / relative).is_file())

    def test_data_source_reference_requires_end_to_end_agent_execution(self):
        text = (REPO_ROOT / ".agents" / "skills" / "she-love-me" /
                "references" / "data-sources.md").read_text(encoding="utf-8")
        self.assertIn("Agent 应主动执行环境检查、安装、初始化、导出和转换命令", text)
        self.assertIn("weflow-cli", text)
        self.assertIn("CipherTalk", text)
        self.assertIn("diagnose_ciphertalk.py", text)
        self.assertIn("--scan-key --download-scanner", text)
        self.assertIn("setup_ciphertalk_desktop.py --download", text)
        self.assertIn("setup_ciphertalk_mcp.py --install", text)
        self.assertIn("list_ciphertalk_sessions_mcp.py --limit 30", text)
        self.assertIn("export_ciphertalk_mcp.py", text)
        self.assertIn("禁止用 MCP `get_messages` 的 offset 分页", text)


if __name__ == "__main__":
    unittest.main()
