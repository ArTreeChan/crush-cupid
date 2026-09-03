"""Prepare the runtime dependency omitted by some CipherTalk desktop releases."""

import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

from ciphertalk_mcp_client import dependency_node_modules, find_launcher


SDK_VERSION = "1.27.1"


if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")


def sdk_entry(node_modules=None):
    root = Path(node_modules) if node_modules else dependency_node_modules()
    return root / "@modelcontextprotocol" / "sdk" / "dist" / "cjs" / "server" / "stdio.js"


def status(launcher=None, node_modules=None):
    try:
        launcher_path = find_launcher(launcher)
        launcher_ready = True
    except RuntimeError:
        launcher_path = None
        launcher_ready = False
    dependency_ready = sdk_entry(node_modules).is_file()
    return {
        "status": "ok",
        "launcher_ready": launcher_ready,
        "dependency_ready": dependency_ready,
        "ready": launcher_ready and dependency_ready,
        "launcher": str(launcher_path) if launcher_path else None,
        "sdk_version": SDK_VERSION,
    }


def install_dependency(prefix=None):
    npm = shutil.which("npm.cmd") or shutil.which("npm")
    if not npm:
        raise RuntimeError("未找到 npm；请先安装 Node.js 18+")
    install_root = Path(prefix) if prefix else dependency_node_modules().parent
    install_root.mkdir(parents=True, exist_ok=True)
    result = subprocess.run(
        [npm, "install", "--prefix", str(install_root), "--no-audit", "--no-fund",
         f"@modelcontextprotocol/sdk@{SDK_VERSION}"],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip() or "MCP SDK 安装失败")
    if not sdk_entry(install_root / "node_modules").is_file():
        raise RuntimeError("npm 返回成功，但未找到 CipherTalk MCP 所需 SDK")
    return install_root


def main():
    parser = argparse.ArgumentParser(description="准备 CipherTalk 桌面版 MCP 运行环境")
    parser.add_argument("--install", action="store_true", help="安装缺失的固定版本 MCP SDK")
    parser.add_argument("--launcher", help="可选：官方 ciphertalk-mcp.cmd 路径")
    args = parser.parse_args()
    changed = False
    before = status(args.launcher)
    if args.install and not before["dependency_ready"]:
        install_dependency()
        changed = True
    result = status(args.launcher)
    result["changed"] = changed
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError) as exc:
        print(json.dumps({"status": "error", "error": str(exc)}, ensure_ascii=False), file=sys.stderr)
        sys.exit(1)
