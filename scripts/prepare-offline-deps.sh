#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT_DIR/offline-deps/gradle"
mkdir -p "$OUT_DIR"

if ! docker image inspect gatling-dependency-cache:local >/dev/null 2>&1; then
  echo "ERROR: image gatling-dependency-cache:local not found." >&2
  echo "Build it once in connected environment:" >&2
  echo "  docker build --target dependency-cache -t gatling-dependency-cache:local ." >&2
  exit 1
fi

echo "Exporting offline Gradle toolchain and cache to $OUT_DIR"
docker run --rm --entrypoint sh gatling-dependency-cache:local -lc "tar -C /opt -czf - gradle" > "$OUT_DIR/gradle-8.6.tar.gz"
docker run --rm --entrypoint sh gatling-dependency-cache:local -lc "tar -C /opt -czf - gradle-cache" > "$OUT_DIR/gradle-cache.tar.gz"

# Split solicitado:
# - gradle-cache: 2 archivos (primero de 25 MB, segundo con el resto)
# - gradle-8.6: 3 archivos (primeros dos de 50 MB, tercero con el resto)
split -d -a 3 -b 50M "$OUT_DIR/gradle-8.6.tar.gz" "$OUT_DIR/gradle-8.6.tar.gz.part-"

dd if="$OUT_DIR/gradle-cache.tar.gz" of="$OUT_DIR/gradle-cache.tar.gz.part-001" bs=1M count=25 status=none
dd if="$OUT_DIR/gradle-cache.tar.gz" of="$OUT_DIR/gradle-cache.tar.gz.part-002" bs=1M skip=25 status=none

rm -f "$OUT_DIR/gradle-8.6.tar.gz" "$OUT_DIR/gradle-cache.tar.gz"

echo "Done. Generated:"
ls -lh "$OUT_DIR"/gradle-8.6.tar.gz.part-* "$OUT_DIR"/gradle-cache.tar.gz.part-*
