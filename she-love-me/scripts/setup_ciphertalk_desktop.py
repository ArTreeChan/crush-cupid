"""Resolve and download the official CipherTalk Windows desktop release."""

import argparse
import hashlib
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path


API_URL = "https://api.github.com/repos/ILoveBingLu/CipherTalk/releases/latest"
OFFICIAL_PREFIX = "https://github.com/ILoveBingLu/CipherTalk/releases/download/"


if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")


def request_json(url=API_URL):
    request = urllib.request.Request(
        url,
        headers={"Accept": "application/vnd.github+json", "User-Agent": "she-love-me"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def select_windows_asset(release):
    assets = release.get("assets") or []
    matches = [
        asset for asset in assets
        if str(asset.get("name", "")).lower().endswith("-setup.exe")
    ]
    if len(matches) != 1:
        raise RuntimeError(f"官方 Release 中 Windows Setup.exe 数量异常：{len(matches)}")
    asset = matches[0]
    url = str(asset.get("browser_download_url") or "")
    digest = str(asset.get("digest") or "")
    if not url.startswith(OFFICIAL_PREFIX):
        raise RuntimeError("拒绝非 CipherTalk 官方 GitHub Release 下载地址")
    if not digest.startswith("sha256:") or len(digest) != 71:
        raise RuntimeError("官方 Release 未提供有效 SHA-256")
    return {
        "tag": release.get("tag_name"),
        "name": asset["name"],
        "url": url,
        "size": asset.get("size"),
        "sha256": digest.split(":", 1)[1].lower(),
    }


def sha256_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def download_asset(asset, output_dir):
    destination = Path(output_dir) / asset["name"]
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.is_file() and sha256_file(destination) == asset["sha256"]:
        return destination, False
    temporary = destination.with_suffix(destination.suffix + ".part")
    request = urllib.request.Request(asset["url"], headers={"User-Agent": "she-love-me"})
    try:
        with urllib.request.urlopen(request, timeout=60) as response, open(temporary, "wb") as output:
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                output.write(chunk)
        actual = sha256_file(temporary)
        if actual != asset["sha256"]:
            raise RuntimeError(f"SHA-256 校验失败：expected={asset['sha256']} actual={actual}")
        os.replace(temporary, destination)
    finally:
        if temporary.exists():
            temporary.unlink()
    return destination, True


def main():
    parser = argparse.ArgumentParser(description="获取 CipherTalk 官方 Windows 桌面版")
    parser.add_argument("--download", action="store_true", help="下载并校验官方安装包")
    parser.add_argument("--output-dir", default="vendor/ciphertalk", help="安装包保存目录")
    args = parser.parse_args()

    asset = select_windows_asset(request_json())
    result = {"status": "ok", "source": "official-github-release", **asset}
    if args.download:
        path, changed = download_asset(asset, args.output_dir)
        result.update({"path": str(path), "downloaded": changed, "verified": True})
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, urllib.error.URLError, json.JSONDecodeError) as exc:
        print(json.dumps({"status": "error", "error": str(exc)}, ensure_ascii=False), file=sys.stderr)
        sys.exit(1)
