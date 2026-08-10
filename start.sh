#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

DATA="$(pwd)/data"
PORT=4567
FS_PORT=8191
FS_URL="http://127.0.0.1:${FS_PORT}"

if [ -f /tmp/flaresolverr-src/src/flaresolverr.py ] && [ -x /tmp/fsvenv/bin/python ]; then
  if ! curl -s -o /dev/null "http://127.0.0.1:${FS_PORT}"; then
    echo "> Starting FlareSolverr on :${FS_PORT} ..."
    nohup /tmp/fsvenv/bin/python /tmp/flaresolverr-src/src/flaresolverr.py --port "${FS_PORT}" \
      > /tmp/fs_src.log 2>&1 &
  else
    echo "> FlareSolverr already running on :${FS_PORT}"
  fi
  FS_ARG="--flaresolverr ${FS_URL}"
else
  echo "> FlareSolverr unavailable (no /tmp/flaresolverr-src) -> manual modal only."
  FS_ARG=""
fi

echo "> Starting miwayomi on :${PORT} ..."

MEM_DEFAULT="-Xmx512m -Xms64m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC"
MEM="${MIWAYOMI_MEM:-$MEM_DEFAULT}"

# Runnable fat jar (downloaded from Releases or built with :server:shadowJar)
JAR="${MIWAYOMI_JAR:-}"
if [ -z "${JAR}" ] && [ -f "miwayomi-all.jar" ]; then JAR="$(pwd)/miwayomi-all.jar"; fi
if [ -z "${JAR}" ] && [ -f "server/build/libs/miwayomi-all.jar" ]; then JAR="$(pwd)/server/build/libs/miwayomi-all.jar"; fi

# Apply a pending update if one exists (data/update/*.jar.new)
UPDDIR="${DATA}/update"
if [ -d "${UPDDIR}" ] && [ -n "${JAR}" ]; then
  for NEW in "${UPDDIR}"/*.jar.new; do
    [ -e "$NEW" ] || continue
    echo "> Applying update: $(basename "$NEW") -> ${JAR}"
    cp "${JAR}" "${JAR}.bak" 2>/dev/null || true
    cp "$NEW" "${JAR}"
    rm -f "$NEW"
  done
  rm -f "${UPDDIR}"/*.json 2>/dev/null || true
fi

if [ -n "${JAR}" ]; then
  echo "> Using runnable JAR: ${JAR}"
  echo "> JVM memory: ${MEM}"
  JAVA_OPTS="${MEM}" nohup java -jar "${JAR}" --data "${DATA}" --port "${PORT}" --no-open ${FS_ARG} \
    > /tmp/miwayomi.log 2>&1 &
elif [ -x "server/build/install/server/bin/server" ]; then
  echo "> Using installed distribution (lightweight): server/build/install/server/bin/server"
  echo "> JVM memory: ${MEM}"
  JAVA_OPTS="${MEM}" nohup server/build/install/server/bin/server --data "${DATA}" --port "${PORT}" --no-open ${FS_ARG} \
    > /tmp/miwayomi.log 2>&1 &
else
  echo "> No JAR or distribution -> gradlew (build the jar with: ./gradlew :server:shadowJar)"
  nohup ./gradlew :server:run --args="--data ${DATA} --port ${PORT} --no-open ${FS_ARG}" \
    --console=plain > /tmp/miwayomi.log 2>&1 &
fi

echo
echo "  WebUI:  http://localhost:${PORT}"
echo "  API:    http://localhost:${PORT}/api/v1"
echo "  Logs:   /tmp/miwayomi.log   (/tmp/fs_src.log)"
echo "  RAM:    single java, heap -Xmx512m (tune with MIWAYOMI_MEM=...)"
