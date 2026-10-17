#!/usr/bin/env bash
set -euo pipefail
# Clean CI emulator only. The instrumentation APK is the allowlisted public Workspace fixture.
./gradlew --no-daemon --max-workers 2 \
  -Dorg.gradle.jvmargs='-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8' \
  assembleLawnWithQuickstepGithubDebug assembleLawnWithQuickstepGithubDebugAndroidTest
report_dir="$PWD/build/reports/omni-handoff"
mkdir -p "$report_dir"
export ANDROID_AVD_HOME="${RUNNER_TEMP:?CI-only fixture requires RUNNER_TEMP}/omni-handoff-avd"
mkdir -p "$ANDROID_AVD_HOME"
printf 'no\n' | avdmanager create avd --force --name omni-handoff-api30 \
  --package 'system-images;android-30;google_apis;x86_64' --device pixel_2
"${ANDROID_HOME:?}/emulator/emulator" -avd omni-handoff-api30 -no-window -no-audio \
  -no-boot-anim -no-snapshot -gpu swiftshader -memory 2048 > "$report_dir/emulator.log" 2>&1 &
omni_emulator_pid=$!
trap 'adb logcat -d > "$report_dir/logcat.txt" 2>&1 || true; kill "$omni_emulator_pid" 2>/dev/null || true' EXIT
timeout 180 adb wait-for-device
for attempt in $(seq 1 90); do
  [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]] && break
  sleep 2
done
[[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]]
adb shell input keyevent 82
main_apk=$(find build/outputs/apk/lawnWithQuickstepGithub/debug -name '*.apk' -print -quit)
test_apk=$(find build/outputs/apk/androidTest/lawnWithQuickstepGithub/debug -name '*.apk' -print -quit)
adb install -r -t "$main_apk"
adb install -r -t "$test_apk"
adb shell cmd package set-home-activity app.lawnchair.debug/app.lawnchair.LawnchairLauncher
adb shell am instrument -w -r -e class app.lawnchair.omni.OmniSearchHandoffTest \
  com.omnidev.workspace/androidx.test.runner.AndroidJUnitRunner | tee "$report_dir/instrumentation.txt"
python3 - "$report_dir/instrumentation.txt" <<'PY'
from pathlib import Path
import re, sys
text = Path(sys.argv[1]).read_text()
if not re.search(r'OK\s*\(4 tests\)', text) or any(value in text for value in ['FAILURES!!!', 'Process crashed', 'INSTRUMENTATION_FAILED']):
    raise SystemExit('Cross-app handoff regressions did not all pass; inspect instrumentation/logcat reports.')
PY
