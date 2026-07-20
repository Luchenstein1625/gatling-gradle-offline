#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BUNDLE_PATH="${1:-offline-image-bundle.tgz}"
REQUIRED_IMAGES=(
  "eclipse-temurin:11-jdk-jammy"
  "eclipse-temurin:11-jre-jammy"
)

REQUIRED_FILES=(
  "offline-deps/gradle/gradle-8.6.tar.gz.part-000"
  "offline-deps/gradle/gradle-8.6.tar.gz.part-001"
  "offline-deps/gradle/gradle-8.6.tar.gz.part-002"
  "offline-deps/gradle/gradle-cache.tar.gz.part-001"
  "offline-deps/gradle/gradle-cache.tar.gz.part-002"
)

missing_files=0
for file in "${REQUIRED_FILES[@]}"; do
  if [[ ! -f "$ROOT_DIR/$file" ]]; then
    echo "Missing offline artifact: $file"
    ((missing_files+=1))
  fi
done

if [[ $missing_files -gt 0 ]]; then
  echo "ERROR: faltan artefactos en offline-deps/." >&2
  echo "Ejecuta scripts/prepare-offline-deps.sh en un entorno conectado y vuelve a intentar." >&2
  exit 1
fi

missing_count=0
for image in "${REQUIRED_IMAGES[@]}"; do
  if ! docker image inspect "$image" >/dev/null 2>&1; then
    echo "Missing image: $image"
    ((missing_count+=1))
  fi
done

if [[ $missing_count -gt 0 ]]; then
  if [[ -e "$BUNDLE_PATH" ]]; then
    echo "Importing offline bundle from: $BUNDLE_PATH"
    "$ROOT_DIR/scripts/import-offline-images.sh" "$BUNDLE_PATH"
  else
    echo "ERROR: missing $missing_count required image(s) and bundle not found: $BUNDLE_PATH" >&2
    echo "Generate bundle in a connected environment with scripts/export-offline-images.sh" >&2
    exit 1
  fi
fi

echo "Running offline compose build..."
docker compose build --no-cache

echo "Offline build completed successfully."
