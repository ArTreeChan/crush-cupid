"""List CipherTalk sessions without exposing message previews."""

import argparse
import json
import os
import sys

from ciphertalk_mcp_client import find_launcher, run_mcp


if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")


def public_sessions(payload):
    return [{
        "displayName": item.get("displayName"),
        "sessionId": item.get("sessionId"),
        "kind": item.get("kind"),
    } for item in payload.get("items", [])]


def main():
    parser = argparse.ArgumentParser(description="列出 CipherTalk 会话（不输出消息预览）")
    parser.add_argument("--limit", type=int, default=30)
    parser.add_argument("--offset", type=int, default=0)
    parser.add_argument("--launcher")
    parser.add_argument("--timeout", type=int, default=120)
    args = parser.parse_args()
    launcher = find_launcher(args.launcher)
    command = [os.environ.get("COMSPEC", "cmd.exe"), "/d", "/c", str(launcher)]
    result = run_mcp(command, "list_sessions", {
        "limit": args.limit, "offset": args.offset,
    }, args.timeout)
    if result.get("isError"):
        detail = result.get("content", [{}])[0].get("text", "CipherTalk 会话读取失败")
        raise RuntimeError(detail)
    payload = result.get("structuredContent") or result
    items = public_sessions(payload)
    print(json.dumps({
        "status": "ok", "total": payload.get("total", len(items)),
        "offset": payload.get("offset", args.offset), "items": items,
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, ValueError) as exc:
        print(json.dumps({"status": "error", "error": str(exc)}, ensure_ascii=False), file=sys.stderr)
        sys.exit(1)
