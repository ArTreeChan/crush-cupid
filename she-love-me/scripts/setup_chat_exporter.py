"""Check or install a supported third-party WeChat chat exporter."""

import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

import setup_ciphertalk_scanner


PROVIDERS = {
    "weflow-cli": {
        "package": "weflow-cli",
        "command": "weflow-cli",
        "minimum_node": (18, 0, 0),
    },
    "ciphertalk": {
        "package": "ciphertalk-cli",
        "command": "miyu",
        "minimum_node": (18, 0, 0),
    },
}

PROVIDER_ORDER = ("weflow-cli", "ciphertalk")


if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")


def run(command, check=True):
    result = subprocess.run(
        command,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if check and result.returncode != 0:
        details = result.stderr.strip() or result.stdout.strip() or "未知错误"
        raise RuntimeError(f"命令失败（{' '.join(command)}）：{details}")
    return result


def command_path(name):
    return shutil.which(name) or shutil.which(f"{name}.cmd")


def node_version(node_path):
    result = run([node_path, "--version"])
    version = result.stdout.strip().lstrip("v")
    try:
        parts = tuple(int(part) for part in version.split(".")[:3])
        return parts + (0,) * (3 - len(parts)), version
    except (TypeError, ValueError) as exc:
        raise RuntimeError(f"无法解析 Node.js 版本：{version!r}") from exc


def provider_status(provider):
    spec = PROVIDERS[provider]
    node = command_path("node")
    npm = command_path("npm")
    executable = command_path(spec["command"])
    report = {
        "status": "ok",
        "provider": provider,
        "platform": sys.platform,
        "supported_platform": sys.platform == "win32",
        "python": sys.executable,
        "node": node,
        "npm": npm,
        "command": executable,
        "installed": bool(executable),
        "ready": False,
    }
    if node:
        parsed_version, version = node_version(node)
        report.update({
            "node_version": version,
            "node_supported": parsed_version >= spec["minimum_node"],
            "minimum_node": ".".join(map(str, spec["minimum_node"])),
        })
    else:
        report["node_supported"] = False
    if executable:
        version_result = run([executable, "--version"], check=False)
        report["command_version"] = (version_result.stdout or version_result.stderr).strip()
        report["command_works"] = version_result.returncode == 0
    else:
        report["command_works"] = False
    report["ready"] = all((
        report["supported_platform"], report["node_supported"], bool(npm),
        report["installed"], report["command_works"],
    ))
    if provider == "ciphertalk":
        scanner_dir = Path("vendor/ciphertalk/scanner")
        _, report["headless_scanner_ready"] = (
            setup_ciphertalk_scanner.component_status(scanner_dir)
        )
    return report


def prepare_ciphertalk_scanner():
    script = Path(__file__).with_name("setup_ciphertalk_scanner.py")
    result = run([sys.executable, str(script), "--download"])
    try:
        payload = json.loads(result.stdout)
    except json.JSONDecodeError as exc:
        raise RuntimeError("CipherTalk 无界面扫描组件安装器返回无效结果") from exc
    if not payload.get("ready"):
        raise RuntimeError("CipherTalk 无界面扫描组件未准备好")
    return payload


def install_provider(provider):
    spec = PROVIDERS[provider]
    if sys.platform != "win32":
        raise RuntimeError("自动安装微信导出器当前仅支持 Windows")
    node = command_path("node")
    npm = command_path("npm")
    if not node or not npm:
        raise RuntimeError("未找到 Node.js/npm；请先安装 Node.js 18 或更高版本")
    parsed_version, version = node_version(node)
    if parsed_version < spec["minimum_node"]:
        required = ".".join(map(str, spec["minimum_node"]))
        raise RuntimeError(f"Node.js {version} 不兼容 {provider}；需要 {required} 或更高版本")

    run([npm, "install", "-g", spec["package"]])
    status = provider_status(provider)
    if provider == "ciphertalk":
        status["headless_scanner"] = prepare_ciphertalk_scanner()
        status["headless_scanner_ready"] = True
    return status


def setup_automatic(install):
    attempts = []
    for provider in PROVIDER_ORDER:
        try:
            status = provider_status(provider)
            if install and not status["ready"]:
                status = install_provider(provider)
                status["changed"] = True
            elif install and provider == "ciphertalk" and not status["headless_scanner_ready"]:
                status["headless_scanner"] = prepare_ciphertalk_scanner()
                status["headless_scanner_ready"] = True
                status["changed"] = True
            else:
                status["changed"] = False
            attempts.append(status)
            if status["ready"]:
                result = dict(status)
                result["attempts"] = [dict(attempt) for attempt in attempts]
                return result
        except (OSError, RuntimeError) as exc:
            attempts.append({
                "status": "error",
                "provider": provider,
                "error": str(exc),
            })
    raise RuntimeError(json.dumps({
        "message": "没有可用的微信导出器",
        "attempts": attempts,
    }, ensure_ascii=False))


def main():
    parser = argparse.ArgumentParser(description="检查或安装 she-love-me 支持的微信导出工具")
    parser.add_argument(
        "--provider", choices=["auto", *PROVIDER_ORDER], default="auto",
        help="默认依次尝试 weflow-cli 和 CipherTalk",
    )
    parser.add_argument("--install", action="store_true", help="缺失时通过 npm 全局安装并准备依赖")
    args = parser.parse_args()

    if args.provider == "auto":
        result = setup_automatic(args.install)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return

    before = provider_status(args.provider)
    if args.install and not before["ready"]:
        after = install_provider(args.provider)
        after["changed"] = True
        print(json.dumps(after, ensure_ascii=False, indent=2))
        return
    if args.install and args.provider == "ciphertalk" and not before["headless_scanner_ready"]:
        before["headless_scanner"] = prepare_ciphertalk_scanner()
        before["headless_scanner_ready"] = True
        before["changed"] = True
        print(json.dumps(before, ensure_ascii=False, indent=2))
        return
    before["changed"] = False
    print(json.dumps(before, ensure_ascii=False, indent=2))
    if not before["ready"]:
        sys.exit(1)


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError) as exc:
        print(json.dumps({"status": "error", "error": str(exc)}, ensure_ascii=False), file=sys.stderr)
        sys.exit(1)
