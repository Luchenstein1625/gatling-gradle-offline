# Offline Image Bootstrap

This repository can be built offline with:

- docker compose build --no-cache

as long as required base images and offline dependency artifacts are available locally.

There is also a single-command wrapper that can auto-import bundle files when needed:

- ./scripts/offline-build.sh [offline-image-bundle.tgz]

## 1) Prepare offline dependency folder on a machine with internet

Run from repository root:

```bash
chmod +x scripts/prepare-offline-deps.sh
./scripts/prepare-offline-deps.sh
```

This creates:

- offline-deps/gradle/gradle-8.6.tar.gz.part-000
- offline-deps/gradle/gradle-8.6.tar.gz.part-001
- offline-deps/gradle/gradle-8.6.tar.gz.part-002
- offline-deps/gradle/gradle-cache.tar.gz.part-001
- offline-deps/gradle/gradle-cache.tar.gz.part-002

## 2) Create image bundle on a machine with internet

Run from repository root:

```bash
chmod +x scripts/export-offline-images.sh scripts/import-offline-images.sh
./scripts/export-offline-images.sh
```

This creates:

- offline-image-bundle/ (directory with .tar image files)
- offline-image-bundle.tgz (portable archive)
- offline-image-bundle/manifest.txt (image list + checksums)

## 3) Transfer bundle and offline-deps to restricted environment

Copy both to the target server:

- offline-image-bundle.tgz
- offline-deps/ (at least offline-deps/gradle/*.tar.gz)

## 4) Import images on target server

Run on target server from repository root:

```bash
chmod +x scripts/import-offline-images.sh
./scripts/import-offline-images.sh offline-image-bundle.tgz
```

Optional: if already extracted, pass the directory directly:

```bash
./scripts/import-offline-images.sh offline-image-bundle
```

## 5) Build offline

After import and with offline-deps present:

```bash
docker compose build --no-cache
```

Or in one step (import-if-needed + build):

```bash
chmod +x scripts/offline-build.sh scripts/import-offline-images.sh
./scripts/offline-build.sh offline-image-bundle.tgz
```

## Notes

- Dockerfile reads Gradle and dependency cache from offline-deps/gradle/*.tar.gz.
- Dockerfile reconstructs tarballs from part files automatically during build.
- Base images still must exist locally (`eclipse-temurin:*`) via prior load/pull.
- For runtime in disconnected clusters, publish the final app image to a registry reachable by the cluster.
