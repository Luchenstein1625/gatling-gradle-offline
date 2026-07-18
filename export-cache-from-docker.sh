#!/bin/bash
set -e

WORKSPACE="/Users/larayad/Documents/2026/github/gatling-gen3-docker-fast-api"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
EXPORT_DIR="${WORKSPACE}/offline-dependencies-${TIMESTAMP}"

echo "📦 Extrayendo dependencias del Docker image a repo local..."
echo "   Timestamp: $TIMESTAMP"
echo "   Directorio: $EXPORT_DIR"
echo ""

# Crear contenedor temporal que va a exportar el cache
docker run --rm \
  --name gradle-export-${TIMESTAMP} \
  -v ${WORKSPACE}:/workspace \
  -w /workspace \
  --entrypoint sh \
  gatling-control-api:secure \
  -c "
    echo '✅ Contenedor iniciado'
    echo 'Buscando gradle cache...'
    
    # Si existe cache en el builder, copiar a /workspace
    if [ -d /opt/offline/gradle-cache ]; then
      echo '✅ Encontrado: /opt/offline/gradle-cache'
      mkdir -p /workspace/offline-dependencies-export
      cp -r /opt/offline/gradle-cache /workspace/offline-dependencies-export/
      find /workspace/offline-dependencies-export -type f | wc -l
    else
      echo '⚠️  No se encontró /opt/offline/gradle-cache'
      find / -maxdepth 3 -type d -name gradle 2>/dev/null | head -10
    fi
  "

echo ""
echo "✅ Exportación completada"
ls -lh ${EXPORT_DIR} 2>/dev/null || echo "⚠️  Directorio no encontrado - cache puede estar en Docker layer"
