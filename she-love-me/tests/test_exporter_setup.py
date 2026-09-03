import sys
import unittest
from pathlib import Path
from unittest.mock import patch


REPO_ROOT = Path(__file__).resolve().parents[1]
SCRIPTS_DIR = REPO_ROOT / "scripts"
sys.path.insert(0, str(SCRIPTS_DIR))

import setup_chat_exporter


class ExporterSetupTests(unittest.TestCase):
    def test_provider_catalog_has_expected_commands(self):
        self.assertEqual(setup_chat_exporter.PROVIDERS["weflow-cli"]["command"], "weflow-cli")
        self.assertEqual(setup_chat_exporter.PROVIDERS["ciphertalk"]["command"], "miyu")

    @patch.object(setup_chat_exporter, "command_path")
    def test_status_reports_missing_runtime_without_installing(self, command_path):
        command_path.return_value = None
        status = setup_chat_exporter.provider_status("weflow-cli")
        self.assertFalse(status["ready"])
        self.assertFalse(status["installed"])
        self.assertFalse(status["node_supported"])

    def test_unknown_provider_is_not_in_catalog(self):
        self.assertNotIn("unknown", setup_chat_exporter.PROVIDERS)

    @patch.object(setup_chat_exporter, "run")
    def test_node_version_parses_semver(self, run):
        run.return_value.stdout = "v22.12.0\n"
        parsed, display = setup_chat_exporter.node_version("node")
        self.assertEqual(parsed, (22, 12, 0))
        self.assertEqual(display, "22.12.0")

    @patch.object(setup_chat_exporter, "install_provider")
    @patch.object(setup_chat_exporter, "provider_status")
    def test_auto_install_falls_back_to_ciphertalk(self, provider_status, install_provider):
        provider_status.side_effect = [
            {"provider": "weflow-cli", "ready": False},
            {"provider": "ciphertalk", "ready": False},
        ]
        install_provider.side_effect = [
            RuntimeError("node-gyp failed"),
            {"provider": "ciphertalk", "ready": True},
        ]

        result = setup_chat_exporter.setup_automatic(install=True)

        self.assertEqual(result["provider"], "ciphertalk")
        self.assertEqual(result["attempts"][0]["status"], "error")
        self.assertTrue(result["changed"])

    @patch.object(setup_chat_exporter, "provider_status")
    def test_auto_check_uses_existing_provider(self, provider_status):
        provider_status.return_value = {"provider": "weflow-cli", "ready": True}

        result = setup_chat_exporter.setup_automatic(install=False)

        self.assertEqual(result["provider"], "weflow-cli")
        self.assertFalse(result["changed"])
        self.assertNotIn("attempts", result["attempts"][0])


if __name__ == "__main__":
    unittest.main()
