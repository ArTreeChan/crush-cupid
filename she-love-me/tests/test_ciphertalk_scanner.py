import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch


REPO_ROOT = Path(__file__).resolve().parents[1]
SCRIPTS_DIR = REPO_ROOT / "scripts"
sys.path.insert(0, str(SCRIPTS_DIR))

import run_ciphertalk_scanner
import setup_ciphertalk_scanner


class CipherTalkScannerSetupTests(unittest.TestCase):
    def test_components_are_pinned_to_official_source_and_hashes(self):
        self.assertEqual(setup_ciphertalk_scanner.UPSTREAM_TAG, "v2026.812.0")
        self.assertTrue(setup_ciphertalk_scanner.RAW_PREFIX.startswith(
            "https://raw.githubusercontent.com/ILoveBingLu/CipherTalk/"
        ))
        for component in setup_ciphertalk_scanner.COMPONENTS.values():
            self.assertRegex(component["sha256"], r"^[0-9a-f]{64}$")

    def test_component_status_rejects_tampered_files(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for name in setup_ciphertalk_scanner.COMPONENTS:
                (root / name).write_bytes(b"tampered")
            files, ready = setup_ciphertalk_scanner.component_status(root)
            self.assertFalse(ready)
            self.assertTrue(all(not item["verified"] for item in files.values()))


class CipherTalkScannerRunnerTests(unittest.TestCase):
    @patch.object(run_ciphertalk_scanner, "find_koffi")
    @patch.object(run_ciphertalk_scanner.shutil, "which")
    @patch.object(run_ciphertalk_scanner.subprocess, "run")
    def test_failure_preserves_safe_diagnostics(self, run, which, find_koffi):
        which.return_value = "C:/node.exe"
        find_koffi.return_value = Path("C:/koffi")
        run.return_value = Mock(
            returncode=1,
            stdout=json.dumps({
                "ok": False,
                "error": "未扫描到可验证的数据库密钥",
                "method": "contact-db-validated",
                "diagnostic": {"processCount": 5, "candidateCount": 139},
            }),
        )
        with self.assertRaises(run_ciphertalk_scanner.ScannerError) as raised:
            run_ciphertalk_scanner.run_scanner("C:/account", "C:/components")
        self.assertEqual(raised.exception.diagnostic["candidateCount"], 139)
        self.assertNotIn("key", json.dumps(raised.exception.diagnostic).lower())

    def test_helper_success_output_does_not_include_key_field(self):
        source = (SCRIPTS_DIR / "ciphertalk_key_helper.cjs").read_text(encoding="utf-8")
        success_line = next(line for line in source.splitlines() if "ok: true" in line)
        self.assertNotIn("key", success_line.lower())


if __name__ == "__main__":
    unittest.main()
