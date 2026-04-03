# G-18: Deployment Script + Compose Profiles

## Files to CREATE

### `scripts/deploy_saas_farm.sh` (replace the existing stub)
Functional script that:
1. Accepts `--plan`, `--farm-name`, `--slug`, `--admin-user`, `--admin-email`
2. Calls `POST /api/v1/farms` to create farm
3. Calls `POST /api/v1/auth/register` to create admin user for that farm
4. Starts the appropriate Docker Compose profile

### `cloud/docker-compose.profile-security.yml`
Services: postgres, rabbitmq, auth-service, alert-service, device-service, siren-service, api-gateway, dashboard.
NO: mdm-service (not in security plan).

### `cloud/docker-compose.profile-monitoring.yml`
Services: postgres, rabbitmq, auth-service, alert-service, device-service, api-gateway, dashboard.
NO: siren-service, mdm-service.

## Verification
```bash
./scripts/deploy_saas_farm.sh \
  --plan water_only \
  --farm-name "Test Farm" \
  --slug test-farm \
  --admin-user testadmin \
  --admin-email test@example.com
# Expected: Farm created, admin user created, services running
```
