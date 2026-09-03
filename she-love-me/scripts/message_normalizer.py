"""Normalize imported chat messages into the she-love-me data contract."""

import math
from datetime import datetime, timezone


MIN_TIMESTAMP = datetime(2000, 1, 1, tzinfo=timezone.utc).timestamp()
MAX_TIMESTAMP = datetime(2100, 1, 1, tzinfo=timezone.utc).timestamp()


def normalize_timestamp(value):
    """Return a Unix timestamp in seconds from seconds/ms/us/ns or date text."""
    if isinstance(value, str):
        text = value.strip()
        if not text:
            raise ValueError("时间戳为空")
        try:
            value = float(text)
        except ValueError:
            parsed = _parse_datetime(text)
            return parsed.timestamp()

    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"不支持的时间戳: {value!r}")
    if not math.isfinite(value):
        raise ValueError(f"时间戳不是有限数值: {value!r}")

    timestamp = float(value)
    while timestamp >= 100_000_000_000:
        timestamp /= 1000.0

    if not MIN_TIMESTAMP <= timestamp <= MAX_TIMESTAMP:
        raise ValueError(f"时间戳超出支持范围: {value!r}")
    return int(timestamp) if timestamp.is_integer() else timestamp


def _parse_datetime(text):
    normalized = text.replace("Z", "+00:00")
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError:
        parsed = None
        for pattern in (
            "%Y-%m-%d %H:%M:%S",
            "%Y-%m-%d %H:%M",
            "%Y/%m/%d %H:%M:%S",
            "%Y/%m/%d %H:%M",
        ):
            try:
                parsed = datetime.strptime(text, pattern)
                break
            except ValueError:
                continue
        if parsed is None:
            raise ValueError(f"无法解析时间: {text!r}")
    if parsed.tzinfo is None:
        parsed = parsed.astimezone()
    return parsed.astimezone(timezone.utc)


def normalize_payload(payload, drop_invalid=False):
    if not isinstance(payload, dict):
        raise ValueError("输入 JSON 顶层必须是对象")
    messages = payload.get("messages")
    if not isinstance(messages, list):
        raise ValueError("输入数据缺少 messages 数组")

    normalized = []
    warnings = []
    for index, raw in enumerate(messages, start=1):
        try:
            normalized.append(normalize_message(raw, index))
        except ValueError as exc:
            if not drop_invalid:
                raise ValueError(f"第 {index} 条消息无效: {exc}") from exc
            warnings.append({"index": index, "error": str(exc)})

    normalized.sort(key=lambda item: (item["timestamp"], str(item.get("local_id", ""))))
    result = dict(payload)
    result["messages"] = normalized
    result["total"] = len(normalized)
    result["normalization"] = {
        "timestamp_unit": "seconds",
        "dropped_messages": len(warnings),
        "warnings": warnings[:20],
    }
    return result


def normalize_message(raw, index):
    if not isinstance(raw, dict):
        raise ValueError("消息必须是对象")

    sender = str(raw.get("sender", "")).strip().lower()
    sender_aliases = {
        "me": "me", "self": "me", "mine": "me", "我": "me",
        "them": "them", "other": "them", "对方": "them", "ta": "them",
    }
    sender = sender_aliases.get(sender, sender)
    if sender not in ("me", "them"):
        raise ValueError(f"sender 必须是 me/them，当前为 {raw.get('sender')!r}")

    timestamp_value = raw.get("timestamp")
    if timestamp_value is None:
        for key in ("createTime", "create_time", "time", "sendTime", "msgTime"):
            if raw.get(key) is not None:
                timestamp_value = raw[key]
                break

    message = dict(raw)
    message["local_id"] = raw.get("local_id", raw.get("localId", index))
    message["sender"] = sender
    message["timestamp"] = normalize_timestamp(timestamp_value)
    message["type"] = str(raw.get("type") or "text").strip().lower()
    message["content"] = str(raw.get("content") or "")
    transcript = raw.get("transcript") or raw.get("voice_transcript")
    if transcript:
        message["transcript"] = str(transcript).strip()
    return message


def analytical_text(message):
    if message.get("type") == "text":
        return str(message.get("content") or "").strip()
    transcript = str(message.get("transcript") or "").strip()
    return transcript
