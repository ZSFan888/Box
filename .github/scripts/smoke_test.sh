#!/usr/bin/env bash
set -e

echo "=== Installing APK ==="
adb install -r app/build/outputs/apk/debug/*.apk

echo "=== Clearing logcat ==="
adb logcat -c

echo "=== Launching App ==="
adb shell am start -W -n com.proxymax.app/com.proxymax.MainActivity || true

echo "=== Waiting 10s ==="
sleep 10

echo "=== Capturing logs ==="
adb logcat -d -v time > /tmp/app_logcat.txt 2>&1 || true

adb logcat -d -v time \
  | grep -E "AndroidRuntime|FATAL EXCEPTION|Process: com\.proxymax|WM-Worker|Caused by" \
  > /tmp/crash_log.txt 2>&1 || true

echo "=== Crash / Worker log ==="
tail -60 /tmp/crash_log.txt || echo "(no crash detected)"

echo "=== App still running? ==="
adb shell pidof com.proxymax.app > /dev/null 2>&1 \
  && echo "App is running!" \
  || { echo "App crashed or not running"; exit 1; }
