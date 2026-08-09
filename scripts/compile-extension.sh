#!/usr/bin/env bash
#
# compile-extension.sh — compila UNA extensión de keiyoushi (o yuzono) desde
# su código fuente (Kotlin) a un jar JVM limpio que miwayomi puede cargar,
# SIN decompilar APKs (adiós VerifyError de dex2jar).
#
# USO:
#   scripts/compile-extension.sh <dir-de-la-extension> [data-dir]
#
#   <dir-de-la-extension>  carpeta que contiene build.gradle.kts + src/
#                         ej: /home/asking/Escritorio/extensions-source/src/en/mangapill
#   [data-dir]            (opcional) dir de datos de miwayomi; por defecto data/
#                         puede ser ruta absoluta. El jar va a $DATA/extensions/
#
# REQUISITOS: gradle wrapper del repo miwayomi, python3, JDK 21, y el fat jar
# ya construido (./gradlew :server:shadowJar).
#
# NOTA: cubre extensiones "simples" con un `abstract class X : HttpSource`.
# Las que usan lib-multisrc (Madara, MangaBox, etc.) o constructores las
# cubrirá el SourceCompiler integrado (próximo hito).

set -euo pipefail

EXT_DIR="${1:?Usa: $0 <dir-de-la-extension> [data-dir]}"
DATA_DIR="${2:-data}"
MIWAYOMI_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FAT_JAR="$MIWAYOMI_ROOT/server/build/libs/miwayomi-all.jar"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

if [[ ! -f "$EXT_DIR/build.gradle.kts" || ! -d "$EXT_DIR/src" ]]; then
  echo "No es una carpeta de extensión válida: $EXT_DIR" >&2
  exit 1
fi
[[ -f "$FAT_JAR" ]] || { echo "Fat jar no existe; corre ./gradlew :server:shadowJar" >&2; exit 1; }

# --- 1) Leer metadata del DSL keiyoushi { } -------------------------------
NAME="$(grep -hoP 'name\s*=\s*"\K[^"]+' "$EXT_DIR/build.gradle.kts" | head -1 || true)"
LANG="$(grep -hoP 'lang\s*=\s*"\K[^"]+' "$EXT_DIR/build.gradle.kts" | head -1 || true)"
BASEURL="$(grep -hoP 'baseUrl\s*=\s*"\K[^"]+' "$EXT_DIR/build.gradle.kts" | head -1 || true)"
VERSIONCODE="$(grep -hoP 'versionCode\s*=\s*\K[0-9]+' "$EXT_DIR/build.gradle.kts" | head -1 || true)"
NAME="${NAME:-$(basename "$EXT_DIR")}"
LANG="${LANG:-en}"
BASEURL="${BASEURL:-https://example.com}"
VERSIONCODE="${VERSIONCODE:-1}"
echo "Metadata: name=$NAME lang=$LANG baseUrl=$BASEURL versionCode=$VERSIONCODE"

# --- 2) Localizar la clase fuente y su paquete -----------------------------
CLASS_FILE="$(grep -rlP ': HttpSource' "$EXT_DIR/src" --include='*.kt' | head -1 || true)"
if [[ -z "$CLASS_FILE" ]]; then
  echo "No se encontró ninguna clase que extienda HttpSource en $EXT_DIR/src" >&2
  echo "(extensiones con lib-multisrc o clases concretas aún no soportadas por este script)" >&2
  exit 1
fi
PKG_BASE="$(grep -hoP '^package\s+\K.+' "$CLASS_FILE" | head -1)"
CLASS="$(grep -hoP '^abstract class\s+\K[A-Za-z0-9_]+' "$CLASS_FILE" | head -1 || true)"
if [[ -z "$CLASS" ]]; then
  echo "La clase en $CLASS_FILE debe ser 'abstract class X : HttpSource'" >&2
  exit 1
fi
echo "package=$PKG_BASE class=$CLASS"

# --- 3) Calcular id (MD5 de name/lang/versionId — mismo que Tachiyomi) -----
ID="$(python3 -c "
import hashlib
key='${NAME,,}/${LANG,,}/1'
d=hashlib.md5(key.encode()).digest()
print(int.from_bytes(d[:8],'big') & 0x7FFFFFFFFFFFFFFF)
")"
echo "id calculado: $ID"

# --- 4) Generar la factoría (equivalente a ExtensionGenerated de keiyoushi) --
FACTORY="$WORK/factory.kt"
cat > "$FACTORY" <<EOF
package $PKG_BASE

import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.SourceFactory

internal class ExtensionGenerated : SourceFactory {
    override fun createSources(): List<MangaSource> = listOf(
        object : $CLASS() {
            override val name = "$NAME"
            override val lang = "$LANG"
            override val id = ${ID}L
            override val baseUrl = "$BASEURL"
        },
    )
}
EOF

# --- 5) Proyecto gradle temporal y compilación -----------------------------
mkdir -p "$WORK/proj/src/main/kotlin/keiyoushi/annotation"
cat > "$WORK/proj/settings.gradle.kts" <<'EOF'
rootProject.name = "mw-ext"
EOF
cat > "$WORK/proj/build.gradle.kts" <<EOF
plugins { kotlin("jvm") version "2.2.0" }
repositories { mavenCentral() }
val miwayomiFatJar = "$FAT_JAR"
val extSource = "$EXT_DIR/src"
dependencies { implementation(files(miwayomiFatJar)) }
kotlin {
    jvmToolchain(21)
    sourceSets { main { kotlin.srcDir(extSource) } }
}
EOF
cat > "$WORK/proj/src/main/kotlin/keiyoushi/annotation/Source.kt" <<'EOF'
package keiyoushi.annotation

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class Source
EOF
cp "$FACTORY" "$WORK/proj/src/main/kotlin/factory.kt"

echo "Compilando contra el fat jar de miwayomi..."
set +e
(cd "$WORK/proj" && "$MIWAYOMI_ROOT/gradlew" -p "$WORK/proj" compileKotlin --console=plain 2>&1) | tee "$WORK/build.log" >/dev/null
RC=${PIPESTATUS[0]}
set -e
if [[ $RC -ne 0 ]] || grep -qE '^e: ' "$WORK/build.log"; then
  echo "La compilación falló (¿depende de lib-multisrc?):" >&2
  grep -E '^e: ' "$WORK/build.log" | head -30 || true
  exit 1
fi

# --- 6) Empaquetar jar con marcador ---------------------------------------
if [[ "$DATA_DIR" = /* ]]; then OUT_ROOT="$DATA_DIR"; else OUT_ROOT="$MIWAYOMI_ROOT/$DATA_DIR"; fi
OUT_DIR="$OUT_ROOT/extensions"
mkdir -p "$OUT_DIR"
JAR_OUT="$OUT_DIR/$(basename "$EXT_DIR")-jvm.jar"
mkdir -p "$WORK/jarout/META-INF"
cat > "$WORK/jarout/META-INF/miwayomi-extension.json" <<EOF
{"pkgName":"$PKG_BASE","name":"$NAME","versionName":"$VERSIONCODE","versionCode":$VERSIONCODE,"isNsfw":false,"isAnime":false,"sourceClasses":[],"factoryClass":"$PKG_BASE.ExtensionGenerated"}
EOF
cp -r "$WORK/proj/build/classes/kotlin/main/." "$WORK/jarout/"
(cd "$WORK/jarout" && jar cf "$JAR_OUT" .)
echo "✅ Extensión compilada y copiada a: $JAR_OUT"
echo "Reinicia miwayomi y se cargará automáticamente."
