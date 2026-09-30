#!/usr/bin/env bash
# Install an ARISE build on a phone over wireless adb, after checking it.
#
# This script builds nothing: GitHub Actions (.github/workflows/android.yml) compiles
# ARISE. Here we only download, verify and `adb install -r`.
#
#   scripts/arise-install.sh PHONE=<ip>:<port>              newest green run on main
#   scripts/arise-install.sh PHONE=... BRANCH=dev           newest green run on dev
#   scripts/arise-install.sh PHONE=... RUN=<run id>         a specific run
#   scripts/arise-install.sh PHONE=... RELEASE=v1.1.0       a release's APK
#   scripts/arise-install.sh APK=path/to/arise.apk --check-only
#
# Needs: gh (logged in), java, and adb, aapt2 and apksigner from the Android SDK (ANDROID_HOME).
# Safety: it refuses any APK that isn't com.minimaldesigner.arise signed with the expected
# key, never uninstalls, and never passes -d (downgrade).
set -euo pipefail

PKG=com.minimaldesigner.arise
# SHA-256 of the signing cert. The default is the official ARISE key; a fork signs with
# its own key, so set ARISE_CERT_SHA256 to that one's fingerprint.
CERT=${ARISE_CERT_SHA256:-dc2d815b8fe8dea34ef07b47723a5215e9fcdac7835ecb4ed3349aa15870552f}
REPO=${REPO:-minimal-designer/arise}
BRANCH=${BRANCH:-main}
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
BT=$SDK/build-tools/$(ls "$SDK/build-tools" 2>/dev/null | sort -V | tail -1)
ADB=$SDK/platform-tools/adb

PHONE="" RUN="" RELEASE="" APK="" CHECK_ONLY=0
for a in "$@"; do
  case "$a" in
    PHONE=*) PHONE=${a#PHONE=} ;;
    RUN=*) RUN=${a#RUN=} ;;
    RELEASE=*) RELEASE=${a#RELEASE=} ;;
    BRANCH=*) BRANCH=${a#BRANCH=} ;;
    APK=*) APK=${a#APK=} ;;
    --check-only) CHECK_ONLY=1 ;;
    -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
    *) echo "unknown argument: $a" >&2; exit 2 ;;
  esac
done

die() { echo "arise-install: $*" >&2; exit 1; }
[ -x "$BT/aapt2" ] || die "no Android build-tools under $SDK: set ANDROID_HOME"

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

# 1. Get the APK.
if [ -z "$APK" ] && [ -n "$RELEASE" ]; then
  echo "Downloading the APK from release $RELEASE"
  gh release download "$RELEASE" -R "$REPO" -p '*.apk' -D "$WORK"
  APK=$(find "$WORK" -name '*.apk' | head -1)
  [ -n "$APK" ] || die "release $RELEASE has no APK"
elif [ -z "$APK" ]; then
  if [ -z "$RUN" ]; then
    RUN=$(gh run list -R "$REPO" -w android.yml -b "$BRANCH" -s success -L 1 --json databaseId -q '.[0].databaseId')
    [ -n "$RUN" ] || die "no successful android.yml run on $BRANCH yet"
  fi
  echo "Downloading arise-apk from run $RUN"
  gh run download "$RUN" -R "$REPO" -n arise-apk -D "$WORK"
  APK=$(find "$WORK" -name '*.apk' | head -1)
  [ -n "$APK" ] || die "run $RUN has no APK"
fi
[ -f "$APK" ] || die "no such file: $APK"
echo "APK: $(basename "$APK")"

# 2. Right app?
got=$("$BT/aapt2" dump packagename "$APK")
[ "$got" = "$PKG" ] || die "package is '$got', expected $PKG - refusing"

# 3. Right key? (A different key would make Android refuse the update, and the only
#    way past that is an uninstall, which wipes the data.)
"$BT/apksigner" verify --print-certs "$APK" > "$WORK/certs.txt" || die "signature does not verify"
grep -qi "SHA-256 digest: $CERT" "$WORK/certs.txt" || { cat "$WORK/certs.txt" >&2; die "not signed with the expected key - refusing"; }
version=$("$BT/aapt2" dump badging "$APK" | sed -n "s/.*versionCode='\([0-9]*\)' versionName='\([^']*\)'.*/\2 (\1)/p")
echo "Verified: $PKG $version, expected key"

[ "$CHECK_ONLY" = 1 ] && { echo "Check only - not installing."; exit 0; }

# 4. Install over wireless adb.
[ -n "$PHONE" ] || die "set PHONE=ip:port (Wireless debugging's current connect port)"
"$ADB" connect "$PHONE" >/dev/null
"$ADB" -s "$PHONE" get-state >/dev/null 2>&1 || die "phone $PHONE is not reachable - is Wireless debugging on and paired?"
"$ADB" -s "$PHONE" install -r "$APK"

echo "Installed: $("$ADB" -s "$PHONE" shell dumpsys package "$PKG" | sed -n 's/^ *versionName=//p' | head -1)"
