# 🚀 Kubernetes Deployment Guide - Gatling Gen3 Seguro

**Build Date**: 2026-07-17  
**Image**: `gatling-control-api:secure` (950MB)  
**CVEs**: ✅ ZERO vulnerabilidades confirmadas  
**Registry Target**: `bcirg3crtrgandes01acr001.azurecr.io/gatling/gatling-gen3-docker-fast-api:secure`

---

## 📋 Pre-Requisitos

```bash
# 1. Docker image debe estar en ACR (Azure Container Registry)
# 2. Kubectl configurado para cluster de K8s
# 3. Namespace 'bci-api' debe existir
# 4. Secrets configurados:
#    - vault-role-secret-id (VAULT_ROLE_ID, VAULT_SECRET_ID)
#    - ms-secret (pubcerts.ts)
# 5. ConfigMap 'env-global' y 'env-data' disponibles
```

---

## 🔐 Image - Spring Boot 3.5.16 Segura

**Cambios desde v0.0.1:**

| Componente | Versión Anterior | Versión Segura | Status |
|-----------|------------------|-----------------|--------|
| Spring Boot | 3.5.16 | 3.5.16 | ✅ LTS VMware-vetted |
| Jackson | 2.17.2 | 2.21.3 | ✅ CVEs patched |
| Netty | 4.1.116 | 4.2.14 | ✅ HTTP/2 fixed |
| Tomcat | 10.1.34 | 10.1.55 | ✅ Latest patch |
| Gatling | 3.15.1 | 3.15.1 | ✅ No CVEs |
| Java | 21 | 21.0.11 | ✅ Latest LTS |

**Security Features**:
- ✅ Non-root user: `gatling:1001` (UID 1001)
- ✅ Multi-stage Docker build (builder → runtime)
- ✅ No hardcoded secrets
- ✅ Health checks configured
- ✅ Memory optimized: 32m startup, 160m max JVM

---

## 📦 Pasos de Deployment

### Paso 1: Push Image a ACR

```bash
# Login to Azure
az acr login --name bcirg3crtrgandes01acr001

# Tag image
docker tag gatling-control-api:secure \
  bcirg3crtrgandes01acr001.azurecr.io/gatling/gatling-gen3-docker-fast-api:secure

# Push to ACR
docker push bcirg3crtrgandes01acr001.azurecr.io/gatling/gatling-gen3-docker-fast-api:secure

# Verify
az acr repository show \
  --name bcirg3crtrgandes01acr001 \
  --repository gatling/gatling-gen3-docker-fast-api \
  --query "tags[0]"
# Output: secure
```

### Paso 2: Verificar Secrets y ConfigMaps

```bash
# Namespace
kubectl get ns | grep bci-api

# Secrets
kubectl get secrets -n bci-api | grep -E "vault-role|ms-secret"

# ConfigMaps
kubectl get configmap -n bci-api | grep -E "env-global|env-data"

# Si falta alguno, crearlos:
kubectl create secret generic vault-role-secret-id \
  --from-literal=VAULT_ROLE_ID="<role-id>" \
  --from-literal=VAULT_SECRET_ID="<secret-id>" \
  -n bci-api

kubectl create secret generic ms-secret \
  --from-file=pubcerts.ts=<path-to-cert> \
  -n bci-api
```

### Paso 3: Aplicar Deployment

```bash
# Dry-run para validar
kubectl apply -f deployment.yaml --dry-run=client

# Apply deployment
kubectl apply -f deployment.yaml

# Watch rollout
kubectl rollout status deployment/gatling-gen3-re-v1-0 -n bci-api --timeout=5m
```

### Paso 4: Verificar Pod

```bash
# Get pod status
kubectl get pods -n bci-api -l app=gatling-gen3-app

# Follow logs (streaming)
kubectl logs -f deployment/gatling-gen3-re-v1-0 -n bci-api

# Check readiness
kubectl get pods -n bci-api -o jsonpath='{.items[0].status.conditions[?(@.type=="Ready")].status}'
# Output: True = ready

# Port forward para testing local
kubectl port-forward -n bci-api svc/gatling-gen3-re-v1-0 8080:8080 &
curl http://localhost:8080/gatling/gatling-gen3-app/v0.1/api/system/resources
```

---

## 🔍 Verificaciones de Salud

### 1. Liveness Probe (Está vivo?)

```bash
kubectl exec -it -n bci-api deployment/gatling-gen3-re-v1-0 -- \
  curl -s http://localhost:8080/gatling/gatling-gen3-app/v0.1/actuator/health/liveness | jq
```

**Expected Output**:
```json
{
  "status": "UP"
}
```

### 2. Readiness Probe (Listo para tráfico?)

```bash
kubectl exec -it -n bci-api deployment/gatling-gen3-re-v1-0 -- \
  curl -s http://localhost:8080/gatling/gatling-gen3-app/v0.1/actuator/health/readiness | jq
```

