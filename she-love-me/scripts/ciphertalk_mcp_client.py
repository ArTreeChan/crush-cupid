"""Call the packaged CipherTalk MCP server over stdio."""

import argparse
import json
import os
import queue
import re
import shutil
import subprocess
import sys
import threading
import time
from pathlib import Path

try:
    import winreg
except ImportError:  # pragma: no cover - only unavailable outside Windows
    winreg = None


if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")


def launcher_candidates():
    """Return generic packaged-launcher locations without user-specific paths."""
    candidates = []
    discovered = shutil.which("ciphertalk-mcp.cmd")
    if discovered:
        candidates.append(Path(discovered))
    for variable in ("ProgramW6432", "ProgramFiles", "ProgramFiles(x86)"):
        base = os.environ.get(variable)
        if base:
            candidates.append(Path(base) / "CipherTalk" / "ciphertalk-mcp.cmd")
    local_app_data = os.environ.get("LOCALAPPDATA")
    if local_app_data:
        candidates.extend((
            Path(local_app_data) / "Programs" / "CipherTalk" / "ciphertalk-mcp.cmd",
            Path(local_app_data) / "CipherTalk" / "ciphertalk-mcp.cmd",
        ))
    candidates.extend(registry_launcher_candidates())
    return candidates


def registry_launcher_candidates():
    if winreg is None:
        return []
    candidates = []
    roots = (winreg.HKEY_CURRENT_USER, winreg.HKEY_LOCAL_MACHINE)
    views = (0, getattr(winreg, "KEY_WOW64_64KEY", 0), getattr(winreg, "KEY_WOW64_32KEY", 0))
    uninstall = r"Software\Microsoft\Windows\CurrentVersion\Uninstall"
    for root in roots:
        for view in dict.fromkeys(views):
            try:
                parent = winreg.OpenKey(root, uninstall, 0, winreg.KEY_READ | view)
            except OSError:
                continue
            with parent:
                index = 0
                while True:
                    try:
                        name = winreg.EnumKey(parent, index)
                    except OSError:
                        break
                    index += 1
                    try:
                        child = winreg.OpenKey(parent, name)
                        with child:
                            display_name = str(winreg.QueryValueEx(child, "DisplayName")[0])
                            values = {}
                            for field in ("InstallLocation", "DisplayIcon", "UninstallString"):
                                try:
                                    values[field] = str(winreg.QueryValueEx(child, field)[0])
                                except OSError:
                                    values[field] = ""
                    except OSError:
                        continue
                    if not display_name.strip().lower().startswith("ciphertalk"):
                        continue
                    install_location = values["InstallLocation"].strip()
                    if install_location:
                        candidates.append(Path(install_location) / "ciphertalk-mcp.cmd")
                    for field in ("DisplayIcon", "UninstallString"):
                        executable = executable_from_registry_value(values[field])
                        if executable:
                            candidates.append(executable.parent / "ciphertalk-mcp.cmd")
    return candidates


def executable_from_registry_value(value):
    text = str(value or "").strip()
    if not text:
        return None
    if text.startswith('"'):
        match = re.match(r'^"([^"]+)"', text)
        return Path(match.group(1)) if match else None
    executable = re.match(r"^(.+?\.(?:exe|ico))(?:\s|,|$)", text, re.IGNORECASE)
    return Path(executable.group(1)) if executable else None


def find_launcher(explicit=None):
    if explicit:
        launcher = Path(explicit).expanduser()
        if launcher.is_file():
            return launcher.resolve()
        raise RuntimeError(f"CipherTalk MCP 启动器不存在: {launcher}")
    for launcher in launcher_candidates():
        if launcher.is_file():
            return launcher.resolve()
    raise RuntimeError(
        "未找到 CipherTalk MCP 启动器；请先安装官方桌面版，或使用 --launcher 指定路径"
    )


