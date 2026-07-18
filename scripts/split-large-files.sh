#!/bin/bash
# split-large-files.sh
# Divide archivos >50MB en partes para poder commitear a GitHub (límite 100MB)
# Las partes se ensamblan automáticamente durante el Docker build
#
# USO: ./scripts/split-large-files.sh

set -e
cd "$(dirname "$0")/.."

PART_SIZE=50m

echo "=== Dividiendo archivos grandes para git ==="

# --- Gradle ZIP (usado en Docker build) ---
GRADLE_ZIP="offline/gradle/gradle-8.6-bin.zip"
if [ -f "$GRADLE_ZIP" ]; then
    echo "Dividiendo $GRADLE_ZIP..."
    split -d -b $PART_SIZE "$GRADLE_ZIP" "${GRADLE_ZIP}.part."
    echo "  Partes creadas:"
    ls -lh "${GRADLE_ZIP}.part."*
    echo "  Eliminando original (ya en .gitignore)..."
    rm "$GRADLE_ZIP"
else
    echo "  $GRADLE_ZIP no encontrado (puede que ya esté dividido)"
    ls "${GRADLE_ZIP}.part."* 2>/dev/null && echo "  Partes existentes OK" || echo "  ADVERTENCIA: no hay partes tampoco"
fi

echo ""
echo "=== Listo ==="
echo "Archivos para commitear: offline/gradle/gradle-8.6-bin.zip.part.*"
echo "Archivos ignorados (transferir manualmente al servidor):"
echo "  offline/docker-images/*.tar"
echo "  offline/java/temurin-jdk-21.0.11_10-windows-x64.zip"
echo "  offline/java/jdk-21/"
echo "  offline/gradle/gradle-8.6/"
