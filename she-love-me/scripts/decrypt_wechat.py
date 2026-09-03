"""
decrypt_wechat.py - she-love-me 的跨平台解密入口

职责：
  - Windows / Linux: 直接调用 wechat-decrypt 的 main.py decrypt
  - macOS: 编译并调用 C 版密钥扫描器，再执行 decrypt_db.py
"""
import argparse
import platform
import json
import subprocess
import sys
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_DECRYPTOR_DIR = REPO_ROOT / "vendor" / "wechat-decrypt"


def run_command(cmd, cwd, check=True):
    result = subprocess.run(
        cmd,
        cwd=str(cwd),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if result.stdout:
        print(result.stdout.replace(str(REPO_ROOT), "."), end="")
    if result.stderr:
        print(result.stderr.replace(str(REPO_ROOT), "."), end="", file=sys.stderr)
    if check and result.returncode != 0:
        raise RuntimeError(f"命令执行失败: {' '.join(cmd)}")
    return result.returncode


def ensure_decryptor_compatible(decryptor_dir, system):
    if not decryptor_dir.exists():
        raise RuntimeError(
            f"未找到解密器目录 {decryptor_dir}。可用 --decryptor-dir 指向已有兼容实现，"
            "或改用 weflow-cli / CipherTalk / Markdown 导入。"
        )
    required = ("find_all_keys_macos.c", "decrypt_db.py") if system == "darwin" else ("main.py",)
    missing = [name for name in required if not (decryptor_dir / name).is_file()]
    if missing:
        raise RuntimeError(f"解密器接口不兼容，缺少文件: {', '.join(missing)}")


def should_rebuild_macos_scanner(scanner, scanner_source):
    if not scanner.exists():
        return True
    return scanner_source.stat().st_mtime > scanner.stat().st_mtime


def load_existing_keys(keys_file):
    if not keys_file.exists():
        return {}

    try:
        with keys_file.open(encoding="utf-8") as f:
            keys = json.load(f)
    except (OSError, ValueError, json.JSONDecodeError):
        return {}

    return {name: value for name, value in keys.items() if not str(name).startswith("_")}


def run_macos_flow(decryptor_dir):
    scanner = decryptor_dir / "find_all_keys_macos"
    scanner_source = decryptor_dir / "find_all_keys_macos.c"
    keys_file = decryptor_dir / "all_keys.json"
    existing_keys = load_existing_keys(keys_file)
    if existing_keys:
        print(f"[*] 检测到现有数据库密钥 {len(existing_keys)} 个，跳过重复扫描。")
    else:
        if should_rebuild_macos_scanner(scanner, scanner_source):
            print("[*] 编译 macOS 密钥扫描器...")
            run_command(
                ["cc", "-O2", "-o", str(scanner), str(scanner_source), "-framework", "Foundation"],
                cwd=decryptor_dir,
            )

        print("[*] 运行 macOS 密钥扫描器...")
        scanner_rc = run_command([str(scanner)], cwd=decryptor_dir, check=False)
        if scanner_rc != 0:
            raise RuntimeError(
                "macOS 密钥扫描失败。通常需要：1) 以 root 运行扫描器；2) 对 /Applications/WeChat.app 做 ad-hoc 重签名；3) 重启微信后重试。"
            )

    print("[*] 开始解密全部数据库...")
    run_command([sys.executable, "decrypt_db.py"], cwd=decryptor_dir)


def run_default_flow(decryptor_dir):
    print("[*] 调用 wechat-decrypt 主流程...")
    run_command([sys.executable, "main.py", "decrypt"], cwd=decryptor_dir)


def main():
    parser = argparse.ArgumentParser(description="调用兼容解密器处理微信数据库")
    parser.add_argument(
        "--decryptor-dir",
        default=str(DEFAULT_DECRYPTOR_DIR),
        help="兼容解密器目录，默认 vendor/wechat-decrypt",
    )
    args = parser.parse_args()

    decryptor_dir = Path(args.decryptor_dir).expanduser().resolve()
    system = platform.system().lower()
    ensure_decryptor_compatible(decryptor_dir, system)

    if system == "darwin":
        run_macos_flow(decryptor_dir)
        return

    run_default_flow(decryptor_dir)


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as exc:
        print(f"[!] {exc}", file=sys.stderr)
        sys.exit(1)
