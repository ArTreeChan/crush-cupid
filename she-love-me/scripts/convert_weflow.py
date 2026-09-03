"""Convert a WeFlow JSON export to the she-love-me message bundle format."""

import argparse
import json
import sys
from pathlib import Path

from contact_bundle import resolve_bundle_paths
from message_normalizer import normalize_payload


if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")


MSG_TYPE_MAP = {
    "文本消息": "text", "动画表情": "emoji", "表情": "emoji",
    "图片消息": "image", "语音消息": "voice", "视频消息": "video",
    "链接消息": "link", "系统消息": "system", "撤回消息": "revoke",
    "小程序消息": "mini_program", "位置消息": "location", "名片消息": "card",
    "合并转发消息": "merged", "文件消息": "file",
}


def first_value(source, *keys):
    for key in keys:
        value = source.get(key)
        if value not in (None, ""):
            return value
    return None


def convert_payload(data, own_wxid=None, display_name=None, wxid=None):
    if not isinstance(data, dict) or not isinstance(data.get("messages"), list):
        raise ValueError("不是有效的 WeFlow JSON：需要包含 messages 数组")

    session = data.get("session") or {}
    raw_messages = data["messages"]
    contact_username = wxid or session.get("wxid") or "unknown"
    contact_display = display_name or session.get("remark") or session.get("nickname") or contact_username

    detected_own = own_wxid
    if not detected_own:
        for raw in raw_messages:
            sender_username = raw.get("senderUsername")
            if raw.get("isSend") in (1, True, "1") and sender_username != contact_username:
                detected_own = sender_username
                break

    converted = []
    emoji_records = {}
    for index, raw in enumerate(raw_messages, start=1):
        local_type = raw.get("localType", 0)
        message_type = MSG_TYPE_MAP.get(raw.get("type", ""), "other")
        if local_type == 47:
            message_type = "emoji"
        elif local_type == 10002:
            message_type = "revoke"

        is_send = raw.get("isSend") in (1, True, "1")
        content = str(raw.get("content") or "")
        transcript = first_value(raw, "transcript", "voiceTranscript", "recognitionText", "voiceText")
        record = {
            "local_id": raw.get("localId", index),
            "sender": "me" if is_send else "them",
            "content": content,
            "timestamp": first_value(raw, "createTime", "timestamp", "time"),
            "type": message_type,
            "local_type": local_type,
        }
        if transcript:
            record["transcript"] = str(transcript)
        if message_type == "emoji":
            md5 = raw.get("emojiMd5") or f"unknown_{index}"
            emoji_id = f"emoji_{md5}"
            record.update({"emoji_ref": emoji_id, "content": content or "[表情]"})
            item = emoji_records.setdefault(emoji_id, {
                "emoji_id": emoji_id, "md5": md5,
                "cdnurl": raw.get("emojiCdnUrl", ""),
                "len": raw.get("emojiLen"),
                "first_local_id": record["local_id"],
                "first_timestamp": record["timestamp"],
                "occurrence_count": 0,
            })
            item["occurrence_count"] += 1
        elif message_type == "voice":
            record["content"] = content or "[语音消息]"
        elif message_type == "image":
            record["content"] = content or "[图片]"
        elif message_type == "video":
            record["content"] = content or "[视频]"
        elif message_type == "revoke":
            record["content"] = "[撤回了一条消息]"
        converted.append(record)

    payload = normalize_payload({
        "source": "weflow",
        "contact_username": contact_username,
        "contact_display": contact_display,
        "own_wxid": detected_own or "unknown",
        "messages": converted,
    }, drop_invalid=True)
    valid_emoji_messages = [
        message for message in payload["messages"] if message.get("emoji_ref")
    ]
    valid_emoji_ids = {message["emoji_ref"] for message in valid_emoji_messages}
    emoji_records = {
        emoji_id: item for emoji_id, item in emoji_records.items()
        if emoji_id in valid_emoji_ids
    }
    for emoji_id, item in emoji_records.items():
        occurrences = [
            message for message in valid_emoji_messages
            if message["emoji_ref"] == emoji_id
        ]
        item["occurrence_count"] = len(occurrences)
        item["first_timestamp"] = occurrences[0]["timestamp"]
    return payload, emoji_records


def main():
    parser = argparse.ArgumentParser(description="转换 WeFlow JSON 为 she-love-me 数据格式")
    parser.add_argument("--input", required=True, help="WeFlow JSON 文件")
    parser.add_argument("--output-dir", default="data/contacts", help="联系人导出根目录")
    parser.add_argument("--own-wxid", help="自己的微信 wxid")
    parser.add_argument("--display-name", help="覆盖联系人显示名")
    parser.add_argument("--wxid", help="覆盖联系人 wxid")
    args = parser.parse_args()

    with open(args.input, encoding="utf-8-sig") as handle:
        data = json.load(handle)
    payload, emoji_records = convert_payload(data, args.own_wxid, args.display_name, args.wxid)
    bundle = resolve_bundle_paths(
        payload["contact_display"], payload["contact_username"], output_dir=args.output_dir
    )
    payload.update({"bundle_dir": bundle["bundle_dir"], "emoji_catalog_file": "emojis.json"})
    emoji_payload = {
        "contact_username": payload["contact_username"],
        "contact_display": payload["contact_display"],
        "bundle_dir": bundle["bundle_dir"],
        "total_messages": sum(1 for item in payload["messages"] if item["type"] == "emoji"),
        "unique_emojis": len(emoji_records),
        "emoji_records": sorted(emoji_records.values(), key=lambda item: str(item["emoji_id"])),
    }

    Path(bundle["bundle_dir"]).mkdir(parents=True, exist_ok=True)
    Path(bundle["messages_path"]).write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    Path(bundle["emojis_path"]).write_text(json.dumps(emoji_payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({
        "status": "ok", "source": "weflow", "total": payload["total"],
        "dropped": payload["normalization"]["dropped_messages"],
        "bundle_dir": bundle["bundle_dir"], "messages_path": bundle["messages_path"],
        "emojis_path": bundle["emojis_path"],
    }, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(json.dumps({"status": "error", "error": str(exc)}, ensure_ascii=False), file=sys.stderr)
        sys.exit(1)
