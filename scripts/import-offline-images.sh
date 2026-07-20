#!/usr/bin/env bash
set -euo pipefail

INPUT_PATH="${1:-offline-image-bundle.tgz}"
WORK_DIR="offline-image-import"

if [[ ! -e "$INPUT_PATH" ]]; then
  echo "ERROR: input not found: $INPUT_PATH" >&2
  echo "Provide either a .tgz bundle or a directory with .tar image files." >&2
  exit 1
fi

if [[ -d "$INPUT_PATH" ]]; then
  BUNDLE_DIR="$INPUT_PATH"
else
  rm -rf "$WORK_DIR"
  mkdir -p "$WORK_DIR"
  tar -xzf "$INPUT_PATH" -C "$WORK_DIR"

  # Pick first extracted directory.
  BUNDLE_DIR="$(find "$WORK_DIR" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
  if [[ -z "$BUNDLE_DIR" ]]; then
    echo "ERROR: could not find extracted bundle directory in $INPUT_PATH" >&2
    exit 1
  fi
fi

echo "Importing images from: $BUNDLE_DIR"

mapfile -t tar_files < <(find "$BUNDLE_DIR" -maxdepth 1 -type f -name '*.tar' | sort)
if [[ ${#tar_files[@]} -eq 0 ]]; then
  echo "ERROR: no .tar image files found in $BUNDLE_DIR" >&2
  exit 1
fi

for tar_file in "${tar_files[@]}"; do
  echo "- Loading $(basename "$tar_file")"
  docker load -i "$tar_file"
done

echo
echo "Done. Loaded ${#tar_files[@]} image tar files."
echo "Tip: run 'docker images | grep -E "gatling|temurin"' to verify tags."
