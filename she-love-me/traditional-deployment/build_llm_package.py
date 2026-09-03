"""
build_llm_package.py - Build a traditional handoff package for chat-based LLMs.

The package contains:
  - messages.json: unified chat history exported by existing repo scripts
  - analysis_prompt.txt: a detailed prompt that can be uploaded together with the JSON

Optional:
  - stats.json can be embedded into the prompt as a compact machine-generated summary
"""

import argparse
import json
import re
import shutil
from collections import Counter
from datetime import datetime
from pathlib import Path
from typing import Optional


REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUTPUT_DIR = Path(__file__).resolve().parent / "output"
DEFAULT_OUTPUT_DIR_DISPLAY = Path("traditional-deployment") / "output"
PROMPT_FILENAME = "analysis_prompt.txt"
MESSAGES_FILENAME = "messages.json"


def load_json(path: Path) -> dict:
    with path.open("r", encoding="utf-8-sig") as file:
        return json.load(file)


def display_path(path: Path) -> str:
    try:
        return str(path.relative_to(REPO_ROOT))
    except ValueError:
        return str(path)


def slugify(value: str) -> str:
    cleaned = re.sub(r"[\\/:*?\"<>|]+", "_", value.strip())
    cleaned = re.sub(r"\s+", "_", cleaned)
    return cleaned[:80] or "chat_export"


def validate_messages(data: dict) -> None:
    if not isinstance(data, dict):
        raise ValueError("messages.json 顶层必须是对象")

    messages = data.get("messages")
    if not isinstance(messages, list) or not messages:
        raise ValueError("messages.json 中缺少非空的 messages 数组")

    required_keys = {"sender", "content", "timestamp", "type"}
    for index, message in enumerate(messages[:20]):
        if not isinstance(message, dict):
            raise ValueError(f"messages[{index}] 不是对象")
        missing = required_keys - set(message.keys())
        if missing:
            raise ValueError(f"messages[{index}] 缺少字段: {', '.join(sorted(missing))}")


def summarize_messages(data: dict) -> dict:
    messages = data["messages"]
    total = len(messages)
    me_count = sum(1 for item in messages if item.get("sender") == "me")
    them_count = sum(1 for item in messages if item.get("sender") == "them")
    text_messages = [item for item in messages if item.get("type") == "text"]
    text_count = len(text_messages)
    non_text_count = total - text_count
    type_counter = Counter(item.get("type", "unknown") for item in messages)

    timestamps = [item["timestamp"] for item in messages if isinstance(item.get("timestamp"), (int, float))]
    if timestamps:
        start = datetime.fromtimestamp(min(timestamps))
        end = datetime.fromtimestamp(max(timestamps))
        date_range = f"{start:%Y-%m-%d} ~ {end:%Y-%m-%d}"
        span_days = max((end - start).days, 1)
    else:
        date_range = "未知"
        span_days = 0

    preview_types = ", ".join(
        f"{msg_type}:{count}" for msg_type, count in type_counter.most_common(6)
    )

    return {
        "contact_display": data.get("contact_display") or data.get("contact_username") or "未命名联系人",
        "contact_id": data.get("contact_username") or "",
        "total_messages": total,
        "text_messages": text_count,
        "non_text_messages": non_text_count,
        "me_messages": me_count,
        "them_messages": them_count,
        "me_ratio": round(me_count / total * 100, 1) if total else 0,
        "them_ratio": round(them_count / total * 100, 1) if total else 0,
        "date_range": date_range,
        "span_days": span_days,
        "message_types": preview_types or "无",
    }


def build_stats_excerpt(stats: Optional[dict]) -> str:
    if not stats:
        return (
            "未嵌入自动统计摘要。模型需要直接从 messages.json 自行归纳互动模式、"
            "对话发起比例、回复速度差、冷淡信号和风险线索。"
        )

    basic = stats.get("basic", {})
    initiative = stats.get("initiative", {})
    reply_speed = stats.get("reply_speed", {})
    scores = stats.get("scores", {})
    repair = stats.get("repair", {})
    recent = stats.get("recent_30d") or {}
    cold = stats.get("cold_response", {})
    goodnight = stats.get("goodnight", {})

    lines = [
        f"- 总消息数: {basic.get('total_messages', '未知')}",
        f"- 时间范围: {basic.get('date_range', ['未知', '未知'])}",
        f"- 我方消息占比: {basic.get('my_ratio', '未知')}",
        f"- 对方消息占比: {basic.get('their_ratio', '未知')}",
        f"- 我方发起对话占比: {initiative.get('my_start_ratio', '未知')}",
        f"- 我方平均回复: {reply_speed.get('my_avg_human', '未知')}",
        f"- 对方平均回复: {reply_speed.get('their_avg_human', '未知')}",
        f"- 我方修复发起次数: {repair.get('me_repair_count', '未知')}",
        f"- 对方修复发起次数: {repair.get('them_repair_count', '未知')}",
        f"- 我方冷淡回复次数: {cold.get('my_cold_count', '未知')}",
        f"- 对方冷淡回复次数: {cold.get('their_cold_count', '未知')}",
        f"- 我方晚安/早安信号: {goodnight.get('my_goodnight', '未知')}",
        f"- 对方晚安/早安信号: {goodnight.get('their_goodnight', '未知')}",
        f"- 最近30天我方主动比例: {recent.get('me_initiation_ratio', '未知')}",
        f"- 最近30天对方消息波动系数: {recent.get('them_message_density_cv', '未知')}",
        (
            f"- 项目内置三项指数: 主动指数={scores.get('simp_index', '未知')} / "
            f"被爱指数={scores.get('loved_index', '未知')} / 冷淡指数={scores.get('cold_index', '未知')}"
        ),
    ]
    return "\n".join(lines)


