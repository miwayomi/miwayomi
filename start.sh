#!/usr/bin/env bash
# Enciende miwayomi (+ FlareSolverr opcional) en segundo plano.
# Logs: /tmp/miwayomi.log y /tmp/fs_src.log
set -e
cd "$(dirname "$0")"

DATA="$(pwd)/data"
PORT=4567
FS_PORT=8191
FS_URL="http://127.0.0.1:${FS_PORT}"

# 1) FlareSolverr (opcional, auto-solve de Cloudflare).
#    Necesita el código fuente en /tmp/flaresolverr-src (ver README, opción C).
#    Si no está o no lo quieres, quita el --flaresolverr de abajo y el modal
#    manual sigue funcionando.
if [ -f /tmp/flaresolverr-src/src/flaresolverr.py ] && [ -x /tmp/fsvenv/bin/python ]; then
  if ! curl -s -o /dev/null "http://127.0.0.1:${FS_PORT}"; then
    echo "> Arrancando FlareSolverr en :${FS_PORT} ..."
    nohup /tmp/fsvenv/bin/python /tmp/flaresolverr-src/src/flaresolverr.py --port "${FS_PORT}" \
      > /tmp/fs_src.log 2>&1 &
  else
    echo "> FlareSolverr ya estaba corriendo en :${FS_PORT}"
  fi
  FS_ARG="--flaresolverr ${FS_URL}"
else
  echo "> FlareSolverr no disponible (sin /tmp/flaresolverr-src) -> solo modal manual."
  FS_ARG=""
fi

# 2) miwayomi
echo "> Arrancando miwayomi en :${PORT} ..."

# Runtime LIGERO: si existe la distribución instalada, se usa directamente
# (un solo proceso java, SIN daemons de Gradle/Kotlin -> menos RAM).
# Para generarla:  ./gradlew :server:installDist   (y ./gradlew --stop)
BIN="server/build/install/server/bin/server"
MEM_DEFAULT="-Xmx512m -Xms64m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC"
MEM="${MIWAYOMI_MEM:-$MEM_DEFAULT}"
if [ -x "${BIN}" ]; then
  echo "> Usando distribución instalada (ligera): ${BIN}"
  echo "> Memoria JVM: ${MEM}"
  JAVA_OPTS="${MEM}" nohup "${BIN}" --data "${DATA}" --port "${PORT}" ${FS_ARG} \
    > /tmp/miwayomi.log 2>&1 &
else
  echo "> Sin distribución instalada -> gradlew (genera con: ./gradlew :server:installDist)"
  nohup ./gradlew :server:run --args="--data ${DATA} --port ${PORT} ${FS_ARG}" \
    --console=plain > /tmp/miwayomi.log 2>&1 &
fi

echo
echo "  WebUI:  http://localhost:${PORT}"
echo "  API:    http://localhost:${PORT}/api/v1"
echo "  Logs:   /tmp/miwayomi.log   (/tmp/fs_src.log)"
echo "  RAM:    un solo java, heap -Xmx512m (ajusta con MIWAYOMI_MEM=...)"
