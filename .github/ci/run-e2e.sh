#!/usr/bin/env bash
# Runs the end-to-end test on the Android emulator started by android-emulator-runner.
# Results, screenshots and logcat end up in ./e2e.
set -uo pipefail

PKG=com.example.plantshome
mkdir -p e2e/screens

adb wait-for-device
# -g grants runtime permissions (notifications) up front, as a user would on first use.
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb install -r -g app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c

# A freshly booted emulator can show a lock screen or a system dialog ("System UI isn't
# responding") that takes window focus from the app. Wait for boot, unlock, close dialogs.
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
sleep 10
adb shell input keyevent 82
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null
sleep 3

adb shell am instrument -w -r \
  -e authEmulatorHost 10.0.2.2 \
  -e class com.example.plantshome.e2e.FullJourneyTest \
  "$PKG.test/com.example.plantshome.e2e.E2ERunner" | tee e2e/instrument.txt

adb exec-out run-as "$PKG" tar -c -C files e2e | tar -x -C e2e/screens --strip-components=1 || true
adb logcat -d > e2e/logcat.txt || true

grep -q "OK (1 test)" e2e/instrument.txt
