#\!/bin/bash

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
LOG_FILE="aidl_debug_${TIMESTAMP}.log"

echo "=== AIDL Bridge Debug Log Capture ==="
echo "Log file: $LOG_FILE"
echo "Press Ctrl+C to stop logging"
echo ""

# Clear logcat
adb logcat -c

# Capture all relevant logs
adb logcat -v threadtime \
  FeedPreference:V \
  AndroidRuntime:V \
  ActivityManager:V \
  PackageInstaller:V \
  FileProvider:V \
  System.err:V \
  app.lawnchair:V \
  *:E \
  | tee "$LOG_FILE"

EOF < /dev/null