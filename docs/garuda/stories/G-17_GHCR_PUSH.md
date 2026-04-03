# G-17: Docker Image Tagging + GHCR Push

## Files to MODIFY

### `.github/workflows/backend.yml`
Add after the build step:
```yaml
- name: Login to GHCR
  if: startsWith(github.ref, 'refs/tags/')
  run: echo ${{ secrets.GITHUB_TOKEN }} | docker login ghcr.io -u ${{ github.actor }} --password-stdin

- name: Push images
  if: startsWith(github.ref, 'refs/tags/')
  run: |
    TAG=${{ github.ref_name }}
    for svc in auth-service alert-service device-service siren-service api-gateway; do
      docker tag sudarshanchakra/$svc ghcr.io/mandnargitech/$svc:$TAG
      docker push ghcr.io/mandnargitech/$svc:$TAG
    done
```

### `.github/workflows/dashboard.yml`
Same pattern for dashboard image.

## Verification
```bash
git tag garuda && git push origin garuda
# GitHub Actions runs → images appear at ghcr.io/mandnargitech/*:garuda
```

---

