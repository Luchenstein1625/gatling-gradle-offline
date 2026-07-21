#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT_DIR/offline-deps/gradle"
STAGING_DIR="$ROOT_DIR/offline-deps/.gradle-refresh"
IMAGE="gatling-gradle-cache-refresh:local"

cd "$ROOT_DIR"
rm -rf "$STAGING_DIR"
mkdir -p "$STAGING_DIR" "$OUT_DIR"

echo "Construyendo caché limpio con las dependencias actuales..."
docker build --no-cache -f Dockerfile.cache-refresh -t "$IMAGE" .

echo "Exportando Gradle 8.14.3 y el caché resuelto..."
docker run --rm --entrypoint sh "$IMAGE" -lc "tar -C /opt -czf - gradle" \
  > "$STAGING_DIR/gradle-8.14.3.tar.gz"
docker run --rm --entrypoint sh "$IMAGE" -lc "tar -C /opt -czf - gradle-cache" \
  > "$STAGING_DIR/gradle-cache.tar.gz"

split -d -a 3 -b 50M "$STAGING_DIR/gradle-8.14.3.tar.gz" \
  "$STAGING_DIR/gradle-8.14.3.tar.gz.part-"
split -d -a 3 -b 50M "$STAGING_DIR/gradle-cache.tar.gz" \
  "$STAGING_DIR/gradle-cache.tar.gz.part-"

test -s "$STAGING_DIR/gradle-8.14.3.tar.gz.part-000"
test -s "$STAGING_DIR/gradle-cache.tar.gz.part-000"

rm -f "$OUT_DIR"/gradle-8.14.3.tar.gz.part-* "$OUT_DIR"/gradle-cache.tar.gz.part-*
mv "$STAGING_DIR"/gradle-8.14.3.tar.gz.part-* "$OUT_DIR/"
mv "$STAGING_DIR"/gradle-cache.tar.gz.part-* "$OUT_DIR/"
rm -rf "$STAGING_DIR"

echo "Validando que el build funciona sin red..."
docker build --network=none --no-cache -t gatling-control-center:offline .
echo "Caché offline regenerado y validado correctamente."
