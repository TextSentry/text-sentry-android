#!/usr/bin/env bash
# Record the real Android starting window and welcome screen on the disposable CI emulator.
set -euo pipefail
output=app/build/reports/androidTests/launch
mkdir -p "$output"
adb install -r app/build/outputs/apk/sideload/debug/app-sideload-debug.apk
adb shell pm clear org.textsentry.app
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell screenrecord --size 720x1280 --time-limit 5 /sdcard/textsentry-launch.mp4 &
recorder=$!
sleep 1
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n org.textsentry.app/.SetupActivity
wait "$recorder"
adb pull /sdcard/textsentry-launch.mp4 "$output/launch.mp4"
adb exec-out screencap -p > "$output/welcome.png"

# Capture Home using the synthetic account and reply preferences from its UI test.
adb install -r app/build/outputs/apk/androidTest/sideload/debug/app-sideload-debug-androidTest.apk
adb shell am instrument -w -e class org.textsentry.app.HomeStopRepliesTest -e captureHome true \
  org.textsentry.app.test/androidx.test.runner.AndroidJUnitRunner
adb exec-out run-as org.textsentry.app cat cache/home.png > "$output/home.png"
test -s "$output/home.png"