def dependency_node_modules():
    return Path(__file__).resolve().parent / "tmp" / "ciphertalk-mcp" / "node_modules"


def encode_message(payload):
    """Encode one MCP stdio message using the SDK's JSON-lines transport."""
    data = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    return (data + "\n").encode("utf-8")


def decode_messages(data):
    messages = []
    for line in data.decode("utf-8", errors="replace").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            messages.append(json.loads(line))
        except json.JSONDecodeError:
            continue
    return messages


def run_mcp(command, tool=None, arguments=None, timeout=120):
    initialize = {"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {
            "protocolVersion": "2025-03-26",
            "capabilities": {},
            "clientInfo": {"name": "she-love-me", "version": "1.0.0"},
        }}
    if tool:
        request = {
            "jsonrpc": "2.0", "id": 2, "method": "tools/call",
            "params": {"name": tool, "arguments": arguments or {}},
        }
    else:
        request = {"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {}}
    env = os.environ.copy()
    dependency_dir = dependency_node_modules()
    if dependency_dir.is_dir():
        existing = env.get("NODE_PATH", "")
        env["NODE_PATH"] = str(dependency_dir) + (os.pathsep + existing if existing else "")
    process = subprocess.Popen(
        command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        cwd=Path.cwd(), env=env,
    )
    output_queue = queue.Queue()
    errors = []

    def read_stdout():
        for line in iter(process.stdout.readline, b""):
            try:
                output_queue.put(json.loads(line.decode("utf-8")))
            except (UnicodeDecodeError, json.JSONDecodeError):
                continue

    def read_stderr():
        for line in iter(process.stderr.readline, b""):
            text = line.decode("utf-8", errors="replace").strip()
            if text:
                errors.append(text)

    threading.Thread(target=read_stdout, daemon=True).start()
    threading.Thread(target=read_stderr, daemon=True).start()

    def send(payload):
        process.stdin.write(encode_message(payload))
        process.stdin.flush()

    def wait_for(request_id, deadline):
        while time.monotonic() < deadline:
            try:
                message = output_queue.get(timeout=min(0.25, deadline - time.monotonic()))
            except queue.Empty:
                if process.poll() is not None:
                    break
                continue
            if message.get("id") == request_id:
                return message
        detail = "\n".join(errors[-10:])
        raise RuntimeError(detail or f"CipherTalk MCP 请求 {request_id} 超时")

    deadline = time.monotonic() + timeout
    try:
        send(initialize)
        initialized = wait_for(1, deadline)
        if "error" in initialized:
            raise RuntimeError(str(initialized["error"].get("message") or initialized["error"]))
        send({"jsonrpc": "2.0", "method": "notifications/initialized", "params": {}})
        send(request)
        response = wait_for(2, deadline)
        if "error" in response:
            raise RuntimeError(str(response["error"].get("message") or response["error"]))
        return response["result"]
    finally:
        if process.stdin:
            process.stdin.close()
        try:
            process.wait(timeout=3)
        except subprocess.TimeoutExpired:
            process.terminate()
            process.wait(timeout=3)


def main():
    parser = argparse.ArgumentParser(description="调用 CipherTalk 官方 MCP 服务")
    parser.add_argument("--launcher", help="可选：官方 ciphertalk-mcp.cmd 路径")
    parser.add_argument("--tool")
    parser.add_argument("--arguments", default="{}", help="工具参数 JSON")
    parser.add_argument("--timeout", type=int, default=120)
    args = parser.parse_args()
    launcher = find_launcher(args.launcher)
    command = [os.environ.get("COMSPEC", "cmd.exe"), "/d", "/c", str(launcher)]
    result = run_mcp(command, args.tool, json.loads(args.arguments), args.timeout)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, ValueError, json.JSONDecodeError) as exc:
        print(json.dumps({"status": "error", "error": str(exc)}, ensure_ascii=False), file=sys.stderr)
        sys.exit(1)
