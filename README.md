# SudarshanChakra

Enterprise smart farm hazard detection & security system (IoT + Edge AI + Cloud).

## Deploy after successful build

- **Local:** PostgreSQL and RabbitMQ are already running. Start backend services (`./gradlew :auth-service:bootRun` etc.), then `cd dashboard && npm run dev`. Install Android APK from `android/app/build/outputs/apk/debug/app-debug.apk`.
- **Cloud VPS:** From repo root run `./cloud/deploy.sh` (builds images, starts stack with **`--profile full`**, init RabbitMQ). Use `--profile security|monitoring|water_only` for smaller stacks (G-18). First time: `cp cloud/.env.example cloud/.env` and set `DB_PASS`, `RABBITMQ_PASS`, `JWT_SECRET`. Farm provisioning: [scripts/deploy_saas_farm.sh](scripts/deploy_saas_farm.sh). Dashboard/API: `http://<vps-ip>:9080`. See [docs/DEPLOY_AFTER_BUILD.md](docs/DEPLOY_AFTER_BUILD.md) and [docs/VPS_HEALTH_AND_USAGE.md](docs/VPS_HEALTH_AND_USAGE.md).
- **Edge (GPU + cameras):** `cd edge && docker compose up -d` after configuring VPN and `config/cameras.json`.
- **Full steps:** [docs/DEPLOY_AFTER_BUILD.md](docs/DEPLOY_AFTER_BUILD.md) | [docs/DEPLOYMENT_GUIDE.md](docs/DEPLOYMENT_GUIDE.md)
- **Ports & default dev credentials:** [docs/PORTS_AND_CREDENTIALS.md](docs/PORTS_AND_CREDENTIALS.md)