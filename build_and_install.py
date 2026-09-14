#!/usr/bin/env python3
"""
Build and install Sora Launcher (Lawnchair) debug APK to connected Android device.
Run: python build_and_install.py [--clean] [--no-install] [--no-launch] [--install-only]

Modes:
  (default)       Build + install + launch
  --clean         Clean build cache first, then build + install + launch
  --no-install    Only build, skip device install
  --no-launch     Build + install, but don't auto-launch
  --install-only  Skip build, install existing APK to device + launch
"""

import subprocess
import sys
import os
import argparse
from pathlib import Path

PROJECT_ROOT = Path(r"D:\Projects\Sora Launcher")
GRADLEW = PROJECT_ROOT / "gradlew.bat" if os.name == "nt" else PROJECT_ROOT / "gradlew"
APK_PATH = Path(r"D:\Projects\Sora Launcher\build\outputs\apk\lawnWithQuickstepGithub\debug\SoraLauncher.16.Dev.(33432f4).github.debug.apk")
ADB_PATH = Path(r"D:\platform-tools\adb.exe") if os.name == "nt" else Path(r"D:\platform-tools\adb")


def run(cmd: list[str], cwd: Path = None, check: bool = True) -> subprocess.CompletedProcess:
    print(f"$ {' '.join(cmd)}")
    result = subprocess.run(cmd, cwd=cwd or PROJECT_ROOT, capture_output=True, text=True)
    if result.stdout:
        print(result.stdout)
    if result.stderr:
        print(result.stderr, file=sys.stderr)
    if check and result.returncode != 0:
        sys.exit(result.returncode)
    return result


def install_and_launch(device: str, no_launch: bool = False):
    """Install APK to device and optionally launch."""
    # Install APK (-r replace, -d allow version code downgrade)
    print("\n=== Installing APK ===")
    run([str(ADB_PATH), "-s", device, "install", "-r", "-d", str(APK_PATH)])

    if no_launch:
        print("\n⏭️  Skipping launch (--no-launch)")
        return

    # Launch launcher
    print("\n=== Launching Lawnchair ===")
    run([str(ADB_PATH), "-s", device, "shell", "monkey", "-p", "app.lawnchair", "-c", "android.intent.category.LAUNCHER", "1"], check=False)


def main():
    parser = argparse.ArgumentParser(description="Build & install Sora Launcher debug APK")
    parser.add_argument("--clean", action="store_true", help="Run gradlew clean before build (clears cache)")
    parser.add_argument("--no-install", action="store_true", help="Only build, skip device install")
    parser.add_argument("--no-launch", action="store_true", help="Don't auto-launch app after install")
    parser.add_argument("--install-only", action="store_true", help="Skip build, install existing APK to device")
    args = parser.parse_args()

    # Verify APK exists for install modes
    if args.install_only or not args.no_install:
        if not APK_PATH.exists():
            print(f"❌ APK not found at {APK_PATH}. Run without --install-only first.", file=sys.stderr)
            sys.exit(1)
        print(f"✅ APK ready: {APK_PATH}")

    # --install-only: skip build entirely, just install + launch
    if args.install_only:
        print("=== Install-only mode (skipping build) ===")
        # Check adb device
        print("\n=== Checking connected device ===")
        devices = run([str(ADB_PATH), "devices"], check=False)
        lines = devices.stdout.strip().splitlines()[1:]
        authorized = [l.split()[0] for l in lines if l.strip().endswith("device")]
        if not authorized:
            print("❌ No authorized device found. Enable USB debugging & authorize this PC.", file=sys.stderr)
            sys.exit(1)
        device = authorized[0]
        print(f"✅ Device: {device}")

        install_and_launch(device, args.no_launch)
        print("\n🎉 Done! Folder flicker fix should now be active.")
        return

    # Normal build flow
    if args.clean:
        print("=== Cleaning build cache ===")
        run([str(GRADLEW), "clean"])

    print("=== Building debug APK ===")
    run([str(GRADLEW), "assembleDebug"])

    if args.no_install:
        print("\n⏭️  Skipping install (--no-install)")
        return

    # Check adb device
    print("\n=== Checking connected device ===")
    devices = run([str(ADB_PATH), "devices"], check=False)
    lines = devices.stdout.strip().splitlines()[1:]
    authorized = [l.split()[0] for l in lines if l.strip().endswith("device")]
    if not authorized:
        print("❌ No authorized device found. Enable USB debugging & authorize this PC.", file=sys.stderr)
        sys.exit(1)
    device = authorized[0]
    print(f"✅ Device: {device}")

    install_and_launch(device, args.no_launch)
    print("\n🎉 Done! Folder flicker fix should now be active.")


if __name__ == "__main__":
    main()