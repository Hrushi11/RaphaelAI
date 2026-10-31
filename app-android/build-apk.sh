#!/usr/bin/env bash
# Build the signed release APK into app-android/dist/Raphael-v<version>.apk
#
#   ./build-apk.sh --v0.5.2              set version 0.5.2 (versionCode +1), then build
#   ./build-apk.sh --version 0.5.2       same
#   ./build-apk.sh --v0.5.2 --install    ...and install on the phone connected over adb
#   ./build-apk.sh                       rebuild the current version (asks before overwriting)
#
# The version is written into versionName in app/build.gradle.kts, and versionCode goes up by 1
# (Android only installs an update over an older version when versionCode increases).
set -euo pipefail

# Gradle breaks on the space in "Raphael AI", so always build via the space-free symlink.
PROJECT="$HOME/raphael/app-android"
GRADLE_FILE="app/build.gradle.kts"
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
export JAVA_HOME

# ---------------------------------------------------------------- arguments
NEW_VERSION=""
INSTALL=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --install) INSTALL=true ;;
    --version) shift; NEW_VERSION="${1:-}" ;;
    --v*) NEW_VERSION="${1#--v}" ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) echo "✗ Unknown option: $1 (try --v0.5.2 or --install)" >&2; exit 1 ;;
  esac
  shift
done
NEW_VERSION="${NEW_VERSION#v}" # accept "v0.5.2" too

cd "$PROJECT"

if [[ ! -f keystore.properties ]]; then
  echo "✗ keystore.properties not found in $PROJECT — the release signing key is missing." >&2
  echo "  (It points to ~/.android/raphael-release.jks; restore both from your backup.)" >&2
  exit 1
fi

CUR_VERSION=$(grep -m1 'versionName' "$GRADLE_FILE" | sed -E 's/.*"(.*)".*/\1/')
CUR_CODE=$(grep -m1 'versionCode' "$GRADLE_FILE" | sed -E 's/[^0-9]*([0-9]+).*/\1/')

# ---------------------------------------------------------------- version bump
if [[ -n "$NEW_VERSION" && "$NEW_VERSION" != "$CUR_VERSION" ]]; then
  if [[ ! "$NEW_VERSION" =~ ^[0-9]+(\.[0-9]+)*$ ]]; then
    echo "✗ Version must look like 0.5.2 (got '$NEW_VERSION')" >&2
    exit 1
  fi
  NEW_CODE=$((CUR_CODE + 1))
  sed -i '' -E "s/versionCode = [0-9]+/versionCode = ${NEW_CODE}/" "$GRADLE_FILE"
  sed -i '' -E "s/versionName = \"[^\"]*\"/versionName = \"${NEW_VERSION}\"/" "$GRADLE_FILE"
  echo "▶ Version ${CUR_VERSION} (code ${CUR_CODE}) → ${NEW_VERSION} (code ${NEW_CODE})"
  VERSION="$NEW_VERSION"
else
  VERSION="$CUR_VERSION"
  if [[ -f "dist/Raphael-v${VERSION}.apk" ]]; then
    echo "⚠ dist/Raphael-v${VERSION}.apk already exists and the version isn't changing."
    echo "  Phones that have it won't take this as an update. Use e.g. ./build-apk.sh --v0.5.2"
    read -r -p "  Rebuild v${VERSION} anyway and overwrite it? [y/N] " answer
    [[ "$answer" =~ ^[Yy]$ ]] || { echo "Cancelled."; exit 0; }
  fi
fi

# ---------------------------------------------------------------- build
OUT="dist/Raphael-v${VERSION}.apk"
echo "▶ Building Raphael v${VERSION} (release)…"
./gradlew :app:assembleRelease -q

mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk "$OUT"
SIZE=$(du -h "$OUT" | cut -f1 | tr -d ' ')
echo "✓ Built $PROJECT/$OUT ($SIZE)"

# ---------------------------------------------------------------- install
if $INSTALL; then
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

# ./build-apk.sh --v0.5.2
# ./build-apk.sh --v0.5.2 --install