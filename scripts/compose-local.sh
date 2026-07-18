#!/bin/bash
# compose-local.sh
# Wrapper para ejecutar docker compose CON límites de recursos (local)
# 
# USO:
#   ./scripts/compose-local.sh up -d
#   ./scripts/compose-local.sh logs -f
#   ./scripts/compose-local.sh down

docker compose \
  -f docker-compose.yml \
  -f docker-compose.local.yml \
  "$@"
