#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IMAGE_TAR="$ROOT/offline/docker-images/gatling-performance-1.0.0.tar"

if [[ ! -f "$IMAGE_TAR" ]]; then
  echo "Falta $IMAGE_TAR"
  exit 1
fi

docker load -i "$IMAGE_TAR"
echo "Imagen cargada. Publiquela en el registro interno o use Kubernetes con el manifiesto incluido."