def build_prompt(summary: dict, stats: Optional[dict], messages_name: str) -> str:
    stats_excerpt = build_stats_excerpt(stats)
    contact_name = summary["contact_display"]
    warning = ""
    if summary["total_messages"] > 12000:
        warning = (
            "\n上下文提醒：本数据量较大。如果你的上下文窗口有限，"
            "请优先精读最近 3 到 6 个月、冷战后的修复时段、明显升温时段和关系转折点，"
            "但最终结论仍需明确说明基于哪些时间段和哪些证据。"
        )

    return f"""你将和一个名为 `{messages_name}` 的聊天记录 JSON 文件一起被提交给聊天框里的大模型。
你的任务不是泛泛做情感安慰，而是像关系分析师一样，严格基于聊天数据做结构化判断。

【文件说明】
1. `{messages_name}` 是统一格式的聊天记录文件。
2. 顶层常见字段包括：
   - `contact_display` / `contact_username`: 联系人信息
   - `messages`: 聊天数组
3. `messages` 中每条记录常见字段包括：
   - `sender`: `me` 或 `them`
   - `content`: 文本内容，非文本消息已被转成占位符
   - `timestamp`: Unix 秒级时间戳
   - `type`: `text` / `image` / `voice` / `video` / `emoji` / `link` / `call` / `system` / `revoke` 等

【本次数据摘要】
- 分析对象: {contact_name}
- 总消息数: {summary['total_messages']}
- 文本消息数: {summary['text_messages']}
- 非文本消息数: {summary['non_text_messages']}
- 我方消息数: {summary['me_messages']} ({summary['me_ratio']}%)
- 对方消息数: {summary['them_messages']} ({summary['them_ratio']}%)
- 时间范围: {summary['date_range']}
- 覆盖天数: {summary['span_days']}
- 主要消息类型: {summary['message_types']}

【自动统计摘要】
{stats_excerpt}
{warning}

【分析铁律】
1. 只能基于聊天记录和上面的统计摘要下结论，禁止脑补现实中未出现的信息。
2. 任何涉及人格、依恋、操控、冷暴力、未来画饼、单相思痴迷等结论，都必须给出明确证据。
3. 如果证据不足，不要硬判，直接写“证据不足”并说明卡在哪里。
4. 引用证据时，尽量带上时间、发送方、原文片段。
5. 非文本消息只能作为辅助信号，核心结论优先基于文本和互动节奏。
6. 不要把“安慰用户”放在第一位，第一位是看清关系现状、互动结构和风险成本。

【建议的分析顺序】
1. 先判断关系类型、关系阶段、整体趋势。
2. 再判断双方谁更主动、谁更保留、谁在修复冲突、谁掌握节奏。
3. 再分析双方的依恋倾向、沟通风格、防御模式、情感可得性。
4. 再识别高风险信号，比如：
   - 冷暴力 / 长时间沉默
   - 间歇性强化
   - 爱情轰炸
   - 理想化-贬低循环
   - 未来画饼
   - 单相思痴迷 / 过度情绪依赖
   - 情感创伤绑定
5. 最后给出具体建议：该停止什么、该开始什么、什么时候应该止损。

【必须完成的判断项】
1. 关系类型
   例如：双向喜欢、暧昧拉扯、严重单向投入、朋友边界、名存实亡、情绪工具人、备选位等。
2. 关系阶段
   例如：初识、升温、拉扯确认前夜、正式确认期、关系维护期、降温撤退期。
3. 关系趋势
   例如：升温中、基本稳定、逐渐降温、已经明显冷却。
4. 情感对称性
   说明谁是更在乎的一方，谁是更有保留的一方，证据是什么。
5. 双方人格与互动画像
   包括但不限于：
   - 依恋倾向
   - 沟通风格
   - 防御机制
   - 爱的语言
   - 情感可得性
6. Sternberg 三角
   给出激情、亲密、承诺三个维度的 0-100 分估计值，并解释分数依据。
7. Gottman 风险观察
   判断正负互动比例是否失衡，是否出现批评、蔑视、防御、冷战/筑墙。
8. 风险预警
   对高风险项做单独小节。如果不成立，写“不构成高亮预警，仅作为观察提示”。
9. 行动建议
   必须具体到聊天行为，不要空话。

【输出要求】
请用中文 Markdown 输出，严格使用下面的结构：

# 聊天关系深度分析报告

## 1. 一句话结论
用 3 到 5 句话说清这段关系到底是什么状态。

## 2. 数据概览
- 联系人
- 时间范围
- 总消息数
- 我方 / 对方消息占比
- 你认为最关键的三个数据事实

## 3. 关系诊断
- 关系类型
- 关系阶段
- 关系趋势
- 情感对称性
- 谁在主导节奏

## 4. 双方画像
分别写“我”和“对方”：
- 依恋倾向
- 沟通风格
- 防御机制
- 核心需求
- 情感可得性

## 5. 互动结构
- 谁更常发起对话
- 谁更常修复冲突
- 是否存在追逃循环
- 是否存在爱的语言错位
- 是否存在关系转折点

## 6. Sternberg 三角评分
- 激情: 分数 + 依据
- 亲密: 分数 + 依据
- 承诺: 分数 + 依据
- 爱情类型结论

## 7. Gottman 与风险预警
- 正负互动比例的判断
- 是否出现批评 / 蔑视 / 防御 / 冷战
- 是否存在高风险信号
- 如果证据不足，明确写证据不足

## 8. 军师建议
- 立刻停止的 3 件事
- 立刻开始的 3 件事
- 继续推进这段关系的前提
- 你的止损线

## 9. 关键证据摘录
至少列 5 条。每条都包含：
- 时间
- 发送方
- 原文片段
- 这条证据说明了什么

## 10. 最终结论
直接回答：
- 这段关系值不值得继续投入？
- 如果继续，应该怎么继续？
- 如果不继续，最核心的原因是什么？

【最终风格】
1. 结论要直接，不要模棱两可。
2. 语气可以有人味，但不要油腻，不要像鸡汤。
3. 先讲事实，再讲理论，再给建议。
4. 关键判断要让用户能回到聊天记录里对得上号。
"""


