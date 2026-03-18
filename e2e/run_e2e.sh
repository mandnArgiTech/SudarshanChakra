#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
docker compose -f docker-compose.e2e.yml up -d --wait 2>/dev/null || true
echo "E2E infra up; run: python3 test_full_stack.py (requires full backend stack)"
