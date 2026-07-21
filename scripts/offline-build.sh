#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BUNDLE_PATH="${1:-offline-image-bundle.tgz}"
REQUIRED_IMAGES=(
  "eclipse-temurin:17-jdk-jammy"
  "aquasec/trivy:0.72.0"
)

missing_files=0
for pattern in "gradle-8.14.3.tar.gz.part-*" "gradle-cache.tar.gz.part-*"; do
  if ! compgen -G "$ROOT_DIR/offline-deps/gradle/$pattern" >/dev/null; then
    echo "Missing offline artifact: offline-deps/gradle/$pattern"
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
