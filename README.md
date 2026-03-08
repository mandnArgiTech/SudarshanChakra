# SudarshanChakra

Enterprise smart farm hazard detection & security system (IoT + Edge AI + Cloud).

## Deploy after successful build

- **Local:** PostgreSQL and RabbitMQ are already running. Start backend services (`./gradlew :auth-service:bootRun` etc.), then `cd dashboard && npm run dev`. Install Android APK from `android/app/build/outputs/apk/debug/app-debug.apk`.
- **Cloud VPS:** From repo root run `./cloud/deploy.sh` (builds images, starts stack, init RabbitMQ). First time: `cp cloud/.env.example cloud/.env` and set `DB_PASS`, `RABBITMQ_PASS`, `JWT_SECRET`. Dashboard/API: `http://<vps-ip>:9080` (or port 80 if you set it in `cloud/docker-compose.vps.yml`). See [docs/DEPLOY_AFTER_BUILD.md](docs/DEPLOY_AFTER_BUILD.md) and [docs/VPS_HEALTH_AND_USAGE.md](docs/VPS_HEALTH_AND_USAGE.md) for health checks and usage.
- **Edge (GPU + cameras):** `cd edge && docker compose up -d` after configuring VPN and `config/cameras.json`.
- **Full steps:** [docs/DEPLOY_AFTER_BUILD.md](docs/DEPLOY_AFTER_BUILD.md) | [docs/DEPLOYMENT_GUIDE.md](docs/DEPLOYMENT_GUIDE.md)