#!/usr/bin/env python3
"""Convert a WeFlow HTML export (window.WEFLOW_DATA) to she-love-me message bundle.

The HTML files produced by some WeFlow versions embed the whole chat as a JS
array:  window.WEFLOW_DATA = [ {"i":..,"t":..,"s":0|1,"a":"<span>..</span>",
"b":"<div class=\"message-time\">..</div><div class=\"message-content\">..</div>","p":".."}, ... ];

This script extracts that array, maps each record to the unified she-love-me
bundle format and writes messages.json (+ emojis.json) under data/contacts/.
"""

import argparse
import json
import re
import sys
from html import unescape
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "scripts"))

from contact_bundle import resolve_bundle_paths  # noqa: E402
from message_normalizer import normalize_payload  # noqa: E402


def strip_tags(html_text):
    text = re.sub(r"<br\s*/?>", "\n", html_text, flags=re.IGNORECASE)
    text = re.sub(r"<[^>]+>", "", text)
    text = unescape(text)
    return text


def parse_bubble(b):
    """Return (msg_type, content) from the bubble HTML."""
    if 'message-media image' in b:
        return "image", "[图片]"
    if 'message-media emoji' in b:
        alt = re.search(r'alt="([^"]*)"', b)
        return "emoji", (alt.group(1).strip() if alt and alt.group(1).strip() else "[表情]")
    if 'message-media video' in b:
        return "video", "[视频]"
    if 'message-media audio' in b:
        return "voice", "[语音消息]"
    if 'message-media file' in b:
        return "file", "[文件]"
    if 'message-link-card' in b:
        title = re.search(r'class="[^"]*link-card-title[^"]*"[^>]*>(.*?)</', b, re.S)
        content = strip_tags(title.group(1)).strip() if title else "[链接]"
        return "link", content
    # default: text message
    text_divs = re.findall(r'class="message-text"[^>]*>(.*?)</div>', b, re.S)
    content = "\n".join(strip_tags(t) for t in text_divs).strip()
    if not content:
        # fall back to whole bubble text
        content = strip_tags(b).strip()
    return "text", content


def main():
    parser = argparse.ArgumentParser(description="转换 WeFlow HTML 导出为 she-love-me 数据格式")
    parser.add_argument("--input", required=True, help="WeFlow HTML 文件")
    parser.add_argument("--contact", default=None, help="联系人显示名（默认取自文件名）")
    parser.add_argument("--output-dir", default="data/contacts", help="联系人导出根目录")
    parser.add_argument("--output", default=None, help="直接指定 messages.json 输出路径（与 --output-dir 互斥；供 crush-cupid 后端固定路径调用）")
    args = parser.parse_args()

    if sys.platform == "win32":
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")

    html = Path(args.input).read_text(encoding="utf-8-sig")
    match = re.search(r"window\.WEFLOW_DATA\s*=\s*(\[.*?\]);", html, re.S)
    if not match:
        raise SystemExit("未找到 window.WEFLOW_DATA 数组")
    raw_items = json.loads(match.group(1))
    print(f"提取到 {len(raw_items)} 条原始消息", file=sys.stderr)

    # Detect contact display name
    contact_display = args.contact
    if not contact_display:
        m = re.search(r"<title>(.*?)\s*-\s*聊天记录</title>", html)
        contact_display = m.group(1).strip() if m else Path(args.input).stem
    # Detect own sender label from s=1 messages
    own_label = "我"
    them_label = contact_display
    labels = {}
    for item in raw_items:
        label = strip_tags(item.get("a", ""))
        side = "me" if item.get("s") == 1 else "them"
        labels.setdefault(side, set()).add(label)
    own_candidates = sorted(labels.get("me", {"我"}))
    them_candidates = sorted(labels.get("them", {contact_display}))
    if own_candidates:
        own_label = own_candidates[0]
    if them_candidates:
        them_label = them_candidates[0]
    print(f"发送方标签 -> 我: {own_label!r} | 对方: {them_label!r}", file=sys.stderr)

    messages = []
    emoji_count = 0
    for item in raw_items:
        sender = "me" if item.get("s") == 1 else "them"
        msg_type, content = parse_bubble(item.get("b", ""))
        record = {
            "local_id": item.get("i"),
            "sender": sender,
            "content": content,
            "timestamp": item.get("t"),
            "type": msg_type,
            "local_type": 0,
        }
        if msg_type == "emoji":
            emoji_count += 1
        messages.append(record)

    payload = normalize_payload({
        "source": "weflow-html",
        "contact_username": "weflow_html_contact",
        "contact_display": contact_display,
        "own_label": own_label,
        "them_label": them_label,
        "messages": messages,
    }, drop_invalid=True)

    bundle = resolve_bundle_paths(
        payload["contact_display"], payload["contact_username"],
        output=args.output,
        output_dir=(None if args.output else args.output_dir),
    )
    payload.update({"bundle_dir": bundle["bundle_dir"], "emoji_catalog_file": "emojis.json"})
    emoji_payload = {
        "contact_username": payload["contact_username"],
        "contact_display": payload["contact_display"],
        "bundle_dir": bundle["bundle_dir"],
        "total_messages": emoji_count,
        "unique_emojis": 0,
        "emoji_records": [],
    }

    Path(bundle["bundle_dir"]).mkdir(parents=True, exist_ok=True)
    Path(bundle["messages_path"]).write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    Path(bundle["emojis_path"]).write_text(json.dumps(emoji_payload, ensure_ascii=False, indent=2), encoding="utf-8")

    # quick sender/time range summary
    me_n = sum(1 for m in payload["messages"] if m["sender"] == "me")
    them_n = sum(1 for m in payload["messages"] if m["sender"] == "them")
    print(json.dumps({
        "status": "ok",
        "source": "weflow-html",
        "total": payload["total"],
        "me": me_n,
        "them": them_n,
        "dropped": payload["normalization"]["dropped_messages"],
        "bundle_dir": bundle["bundle_dir"],
        "messages_path": bundle["messages_path"],
        "emojis_path": bundle["emojis_path"],
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
