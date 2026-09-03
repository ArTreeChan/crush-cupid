import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


REPO_ROOT = Path(__file__).resolve().parents[1]
SCRIPTS_DIR = REPO_ROOT / "scripts"
sys.path.insert(0, str(SCRIPTS_DIR))

import ciphertalk_mcp_client
import list_ciphertalk_sessions_mcp
import setup_ciphertalk_mcp


class CipherTalkMcpClientTests(unittest.TestCase):
    def test_uses_json_lines_framing(self):
        encoded = ciphertalk_mcp_client.encode_message({"jsonrpc": "2.0", "id": 1})
        self.assertTrue(encoded.endswith(b"\n"))
        self.assertNotIn(b"Content-Length", encoded)
        self.assertEqual(ciphertalk_mcp_client.decode_messages(encoded)[0]["id"], 1)

    def test_explicit_launcher_does_not_require_standard_install_path(self):
        with tempfile.TemporaryDirectory() as temporary:
            launcher = Path(temporary) / "ciphertalk-mcp.cmd"
            launcher.write_text("@echo off\n", encoding="utf-8")
            self.assertEqual(
                ciphertalk_mcp_client.find_launcher(launcher), launcher.resolve()
            )

    def test_missing_explicit_launcher_is_rejected(self):
        with self.assertRaises(RuntimeError):
            ciphertalk_mcp_client.find_launcher("missing-ciphertalk-mcp.cmd")

    @unittest.skipUnless(sys.platform == "win32", "Windows registry path behavior")
    def test_registry_command_path_parsing(self):
        path = ciphertalk_mcp_client.executable_from_registry_value(
            '"X:\\Apps\\CipherTalk\\Uninstall CipherTalk.exe" /currentuser'
        )
        self.assertEqual(path, Path("X:/Apps/CipherTalk/Uninstall CipherTalk.exe"))

    @patch.object(ciphertalk_mcp_client, "registry_launcher_candidates")
    @patch.object(ciphertalk_mcp_client.shutil, "which")
    def test_launcher_candidates_include_registry_install(self, which, registry_candidates):
        which.return_value = None
        registry_path = Path("X:/Apps/CipherTalk/ciphertalk-mcp.cmd")
        registry_candidates.return_value = [registry_path]
        with patch.dict(os.environ, {}, clear=True):
            self.assertIn(registry_path, ciphertalk_mcp_client.launcher_candidates())


class CipherTalkMcpSetupTests(unittest.TestCase):
    def test_sdk_entry_is_detected_under_node_modules(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            entry = setup_ciphertalk_mcp.sdk_entry(root)
            entry.parent.mkdir(parents=True)
            entry.write_text("module.exports = {}", encoding="utf-8")
            self.assertTrue(setup_ciphertalk_mcp.sdk_entry(root).is_file())

    @patch.object(setup_ciphertalk_mcp, "find_launcher")
    def test_status_does_not_expose_config_or_account_data(self, find_launcher):
        with tempfile.TemporaryDirectory() as temporary:
            launcher = Path(temporary) / "ciphertalk-mcp.cmd"
            launcher.write_text("@echo off\n", encoding="utf-8")
            find_launcher.return_value = launcher
            result = setup_ciphertalk_mcp.status(node_modules=Path(temporary) / "modules")
        self.assertEqual(
            set(result),
            {"status", "launcher_ready", "dependency_ready", "ready", "launcher", "sdk_version"},
        )


class CipherTalkSessionListTests(unittest.TestCase):
    def test_public_sessions_drop_message_preview_and_unread_count(self):
        result = list_ciphertalk_sessions_mcp.public_sessions({"items": [{
            "displayName": "example",
            "sessionId": "example-id",
            "kind": "friend",
            "lastMessagePreview": "private message",
            "unreadCount": 3,
        }]})
        self.assertEqual(result, [{
            "displayName": "example", "sessionId": "example-id", "kind": "friend",
        }])


if __name__ == "__main__":
    unittest.main()
