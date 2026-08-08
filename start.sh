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

BIN="server/build/install/server/bin/server"
MEM_DEFAULT="-Xmx512m -Xms64m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC"
MEM="${MIWAYOMI_MEM:-$MEM_DEFAULT}"
if [ -x "${BIN}" ]; then
  echo "> Using installed distribution (lightweight): ${BIN}"
  echo "> JVM memory: ${MEM}"
  JAVA_OPTS="${MEM}" nohup "${BIN}" --data "${DATA}" --port "${PORT}" ${FS_ARG} \
    > /tmp/miwayomi.log 2>&1 &
else
  echo "> No installed distribution -> gradlew (generate with: ./gradlew :server:installDist)"
  nohup ./gradlew :server:run --args="--data ${DATA} --port ${PORT} ${FS_ARG}" \
    --console=plain > /tmp/miwayomi.log 2>&1 &
fi

echo
echo "  WebUI:  http://localhost:${PORT}"
echo "  API:    http://localhost:${PORT}/api/v1"
echo "  Logs:   /tmp/miwayomi.log   (/tmp/fs_src.log)"
echo "  RAM:    single java, heap -Xmx512m (tune with MIWAYOMI_MEM=...)"
