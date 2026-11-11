#!/usr/bin/env python3
"""Read-only Android APK upgrade preflight. Does not install, delete or resign apps.

Usage: python3 scripts/check_apk_upgrade.py OLD.apk NEW.apk --build-tools "$ANDROID_HOME/build-tools/35.0.0"
Only an exact package+certificate match and increasing versionCode is approved.
Android v3 signing-key rotation is deliberately not auto-approved: require an
explicit platform-level validation, not a false positive from this conservative gate.
"""

import argparse
from dataclasses import dataclass
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys


class PreflightError(Exception):
    pass


@dataclass(frozen=True)
class ApkIdentity:
    package: str
    version_code: int
    signer_sha256: str


def parse_badging(output: str) -> tuple[str, int]:
    """Extract the unique package and numeric versionCode from aapt badging."""
    matches = re.findall(r"^package:\s+name='([^']+)'\s+versionCode='([^']+)'", output, re.MULTILINE)
    if len(matches) != 1:
        raise PreflightError("Невозможно достоверно определить package ID и versionCode (aapt)")
    package, code = matches[0]
    if not re.fullmatch(r"[A-Za-z_][A-Za-z_0-9]*(?:\.[A-Za-z_][A-Za-z_0-9]*)+", package):
        raise PreflightError("Некорректное имя пакета в AndroidManifest")
    if not re.fullmatch(r"[0-9]+", code):
        raise PreflightError("Некорректный versionCode APK")
    return package, int(code)


def parse_signers(output: str) -> str:
    fingerprints = re.findall(
        r"^Signer #\d+ certificate SHA-256 digest:\s*([a-fA-F0-9:]+)\s*$",
        output, re.MULTILINE,
    )
    if len(fingerprints) != 1:
        raise PreflightError("Ожидался один подтверждённый подписант APK; обновление заблокировано")
    digest = fingerprints[0].replace(":", "").lower()
    if not re.fullmatch(r"[a-f0-9]{64}", digest):
        raise PreflightError("Неверный SHA-256 сертификата APK")
    return digest


def run_checked(args: list[str]) -> str:
    try:
        call = subprocess.run(args, text=True, encoding="utf-8", errors="replace",
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    except (OSError, ValueError) as exc:
        raise PreflightError("Невозможно запустить инструмент проверки APK") from exc
    if call.returncode:
        # Do not echo external tool output: file paths and signing metadata can be sensitive.
        raise PreflightError(f"Инструмент {Path(args[0]).name} отклонил APK (код {call.returncode})")
    return call.stdout


def inspect_apk(path: Path, apksigner: str, aapt: str) -> ApkIdentity:
    if not path.is_file() or path.stat().st_size == 0:
        raise PreflightError("APK отсутствует или пуст: " + str(path))
    # apksigner validates signature integrity and signing scheme, not merely cert text.
    signer_output = run_checked([apksigner, "verify", "--print-certs", str(path)])
    fingerprint = parse_signers(signer_output)
    package, code = parse_badging(run_checked([aapt, "dump", "badging", str(path)]))
    return ApkIdentity(package, code, fingerprint)


def compare(old: ApkIdentity, new: ApkIdentity) -> None:
    if old.package != new.package:
        raise PreflightError("Разные package ID — это разные приложения, обновление поверх невозможно")
    if old.signer_sha256 != new.signer_sha256:
        raise PreflightError("Разные сертификаты подписи — установка поверх текущих данных запрещена")
    if new.version_code <= old.version_code:
        raise PreflightError("versionCode новой APK не выше старой; обычное обновление заблокировано")


def build_tools(explicit: str | None) -> tuple[str, str]:
    if explicit:
        directory = Path(explicit)
        aapt, signer = directory / "aapt", directory / "apksigner"
        if not aapt.is_file() or not signer.is_file():
            raise PreflightError("В указанной папке Android build-tools нет aapt/apksigner")
        return str(signer), str(aapt)
    aapt, signer = shutil.which("aapt"), shutil.which("apksigner")
    if aapt and signer:
        return signer, aapt
    for env in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk = os.environ.get(env)
        if sdk:
            root = Path(sdk) / "build-tools"
            if root.is_dir():
                for directory in sorted(root.iterdir(), reverse=True):
                    aapt, signer = directory / "aapt", directory / "apksigner"
                    if aapt.is_file() and signer.is_file():
                        return str(signer), str(aapt)
    raise PreflightError("Требуется Android SDK build-tools (aapt и apksigner)")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Проверка совместимости APK без изменения установленных данных")
    parser.add_argument("installed_apk", type=Path, help="APK установленной версии, извлечённая с устройства или точный оригинал")
    parser.add_argument("candidate_apk", type=Path, help="Новая APK")
    parser.add_argument("--build-tools", help="Путь к Android SDK build-tools, например $ANDROID_HOME/build-tools/35.0.0")
    args = parser.parse_args(argv)
    try:
        signer, aapt = build_tools(args.build_tools)
        old = inspect_apk(args.installed_apk, signer, aapt)
        new = inspect_apk(args.candidate_apk, signer, aapt)
        compare(old, new)
    except PreflightError as exc:
        print("НЕСОВМЕСТИМО / НЕ ПОДТВЕРЖДЕНО: " + str(exc), file=sys.stderr)
        print("Не удаляйте установленное приложение и не используйте adb uninstall.", file=sys.stderr)
        return 2
    print(f"ПРОВЕРКА ПРОЙДЕНА: {old.package}; versionCode {old.version_code} -> {new.version_code}; подпись совпадает.")
    print("Это только проверка APK, сохранность данных на устройстве требует отдельного теста установки и восстановления.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
