#!/bin/bash
# compose-prod.sh
# Wrapper para ejecutar docker compose SIN límites de recursos (servidor)
# Los límites vienen del orquestador en producción (Kubernetes, nomad, etc)
# 
# USO:
#   ./scripts/compose-prod.sh up -d
#   ./scripts/compose-prod.sh logs -f
#   ./scripts/compose-prod.sh down

docker compose -f docker-compose.yml "$@"
