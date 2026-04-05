# G-17: Docker Image Tagging + GHCR Push — DONE

## What shipped

Docker images are built and pushed by **[`.github/workflows/docker-publish.yml`](../../../.github/workflows/docker-publish.yml)** (not `backend.yml` / `dashboard.yml` — those jobs do not produce Docker images).

### Triggers

| Event | Behavior |
|-------|----------|
| **Push to `main`** | Runs when changed paths include `backend/**`, `dashboard/**`, or this workflow file. Each matrix image gets **`:latest`** and **`:<commit-sha>`**. |
| **Push any git tag** | GitHub **does not apply** `paths` filters to tag pushes — the full matrix always runs. Each image gets **`:<tag>`** (e.g. `garuda`, `v1.0.0`) and **`:<commit-sha>`**. **`latest`** is updated only on **`main`** branch pushes, not from tag-only workflows. |

### Images (flat names)

`ghcr.io/<repository_owner_lowercase>/<service>:<tag>`

Services in the matrix: **alert-service**, **auth-service**, **device-service**, **siren-service**, **mdm-service**, **api-gateway**, **dashboard**.

Example (org `mandnargitech`):

- `docker pull ghcr.io/mandnargitech/auth-service:garuda`
- `docker pull ghcr.io/mandnargitech/dashboard:latest`

### Compose alignment

[`cloud/docker-compose.yml`](../../../cloud/docker-compose.yml) and [`docs/docker-compose.cloud.yml`](../../docker-compose.cloud.yml) use the same flat paths (no `sudarshanchakra/` segment between owner and service name).

## Verification

```bash
git tag garuda && git push origin garuda
# Actions → Docker publish (GHCR) → packages under ghcr.io/<owner>/

docker pull ghcr.io/mandnargitech/auth-service:garuda
```

**Note:** Packages may be **private** until the org enables public packages or the user authenticates (`docker login ghcr.io`).

## Story errata (original G-17 snippet)

The original story proposed `docker tag sudarshanchakra/$svc` after `backend.yml`; CI never builds local `sudarshanchakra/*` images in that workflow. The implementation uses **build-push-action** from each service `context` instead.

---