def build_package(messages_path: Path, output_dir: Path, stats_path: Optional[Path] = None) -> tuple[Path, Path]:
    messages_data = load_json(messages_path)
    validate_messages(messages_data)
    summary = summarize_messages(messages_data)
    stats_data = load_json(stats_path) if stats_path else None

    contact_slug = slugify(summary["contact_display"])
    package_dir = output_dir / contact_slug
    package_dir.mkdir(parents=True, exist_ok=True)

    packaged_messages = package_dir / MESSAGES_FILENAME
    shutil.copyfile(messages_path, packaged_messages)

    prompt_text = build_prompt(summary, stats_data, packaged_messages.name)
    prompt_path = package_dir / PROMPT_FILENAME
    prompt_path.write_text(prompt_text, encoding="utf-8")

    return packaged_messages, prompt_path


def main() -> None:
    parser = argparse.ArgumentParser(description="为聊天框大模型构建传统分析包")
    parser.add_argument(
        "--messages",
        required=True,
        help="messages.json 路径，通常来自 scripts/extract_messages.py 或 scripts/extract_messages_qq.py",
    )
    parser.add_argument(
        "--stats",
        help="可选的 stats.json 路径，来自 scripts/stats_analyzer.py，用于把统计摘要嵌入提示词",
    )
    parser.add_argument(
        "--output-dir",
        default=str(DEFAULT_OUTPUT_DIR),
        help=f"输出目录，默认 {DEFAULT_OUTPUT_DIR_DISPLAY}",
    )
    args = parser.parse_args()

    messages_path = Path(args.messages).expanduser().resolve()
    stats_path = Path(args.stats).expanduser().resolve() if args.stats else None
    output_dir = Path(args.output_dir).expanduser().resolve()

    if not messages_path.exists():
        raise SystemExit(f"messages.json 不存在: {display_path(messages_path)}")
    if stats_path and not stats_path.exists():
        raise SystemExit(f"stats.json 不存在: {display_path(stats_path)}")

    packaged_messages, prompt_path = build_package(messages_path, output_dir, stats_path)
    print(
        json.dumps(
            {
                "status": "ok",
                "messages": display_path(packaged_messages),
                "prompt": display_path(prompt_path),
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
