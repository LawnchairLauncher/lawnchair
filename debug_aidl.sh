#\!/bin/bash

echo "=== Debug AIDL Bridge Installation ==="
echo "Starting logcat filtering for AIDL Bridge issues..."
echo ""

# Clear logcat buffer first
adb logcat -c

# Start logcat with filters for our app and relevant errors
adb logcat -v time \
  *:E \
  FeedPreference:* \
  AndroidRuntime:* \
  ActivityManager:* \
  PackageInstaller:* \
  FileProvider:* \
  System.err:* \
  app.lawnchair:* \
  | grep -E "(FeedPreference|AIDL|FileProvider|Installation|Exception|Error|Crash)"

EOF < /dev/null