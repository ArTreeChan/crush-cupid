"""Export one CipherTalk session through the packaged MCP server."""

import argparse
import json
import os
import sys
import time
from pathlib import Path

from ciphertalk_mcp_client import find_launcher, run_mcp


if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description="通过 CipherTalk MCP 导出单个会话")
    parser.add_argument("--session-id", required=True)
    parser.add_argument("--contact", required=True)
    parser.add_argument("--output-dir", default="data/raw/ciphertalk-official")
    parser.add_argument("--start-time", type=int, default=946684800)
    parser.add_argument("--end-time", type=int, default=int(time.time()) + 86400)
    parser.add_argument("--launcher")
    parser.add_argument("--timeout", type=int, default=600)
    args = parser.parse_args()

    launcher = find_launcher(args.launcher)
    command = [os.environ.get("COMSPEC", "cmd.exe"), "/d", "/c", str(launcher)]
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    result = run_mcp(command, "export_chat", {
        "sessionId": args.session_id,
        "format": "json",
        "dateRange": {"start": args.start_time, "end": args.end_time},
        "mediaOptions": {
            "exportAvatars": False,
            "exportImages": False,
            "exportVideos": False,
            "exportEmojis": False,
            "exportVoices": False,
        },
        "outputDir": str(output_dir),
        "validateOnly": False,
    }, args.timeout)
    if result.get("isError"):
        detail = result.get("content", [{}])[0].get("text", "CipherTalk 导出失败")
        raise RuntimeError(detail)
    exported = result.get("structuredContent") or result
    if not exported.get("success") or not exported.get("outputPath"):
        raise RuntimeError(str(exported.get("message") or "CipherTalk 未返回导出文件"))
    output = Path(exported["outputPath"])
    with output.open(encoding="utf-8-sig") as handle:
        total = len((json.load(handle) or {}).get("messages", []))
    print(json.dumps({
        "status": "ok", "contact": args.contact, "total": total,
        "output": str(output),
    }, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, ValueError) as exc:
        print(json.dumps({"status": "error", "error": str(exc)}, ensure_ascii=False), file=sys.stderr)
        sys.exit(1)