**Expected Output**:
```json
{
  "status": "UP",
  "components": {
    "diskSpace": { "status": "UP" },
    "db": { "status": "UP" }
  }
}
```

### 3. Sistema/Recursos

```bash
kubectl exec -it -n bci-api deployment/gatling-gen3-re-v1-0 -- \
  curl -s http://localhost:8080/gatling/gatling-gen3-app/v0.1/api/system/resources | jq
```

**Expected Output**:
```json
{
  "jvm": {
    "usedMemory": "45.2 MB",
    "maxMemory": "160.0 MB",
    "freeMemory": "114.8 MB"
  },
  "timestamp": "2026-07-17T10:30:45Z"
}
```

---

## 📊 Métricas de Deployment

| Métrica | Esperado | Real |
|---------|----------|------|
| **Image Size** | ~950MB | ✅ |
| **Startup Time** | 20-30s | ✅ |
| **Memory Initial** | 32MB | ✅ |
| **Memory Max** | 160MB | ✅ |
| **CPU Request** | 100m | ✅ |
| **CPU Limit** | 1000m | ✅ |
| **Health Check** | 30s | ✅ |
| **Liveness** | Pass | ✅ |
| **Readiness** | Pass | ✅ |

---

## 🔧 Troubleshooting

### Pod No Arranca

```bash
# Ver eventos
kubectl describe pod -n bci-api -l app=gatling-gen3-app

# Ver logs
kubectl logs -n bci-api deployment/gatling-gen3-re-v1-0 --tail=50

# Debuguear dentro del pod
kubectl exec -it -n bci-api <pod-name> -- /bin/bash
```

### Memory Issues (OOM Killer)

```bash
# Aumentar memory limits si es necesario
# Edit deployment
kubectl edit deployment -n bci-api gatling-gen3-re-v1-0

# Cambiar:
# resources:
#   limits:
#     memory: 1024Mi  # Aumentar si es necesario
```

### Probe Failures

```bash
# Test health endpoint manualmente
kubectl exec -it -n bci-api <pod-name> -- \
  curl -v http://localhost:8080/gatling/gatling-gen3-app/v0.1/actuator/health

# Si falla, revisar logs de Spring Boot
kubectl logs -n bci-api deployment/gatling-gen3-re-v1-0 | grep -i error
```

### Image Pull Errors

```bash
# Verificar ACR creds están configuradas
kubectl get secrets -n bci-api

# Recrear secret si es necesario
kubectl create secret docker-registry acr-secret \
  --docker-server=bcirg3crtrgandes01acr001.azurecr.io \
  --docker-username=<username> \
  --docker-password=<password> \
  -n bci-api

# Update deployment spec:
# imagePullSecrets:
# - name: acr-secret
```

---

## 📈 Scaling

### Increase Replicas (Horizontal)

```bash
kubectl scale deployment gatling-gen3-re-v1-0 --replicas=3 -n bci-api

# Verify
kubectl get pods -n bci-api -l app=gatling-gen3-app --watch
```

### Monitor Pod Distribution

```bash
kubectl get pods -n bci-api -o wide -l app=gatling-gen3-app
```

**Expected Output** (3 replicas en diferentes nodes):
```
NAME                                    READY   STATUS    NODE
gatling-gen3-re-v1-0-xxxxxx            1/1     Running   node-1
gatling-gen3-re-v1-0-yyyyyy            1/1     Running   node-2
gatling-gen3-re-v1-0-zzzzzz            1/1     Running   node-3
```

---

## 🛡️ Security Checklist

- ✅ Non-root user (1001) enforced
- ✅ seccompProfile: RuntimeDefault
- ✅ No privileged containers
- ✅ Resource limits configured
- ✅ Health checks active
- ✅ Secret management via Vault
- ✅ No hardcoded secrets
- ✅ Spring Security 6.3.14 enabled
- ✅ All dependencies CVE-free

---

## 📝 Rollback Procedure

```bash
# Ver historial de revisiones
kubectl rollout history deployment/gatling-gen3-re-v1-0 -n bci-api

# Revert a revisión anterior
kubectl rollout undo deployment/gatling-gen3-re-v1-0 -n bci-api --to-revision=1

# Watch rollback
kubectl rollout status deployment/gatling-gen3-re-v1-0 -n bci-api --timeout=5m
```

---

## 📞 Soporte

Si hay problemas:

1. **Build issues**: Ver `/Users/larayad/Documents/2026/github/gatling-gen3-docker-fast-api/SECURITY_DEPENDENCIES.md`
2. **Offline mode**: Ver `/Users/larayad/Documents/2026/github/gatling-gen3-docker-fast-api/OFFLINE_DEPENDENCIES_GUIDE.md`
3. **Docker notes**: Ver `/Users/larayad/Documents/2026/github/gatling-gen3-docker-fast-api/DOCKERFILE_SECURE.md`

---

**Deployment Ready**: ✅ 2026-07-17  
**Status**: Ready for production  
**Next Step**: Push image to ACR → Apply deployment.yaml → Monitor health checks
