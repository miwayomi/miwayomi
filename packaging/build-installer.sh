#!/usr/bin/env bash
# Genera un instalador .exe de Windows con NSIS (desde Linux/macOS).
# Requisito: sudo apt install nsis  (o brew install nsis en macOS)
# Uso:
#   ./packaging/build-installer.sh [version]                 # sin JRE (requiere Java en el equipo Windows)
#   JRE_ZIP=temurin-21-windows-x64.zip ./packaging/build-installer.sh 0.2.0
#     (JRE_ZIP = ruta a un zip del JDK/JRE de Windows para que el instalador sea autónomo)
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
  echo "miwayomi-all.jar no encontrado. Construyelo con: ./gradlew :server:shadowJar"
  exit 1
fi

cp "$JAR" "$STAGE/miwayomi-all.jar"
cp miwayomi.bat "$STAGE/miwayomi.bat"

if [ -n "$JRE_ZIP" ]; then
  echo "> Empaquetando JRE de Windows: $JRE_ZIP"
  mkdir -p "$STAGE/jre"
  python3 - "$JRE_ZIP" "$STAGE/jre" <<'EOF'
import zipfile, sys
zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])
EOF
  # si el zip deja una subcarpeta (jdk-XX...), sube su contenido a jre/
  if ! [ -f "$STAGE/jre/bin/java.exe" ]; then
    for d in "$STAGE"/jre/*/; do
      if [ -f "$d/bin/java.exe" ]; then
        mv "$d"* "$STAGE/jre/"
        break
      fi
    done
  fi
  echo "> JRE detectado: $(ls "$STAGE"/jre/bin/java.exe 2>/dev/null || echo 'NO (revisa el zip)')"
fi

if ! command -v makensis >/dev/null 2>&1; then
  echo "ERROR: makensis no instalado. Instálalo con: sudo apt install nsis   (o brew install nsis)"
  exit 1
fi

echo "> Generando instalador (NSIS) v$VERSION ..."
makensis -DVERSION="$VERSION" packaging/miwayomi.nsi >/dev/null
echo "> Listo: $DIST/miwayomi-setup.exe"
