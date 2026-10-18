#!/usr/bin/env bash
# گرفتن اسکرین‌شات از صفحه‌های برنامه روی امولاتور ۱۰۸۰p در دو چگالی:
# الف) چگالی خود پروفایل تلویزیون (۳۲۰dpi → فضای طراحی ۹۶۰dp)
# ب) ۱۶۰dpi (مثل دستگاهی که چگالی ۱ گزارش می‌کند → ضریب مقیاس تا سقف می‌رود)
set -u

PKG=com.arazgray.neftlix
APK=app/build/outputs/apk/debug/app-debug.apk
OUT=screenshots
mkdir -p "$OUT"

adb install -r "$APK"

adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true
adb shell settings put global policy_control immersive.full="$PKG" || true

echo "== display =="
adb shell wm size || true
adb shell wm density || true

shot() {
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "shot: $1"
}

tap() { # $1=keyevent  $2=delay seconds
  adb shell input keyevent "$1"
  sleep "${2:-0.6}"
}

# از فوکوس فعلی بیلبورد که پایین می‌رویم وارد ریل‌ها می‌شویم، با OK وارد
# جزئیات می‌شویم و بعد سه سطح تاشو (فصل → دسته → کیفیت) را باز می‌کنیم.
capture() {
  local p="$1"
  adb shell am force-stop "$PKG"
  sleep 1
  adb shell am start -n "$PKG/.MainActivity" >/dev/null
  sleep 25
  shot "$p-01-home"
  tap KEYCODE_DPAD_DOWN 1; tap KEYCODE_DPAD_DOWN 1; tap KEYCODE_DPAD_DOWN 1
  sleep 1.5
  shot "$p-02-rails"
  tap KEYCODE_DPAD_CENTER 1
  sleep 14
  shot "$p-03-details"
  tap KEYCODE_DPAD_DOWN 1.2
  tap KEYCODE_DPAD_CENTER 1.6
  shot "$p-04-level1-open"
  tap KEYCODE_DPAD_DOWN 1.2
  tap KEYCODE_DPAD_CENTER 1.6
  shot "$p-05-level2-open"
  tap KEYCODE_DPAD_DOWN 1.2
  tap KEYCODE_DPAD_CENTER 1.8
  shot "$p-06-level3-open"
}

capture "tv320"
adb shell wm density 160
echo "== display after override =="
adb shell wm density || true
capture "hdpi160"
