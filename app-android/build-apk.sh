#!/usr/bin/env bash
# Build the signed release APK into app-android/dist/Raphael-v<version>.apk
#
#   ./build-apk.sh            build only
#   ./build-apk.sh --install  build, then install on the phone connected over adb (USB or Wi-Fi)
#
# The version comes from versionName in app/build.gradle.kts — bump versionCode/versionName there first.
set -euo pipefail

# Gradle breaks on the space in "Raphael AI", so always build via the space-free symlink.
PROJECT="$HOME/raphael/app-android"
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
export JAVA_HOME

cd "$PROJECT"

if [[ ! -f keystore.properties ]]; then
  echo "✗ keystore.properties not found in $PROJECT — the release signing key is missing." >&2
  echo "  (It points to ~/.android/raphael-release.jks; restore both from your backup.)" >&2
  exit 1
fi

VERSION=$(grep -m1 'versionName' app/build.gradle.kts | sed -E 's/.*"(.*)".*/\1/')
OUT="dist/Raphael-v${VERSION}.apk"

echo "▶ Building Raphael v${VERSION} (release)…"
./gradlew :app:assembleRelease -q

mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk "$OUT"
SIZE=$(du -h "$OUT" | cut -f1)
echo "✓ Built $PROJECT/$OUT ($SIZE)"

if [[ "${1:-}" == "--install" ]]; then
  DEVICE=$("$ADB" devices | awk 'NR>1 && $2=="device" && $1 !~ /emulator/ {print $1; exit}')
  if [[ -z "$DEVICE" ]]; then
    echo "✗ No phone connected over adb. Plug it in (or pair Wi-Fi debugging) and try again." >&2
    exit 1
  fi
  echo "▶ Installing on $DEVICE…"
  if ! "$ADB" -s "$DEVICE" install -r "$OUT"; then
    echo "✗ Install failed. If the phone has a build from Android Studio, uninstall Raphael first" >&2
    echo "  (different signing key), then run this again." >&2
    exit 1
  fi
  echo "✓ Installed"
fi
