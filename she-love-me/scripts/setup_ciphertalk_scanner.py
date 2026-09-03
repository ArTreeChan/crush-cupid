"""Download the pinned official CipherTalk key scanner components."""

import argparse
import hashlib
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path


UPSTREAM_REPOSITORY = "https://github.com/ILoveBingLu/CipherTalk"
UPSTREAM_TAG = "v2026.812.0"
RAW_PREFIX = f"https://raw.githubusercontent.com/ILoveBingLu/CipherTalk/{UPSTREAM_TAG}/"
COMPONENTS = {
    "wechat_key_tool.dll": {
        "path": "resources/wechat_key_tool.dll",
        "sha256": "7c40db880092f1b29b8e8895ff34ae2ae09bfe9ca7640fe9f40524fa3ff6597b",
    },
    "wxKeyService.ts": {
        "path": "electron/services/wxKeyService.ts",
        "sha256": "6ef76655ab1f19b0f78ea2eebaF943632990c15bbd9228f338a12ecee3b360cc".lower(),
    },
}


if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")


def sha256_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def component_url(component):
    return RAW_PREFIX + component["path"]


def download_component(name, component, output_dir):
    destination = Path(output_dir) / name
    destination.parent.mkdir(parents=True, exist_ok=True)
    expected = component["sha256"]
    if destination.is_file() and sha256_file(destination) == expected:
        return destination, False

    temporary = destination.with_suffix(destination.suffix + ".part")
    request = urllib.request.Request(
        component_url(component), headers={"User-Agent": "she-love-me"}
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response, open(temporary, "wb") as output:
            while chunk := response.read(1024 * 1024):
                output.write(chunk)
        actual = sha256_file(temporary)
        if actual != expected:
            raise RuntimeError(f"{name} SHA-256 校验失败")
        os.replace(temporary, destination)
    finally:
        if temporary.exists():
            temporary.unlink()
    return destination, True


def component_status(output_dir):
    files = {}
    ready = True
    for name, component in COMPONENTS.items():
        path = Path(output_dir) / name
        valid = path.is_file() and sha256_file(path) == component["sha256"]
        files[name] = {"path": str(path), "verified": valid}
        ready = ready and valid
    return files, ready


def main():
    parser = argparse.ArgumentParser(description="准备 CipherTalk 官方无界面密钥扫描组件")
    parser.add_argument("--download", action="store_true", help="下载缺失或校验失败的组件")
    parser.add_argument("--output-dir", default="vendor/ciphertalk/scanner")
    args = parser.parse_args()

    changed = False
    if args.download:
        for name, component in COMPONENTS.items():
            _, downloaded = download_component(name, component, args.output_dir)
            changed = changed or downloaded
    files, ready = component_status(args.output_dir)
    result = {
        "status": "ok" if ready else "missing",
        "ready": ready,
        "changed": changed,
        "source": UPSTREAM_REPOSITORY,
        "tag": UPSTREAM_TAG,
        "license": "CC-BY-NC-SA-4.0",
        "files": files,
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    if not ready:
        sys.exit(1)


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, urllib.error.URLError) as exc:
        print(json.dumps({"status": "error", "error": str(exc)}, ensure_ascii=False), file=sys.stderr)
        sys.exit(1)
