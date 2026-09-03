import sys
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
SCRIPTS_DIR = REPO_ROOT / "scripts"
sys.path.insert(0, str(SCRIPTS_DIR))

import setup_ciphertalk_desktop


class CipherTalkDesktopSetupTests(unittest.TestCase):
    def test_selects_official_windows_asset(self):
        release = {"tag_name": "v1", "assets": [{
            "name": "CipherTalk-1-Setup.exe",
            "browser_download_url": (
                "https://github.com/ILoveBingLu/CipherTalk/releases/download/v1/"
                "CipherTalk-1-Setup.exe"
            ),
            "size": 100,
            "digest": "sha256:" + "a" * 64,
        }]}
        asset = setup_ciphertalk_desktop.select_windows_asset(release)
        self.assertEqual(asset["sha256"], "a" * 64)

    def test_rejects_unofficial_asset_url(self):
        release = {"assets": [{
            "name": "CipherTalk-1-Setup.exe",
            "browser_download_url": "https://example.com/CipherTalk-1-Setup.exe",
            "digest": "sha256:" + "a" * 64,
        }]}
        with self.assertRaises(RuntimeError):
            setup_ciphertalk_desktop.select_windows_asset(release)


if __name__ == "__main__":
    unittest.main()
