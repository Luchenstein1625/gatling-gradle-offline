#!/bin/bash

# 📥 export-offline-cache.sh
# Exporta el cache de gradle desde Docker build
# para reutilizar en builds offline subsecuentes
#
# Uso: ./export-offline-cache.sh
# Genera: offline-cache-YYYYMMDD.tar.gz

set -e

DATE=$(date +%Y%m%d)
CACHE_FILE="offline-deps-secure-${DATE}.tar.gz"
CONTAINER_NAME="gradle-cache-export-${DATE}"

echo "📥 Exportando dependencias offline seguras..."
echo "Fecha: $DATE"
echo ""

# 1. Crear contenedor desde imagen segura
echo "✅ Paso 1: Creando contenedor..."
docker create \
  --name "$CONTAINER_NAME" \
  gatling-control-api:secure \
  > /dev/null

echo "   Contenedor: $CONTAINER_NAME"

# 2. Copiar caché gradle
echo "✅ Paso 2: Extrayendo cache gradle (~300MB)..."
docker cp "$CONTAINER_NAME":/home/gatling/.gradle ./offline-dependencies/ 2>&1 || {
  echo "❌ Error: No se pudo copiar /home/gatling/.gradle"
  docker rm "$CONTAINER_NAME"
  exit 1
}

echo "   Tamaño cache: $(du -sh ./offline-dependencies/.gradle | cut -f1)"

# 3. Copiar build.gradle y settings.gradle
echo "✅ Paso 3: Copiando configuración gradle..."
docker cp "$CONTAINER_NAME":/workspace/build.gradle ./offline-dependencies/ 2>/dev/null || true
docker cp "$CONTAINER_NAME":/workspace/settings.gradle ./offline-dependencies/ 2>/dev/null || true

# 4. Limpiar contenedor
echo "✅ Paso 4: Limpiando contenedor..."
docker rm "$CONTAINER_NAME" > /dev/null

# 5. Crear archive
echo "✅ Paso 5: Comprimiendo dependencias..."
tar czf "$CACHE_FILE" \
  --exclude='*.git' \
  --exclude='*.github' \
  offline-dependencies/ \
  2>&1 | tail -5

ARCHIVE_SIZE=$(du -sh "$CACHE_FILE" | cut -f1)
echo "   Archive: $CACHE_FILE"
echo "   Tamaño: $ARCHIVE_SIZE"

# 6. Verificar integridad
echo "✅ Paso 6: Verificando integridad..."
JARs=$(tar tzf "$CACHE_FILE" | grep -c "\.jar$" || true)
echo "   JARs encontrados: $JARs"

if [ "$JARs" -lt 100 ]; then
  echo "⚠️  Advertencia: Esperaba >100 JARs, encontré $JARs"
else
  echo "   ✅ Integridad verificada"
fi

# 7. Resumen
echo ""
echo "════════════════════════════════════════════════════════"
echo "✅ EXPORTACIÓN COMPLETADA"
echo "════════════════════════════════════════════════════════"
echo ""
echo "📦 Archive:  $CACHE_FILE"
echo "📏 Tamaño:   $ARCHIVE_SIZE"
echo "📅 Fecha:    $DATE"
echo "🖥️  JARs:     $JARs archivos"
echo ""
echo "📤 PRÓXIMOS PASOS:"
echo ""
echo "1. Guardar en artifact storage:"
echo "   gsutil cp $CACHE_FILE gs://my-bucket/"
echo "   aws s3 cp $CACHE_FILE s3://my-bucket/"
echo ""
echo "2. O subir a Git LFS:"
echo "   git lfs track '$CACHE_FILE'"
echo "   git add $CACHE_FILE"
echo "   git commit -m 'Offline cache: $DATE'"
echo ""
echo "3. Usar en builds offline:"
echo "   tar xzf $CACHE_FILE"
echo "   export GRADLE_USER_HOME=./offline-dependencies"
echo "   gradle --offline build"
echo ""
echo "════════════════════════════════════════════════════════"
echo ""
