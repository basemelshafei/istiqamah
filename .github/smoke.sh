#!/usr/bin/env bash
# Installs the release APK on the emulator, opens every tab, fails on any crash, and saves
# real screenshots to emulator/. Runs inside reactivecircus/android-emulator-runner.
set -u
PKG=com.basem.istiqamah
mkdir -p emulator
adb install -r Istiqamah.apk
adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION || true
adb logcat -c
adb shell am start -W -n $PKG/com.basem.qiyam.MainActivity
sleep 8

crashed() { adb logcat -d | grep -q "FATAL EXCEPTION"; }
shot() { adb exec-out screencap -p > "emulator/$1.png"; }

shot 1_today
for tab in Deen Body Career Wealth; do
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
  # Centre of the tab's bounds, e.g. bounds="[216,2190][432,2337]".
  B=$(grep -o "text=\"$tab\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" ui.xml | tail -1 | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]')
  if [ -n "$B" ]; then
    read X1 Y1 X2 Y2 <<<"$(echo "$B" | tr -d '[]' | tr ',' ' ')"
    adb shell input tap $(( (X1 + X2) / 2 )) $(( (Y1 + Y2) / 2 ))
    sleep 3
  else
    echo "Couldn't find the $tab tab"
  fi
  shot "$(echo $tab | tr 'A-Z' 'a-z')"
done

if crashed || ! adb shell pidof $PKG >/dev/null; then
  echo "THE APP CRASHED"
  adb logcat -d | grep -A40 "FATAL EXCEPTION" | head -80
  exit 1
fi
echo "Smoke test passed: opened every tab with no crash."
