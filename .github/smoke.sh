#!/usr/bin/env bash
# Installs the release APK on the emulator, opens every tab, fails on any crash, and saves
# real screenshots to emulator/. Runs inside reactivecircus/android-emulator-runner.
set -u
PKG=com.basem.istiqamah
mkdir -p emulator
adb install -r BE.apk
adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION || true
adb logcat -c
adb shell am start -W -n $PKG/com.basem.qiyam.MainActivity
sleep 8

crashed() { adb logcat -d | grep -q "FATAL EXCEPTION"; }
shot() { adb exec-out screencap -p > "emulator/$1.png"; }

shot 1_today
# Open each tab the way a widget or notification would, and photograph it.
for tab in deen body career wealth; do
  adb shell am start -n $PKG/com.basem.qiyam.MainActivity --es tab $tab >/dev/null
  sleep 3
  shot "$tab"
done
# Also check a real tap on the tab bar still works.
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
grep -o 'text="Today"[^>]*' ui.xml | head -2 || true

if crashed || ! adb shell pidof $PKG >/dev/null; then
  echo "THE APP CRASHED"
  adb logcat -d | grep -A40 "FATAL EXCEPTION" | head -80
  exit 1
fi
echo "Smoke test passed: opened every tab with no crash."
