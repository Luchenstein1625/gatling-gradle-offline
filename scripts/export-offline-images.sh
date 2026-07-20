#!/usr/bin/env bash
set -euo pipefail

OUT_DIR="${1:-offline-image-bundle}"
mkdir -p "$OUT_DIR"

# Images required for fully offline compose builds in this repository.
IMAGES=(
  "eclipse-temurin:11-jdk-jammy"
  "eclipse-temurin:11-jre-jammy"
  "bcirg3crtrgandes01acr001.azurecr.io/gatling/gatling-gen3-docker-fast-api:secure"
)

MANIFEST_FILE="$OUT_DIR/manifest.txt"
: > "$MANIFEST_FILE"

echo "Exporting images to: $OUT_DIR"
for image in "${IMAGES[@]}"; do
  if ! docker image inspect "$image" >/dev/null 2>&1; then
    echo "ERROR: image not found locally: $image" >&2
    echo "Pull/build it first and retry." >&2
    exit 1
  fi

  safe_name="$(echo "$image" | tr '/:' '__')"
  tar_file="$OUT_DIR/${safe_name}.tar"

  echo "- Saving $image"
  docker save "$image" -o "$tar_file"

  if command -v shasum >/dev/null 2>&1; then
    checksum="$(shasum -a 256 "$tar_file" | awk '{print $1}')"
  elif command -v sha256sum >/dev/null 2>&1; then
    checksum="$(sha256sum "$tar_file" | awk '{print $1}')"
  else
    checksum="UNAVAILABLE"
  fi

  printf '%s|%s|%s\n' "$image" "$tar_file" "$checksum" >> "$MANIFEST_FILE"
done

tarball="${OUT_DIR}.tgz"
tar -czf "$tarball" -C "$(dirname "$OUT_DIR")" "$(basename "$OUT_DIR")"

echo
echo "Done."
echo "Bundle directory: $OUT_DIR"
echo "Bundle archive:   $tarball"
echo "Manifest:         $MANIFEST_FILE"
