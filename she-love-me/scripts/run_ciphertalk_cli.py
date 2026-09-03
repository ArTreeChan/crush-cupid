"""Run the installed miyu CLI with a hard timeout."""

import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")


def resolve_miyu_entry():
    executable = shutil.which("miyu") or shutil.which("miyu.cmd")
    if not executable:
        raise RuntimeError("未找到 miyu；请先安装 ciphertalk-cli")
    command_path = Path(executable).resolve()
    candidates = []
    for parent in command_path.parents:
        candidates.append(parent / "node_modules" / "ciphertalk-cli" / "bin" / "miyu.js")
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise RuntimeError("未找到 ciphertalk-cli 的 miyu.js 入口")


def run_miyu(arguments, timeout):
    node = shutil.which("node") or shutil.which("node.exe")
    if not node:
        raise RuntimeError("未找到 Node.js")
    command = [node, str(resolve_miyu_entry()), "--format=json", "--quiet", *arguments]
    try:
        return subprocess.run(
            command, capture_output=True, text=True, encoding="utf-8", errors="replace",
            timeout=timeout,
        )
    except subprocess.TimeoutExpired as exc:
        raise RuntimeError(f"CipherTalk CLI 超时（{timeout}s）") from exc


def main():
    parser = argparse.ArgumentParser(description="限时运行已安装的 CipherTalk CLI")
    parser.add_argument("--timeout", type=int, default=120)
    parser.add_argument("miyu_args", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    miyu_args = args.miyu_args[1:] if args.miyu_args[:1] == ["--"] else args.miyu_args
    if not miyu_args:
        raise RuntimeError("缺少 miyu 子命令")
    result = run_miyu(miyu_args, max(1, args.timeout))
    output = result.stdout.strip() or result.stderr.strip()
    if output:
        print(output)
    if result.returncode != 0:
        sys.exit(result.returncode)


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, ValueError) as exc:
        print(json.dumps({"ok": False, "error": str(exc)}, ensure_ascii=False), file=sys.stderr)
        sys.exit(1)
