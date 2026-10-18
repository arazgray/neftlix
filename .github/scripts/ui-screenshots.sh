#!/usr/bin/env bash
# گرفتن اسکرین‌شات از صفحه‌های برنامه روی امولاتور ۱۰۸۰p در دو چگالی:
# الف) چگالی خود پروفایل تلویزیون (۳۲۰dpi → فضای طراحی ۹۶۰dp)
# ب) ۱۶۰dpi (مثل دستگاهی که چگالی ۱ گزارش می‌کند → ضریب مقیاس تا سقف می‌رود)
#
# امولاتور دبیرستانی می‌تواند کند باشد (بدون KVM)؛ پس برای هر مرحله صبر
# می‌کنیم تا سرویس‌ها/پنجرهٔ برنامه واقعاً آماده شوند، نه با تأخیر ثابت.
set -u

PKG=com.arazgray.neftlix
APK=app/build/outputs/apk/debug/app-debug.apk
OUT=screenshots
mkdir -p "$OUT"

log() { echo; echo "=== $* ==="; }

wait_until() { # $1=seconds $2=description  rest=command
  local deadline=$((SECONDS + $1)) desc=$2
  shift 2
  while [ $SECONDS -lt $deadline ]; do
    if "$@" >/dev/null 2>&1; then
      echo "ok: $desc"
      return 0
    fi
    sleep 3
  done
  echo "TIMEOUT: $desc"
  return 1
}

services_ready() {
  [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '[:space:]')" = "1" ] || return 1
  for svc in package input settings window activity; do
    adb shell service check "$svc" 2>/dev/null | grep -q found || return 1
  done
  return 0
}

app_window_ready() {
  adb shell dumpsys window 2>/dev/null | grep -E "mCurrentFocus=" | grep -q "$PKG"
}

shot() {
  local file="$OUT/$1.png"
  for attempt in 1 2 3; do
    adb exec-out screencap -p > "$file" 2>/dev/null || true
    if [ -s "$file" ] && [ "$(wc -c < "$file")" -gt 5000 ]; then
      echo "shot: $1 ($(wc -c < "$file") bytes)"
      return 0
    fi
    sleep 3
  done
  echo "shot FAILED: $1"
}

tap() { # $1=keyevent  $2=delay
  adb shell input keyevent "$1" >/dev/null 2>&1 || true
  sleep "${2:-1}"
}

launch_app() { # $1=extra settle seconds
  adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
  sleep 3
  # یک کلید جهت‌دار روی لانچر: دستگاه از حالت لمسی خارج می‌شود تا فوکوس
  # خودکار برنامه (requestFocus) مثل تلویزیون واقعی کار کند.
  adb shell input keyevent KEYCODE_DPAD_DOWN >/dev/null 2>&1 || true
  adb shell am start -n "$PKG/.MainActivity" 2>&1 | tail -1
  wait_until 300 "app window focused" app_window_ready || true
  sleep "${1:-25}"
}

capture_pass() { # $1 = label prefix
  local p="$1"
  launch_app 30
  shot "$p-01-home"
  tap KEYCODE_DPAD_DOWN 1.2; tap KEYCODE_DPAD_DOWN 1.2; tap KEYCODE_DPAD_DOWN 1.2
  sleep 2
  shot "$p-02-rails"
  tap KEYCODE_DPAD_CENTER 1
  sleep 12
  shot "$p-03-details"
  tap KEYCODE_DPAD_DOWN 1.5
  tap KEYCODE_DPAD_CENTER 2
  shot "$p-04-level1-open"
  tap KEYCODE_DPAD_DOWN 1.5
  tap KEYCODE_DPAD_CENTER 2
  shot "$p-05-level2-open"
  tap KEYCODE_DPAD_DOWN 1.5
  tap KEYCODE_DPAD_CENTER 2
  shot "$p-06-level3-open"
  # پایین صفحهٔ جزئیات: نظرات
  for i in $(seq 1 35); do tap KEYCODE_DPAD_DOWN 0.5; done
  sleep 1
  shot "$p-07-comments"
  tap KEYCODE_DPAD_CENTER 2
  shot "$p-08-comments-open"
  # پایین صفحهٔ اصلی: ریل ژانرها
  launch_app 25
  for i in $(seq 1 45); do tap KEYCODE_DPAD_DOWN 0.35; done
  sleep 2
  shot "$p-09-home-bottom"
  # جست‌وجو: زدن روی نوار جست‌وجو، تایپ، اینتر
  adb shell input tap 1100 110 >/dev/null 2>&1 || true
  sleep 1
  adb shell input text "dark" >/dev/null 2>&1 || true
  sleep 1
  adb shell input keyevent KEYCODE_ENTER >/dev/null 2>&1 || true
  sleep 16
  tap KEYCODE_DPAD_DOWN 1.5
  shot "$p-10-search"
}

log "waiting for the emulator system"
adb wait-for-device
wait_until 1500 "android services up" services_ready || true

log "device hygiene"
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true
adb shell settings put global stay_on_while_plugged_in 3 || true
adb shell settings put system screen_off_timeout 2147483647 || true
adb shell settings put global policy_control immersive.full=* || true
adb logcat -c || true

log "display before install"
adb shell wm size || true
adb shell wm density || true

log "installing the app"
installed=0
for i in 1 2 3 4 5 6; do
  out=$(adb install -r "$APK" 2>&1 | tr -d '\r' | tail -2)
  echo "$out"
  if echo "$out" | grep -q "Success"; then installed=1; break; fi
  sleep 12
done
if [ "$installed" != 1 ]; then echo "APK INSTALL FAILED"; exit 1; fi

capture_pass "tv320"

log "switching density to 160"
adb shell wm density 160 || true
adb logcat -c || true
capture_pass "hdpi160"

log "app log (errors and crashes)"
adb logcat -d -v brief 2>/dev/null | grep -E "arazgray|AndroidRuntime|FATAL|ANR |Glide" | tail -160 || true

log "done"
