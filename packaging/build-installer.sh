#!/usr/bin/env bash
# Builds a Windows .exe installer with NSIS (from Linux/macOS).
# Requirement: sudo apt install nsis  (or brew install nsis on macOS)
# Usage:
#   ./packaging/build-installer.sh [version]                 # without JRE (requires Java on the Windows machine)
#   JRE_ZIP=temurin-21-windows-x64.zip ./packaging/build-installer.sh 0.2.0
#     (JRE_ZIP = path to a Windows JDK/JRE zip so the installer is self-contained)
set -e
cd "$(dirname "$0")/.."

VERSION="${1:-0.2.0}"
JRE_ZIP="${JRE_ZIP:-}"
STAGE="packaging/stage"
DIST="packaging/dist"
rm -rf "$STAGE"; mkdir -p "$STAGE" "$DIST"

JAR="server/build/libs/miwayomi-all.jar"
[ -f "$JAR" ] || JAR="miwayomi-all.jar"
if [ ! -f "$JAR" ]; then
  echo "miwayomi-all.jar not found. Build it with: ./gradlew :server:shadowJar"
  exit 1
fi

cp "$JAR" "$STAGE/miwayomi-all.jar"
cp miwayomi.bat "$STAGE/miwayomi.bat"

if [ -n "$JRE_ZIP" ]; then
  echo "> Bundling Windows JRE: $JRE_ZIP"
  mkdir -p "$STAGE/jre"
  python3 - "$JRE_ZIP" "$STAGE/jre" <<'EOF'
import zipfile, sys
zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])
EOF
  # if the zip leaves a subfolder (jdk-XX...), move its content up to jre/
  if ! [ -f "$STAGE/jre/bin/java.exe" ]; then
    for d in "$STAGE"/jre/*/; do
      if [ -f "$d/bin/java.exe" ]; then
        mv "$d"* "$STAGE/jre/"
        break
      fi
    done
  fi
  echo "> JRE detected: $(ls "$STAGE"/jre/bin/java.exe 2>/dev/null || echo 'NO (check the zip)')"
fi

if ! command -v makensis >/dev/null 2>&1; then
  echo "ERROR: makensis not installed. Install it with: sudo apt install nsis   (or brew install nsis)"
  exit 1
fi

echo "> Generating installer (NSIS) v$VERSION ..."
makensis -DVERSION="$VERSION" packaging/miwayomi.nsi >/dev/null
echo "> Done: $DIST/miwayomi-setup.exe"
