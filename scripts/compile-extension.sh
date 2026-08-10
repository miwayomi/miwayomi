#!/usr/bin/env bash
#
# compile-extension.sh — compiles ONE keiyoushi (or yuzono) extension from
# its source code (Kotlin) into a clean JVM jar that miwayomi can load,
# WITHOUT decompiling APKs (goodbye VerifyError from dex2jar).
#
# USAGE:
#   scripts/compile-extension.sh <extension-dir> [data-dir]
#
#   <extension-dir>  folder containing build.gradle.kts + src/
#                    e.g.: /home/asking/Escritorio/extensions-source/src/en/mangapill
#   [data-dir]       (optional) miwayomi data dir; defaults to data/
#                    can be an absolute path. The jar goes to $DATA/extensions/
#
# REQUIREMENTS: gradle wrapper from the miwayomi repo, python3, JDK 21, and the
# fat jar already built (./gradlew :server:shadowJar).
#
# NOTE: covers "simple" extensions with an `abstract class X : HttpSource`.
# Those that use lib-multisrc (Madara, MangaBox, etc.) or constructors will be
# covered by the built-in SourceCompiler (next milestone).

set -euo pipefail

EXT_DIR="${1:?Usage: $0 <extension-dir> [data-dir]}"
DATA_DIR="${2:-data}"
MIWAYOMI_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FAT_JAR="$MIWAYOMI_ROOT/server/build/libs/miwayomi-all.jar"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

if [[ ! -f "$EXT_DIR/build.gradle.kts" || ! -d "$EXT_DIR/src" ]]; then
  echo "Not a valid extension folder: $EXT_DIR" >&2
  exit 1
fi
[[ -f "$FAT_JAR" ]] || { echo "Fat jar does not exist; run ./gradlew :server:shadowJar" >&2; exit 1; }

# --- 1) Read metadata from the keiyoushi DSL { } ---------------------------
NAME="$(grep -hoP 'name\s*=\s*"\K[^"]+' "$EXT_DIR/build.gradle.kts" | head -1 || true)"
LANG="$(grep -hoP 'lang\s*=\s*"\K[^"]+' "$EXT_DIR/build.gradle.kts" | head -1 || true)"
BASEURL="$(grep -hoP 'baseUrl\s*=\s*"\K[^"]+' "$EXT_DIR/build.gradle.kts" | head -1 || true)"
VERSIONCODE="$(grep -hoP 'versionCode\s*=\s*\K[0-9]+' "$EXT_DIR/build.gradle.kts" | head -1 || true)"
NAME="${NAME:-$(basename "$EXT_DIR")}"
LANG="${LANG:-en}"
BASEURL="${BASEURL:-https://example.com}"
VERSIONCODE="${VERSIONCODE:-1}"
echo "Metadata: name=$NAME lang=$LANG baseUrl=$BASEURL versionCode=$VERSIONCODE"

# --- 2) Locate the source class and its package ----------------------------
CLASS_FILE="$(grep -rlP ': HttpSource' "$EXT_DIR/src" --include='*.kt' | head -1 || true)"
if [[ -z "$CLASS_FILE" ]]; then
  echo "No class extending HttpSource found in $EXT_DIR/src" >&2
  echo "(extensions with lib-multisrc or concrete classes are not yet supported by this script)" >&2
  exit 1
fi
PKG_BASE="$(grep -hoP '^package\s+\K.+' "$CLASS_FILE" | head -1)"
CLASS="$(grep -hoP '^abstract class\s+\K[A-Za-z0-9_]+' "$CLASS_FILE" | head -1 || true)"
if [[ -z "$CLASS" ]]; then
  echo "The class in $CLASS_FILE must be 'abstract class X : HttpSource'" >&2
  exit 1
fi
echo "package=$PKG_BASE class=$CLASS"

# --- 3) Compute id (MD5 of name/lang/versionId — same as Tachiyomi) ---------
ID="$(python3 -c "
import hashlib
key='${NAME,,}/${LANG,,}/1'
d=hashlib.md5(key.encode()).digest()
print(int.from_bytes(d[:8],'big') & 0x7FFFFFFFFFFFFFFF)
")"
echo "computed id: $ID"

# --- 4) Generate the factory (equivalent to keiyoushi's ExtensionGenerated) --
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

# --- 5) Temporary gradle project and compilation ---------------------------
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

echo "Compiling against the miwayomi fat jar..."
set +e
(cd "$WORK/proj" && "$MIWAYOMI_ROOT/gradlew" -p "$WORK/proj" compileKotlin --console=plain 2>&1) | tee "$WORK/build.log" >/dev/null
RC=${PIPESTATUS[0]}
set -e
if [[ $RC -ne 0 ]] || grep -qE '^e: ' "$WORK/build.log"; then
  echo "Compilation failed (does it depend on lib-multisrc?):" >&2
  grep -E '^e: ' "$WORK/build.log" | head -30 || true
  exit 1
fi

# --- 6) Package jar with marker -------------------------------------------
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
echo "✅ Extension compiled and copied to: $JAR_OUT"
echo "Restart miwayomi and it will be loaded automatically."
