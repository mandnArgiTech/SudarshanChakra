#!/usr/bin/env bash
# ==============================================================================
# Obtain Let's Encrypt certs for SudarshanChakra Docker nginx (HTTP-01 standalone).
# ==============================================================================
# certbot --nginx edits host nginx; this stack uses nginx in Docker with a mounted config,
# so we use certonly --standalone on the HOST while port 80 is free, then mount
# /etc/letsencrypt into the container (see cloud/docker-compose.vps.yml).
#
# Usage (run on the VPS as root):
#   sudo ./cloud/scripts/setup_tls.sh --domain vivasvan-tech.in --email you@example.com
# Or:
#   sudo TLS_DOMAIN=vivasvan-tech.in TLS_EMAIL=you@example.com ./cloud/scripts/setup_tls.sh
#
# Extra hostnames (SANs), e.g. www:
#   sudo ./cloud/scripts/setup_tls.sh -d vivasvan-tech.in -d www.vivasvan-tech.in -m you@example.com
#
# Before issuance: stop anything bound to host :80 (including nginx-proxy), or HTTP-01 will fail.
# If vivasvan-tech.in:80 is served by another app, use DNS-01 / TLS-ALPN-01 instead (not scripted here).
#
# After certs exist, start the stack; renewals: see comments at bottom.
# ==============================================================================

set -euo pipefail

DOMAIN_ARGS=()
EMAIL=""

usage() {
  sed -n '1,25p' "${BASH_SOURCE[0]}" | tail -n +2
  echo "Options:"
  echo "  -d, --domain FQDN     Subject name (repeat for multiple -d)"
  echo "  -m, --email ADDR      Let's Encrypt account email"
  echo "  -h, --help            This help"
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -d|--domain)
      DOMAIN_ARGS+=(-d "$2")
      shift 2
      ;;
    -m|--email)
      EMAIL="$2"
      shift 2
      ;;
    -h|--help)
      usage 0
      ;;
    *)
      echo "[ERROR] Unknown option: $1" >&2
      usage 1
      ;;
  esac
done

if [[ ${#DOMAIN_ARGS[@]} -eq 0 && -n "${TLS_DOMAIN:-}" ]]; then
  DOMAIN_ARGS=(-d "$TLS_DOMAIN")
fi
if [[ ${#DOMAIN_ARGS[@]} -eq 0 ]]; then
  echo "[ERROR] Set at least one domain: -d example.com or TLS_DOMAIN=example.com" >&2
  exit 1
fi

if [[ -z "$EMAIL" ]]; then
  EMAIL="${TLS_EMAIL:-}"
fi
if [[ -z "$EMAIL" ]]; then
  echo "[ERROR] Set --email or TLS_EMAIL" >&2
  exit 1
fi

if [[ "${EUID:-0}" -ne 0 ]]; then
  echo "[ERROR] Run as root (certbot writes under /etc/letsencrypt)." >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
if command -v apt-get >/dev/null 2>&1; then
  apt-get update -qq
  apt-get install -y -qq certbot
else
  echo "[WARN] apt-get not found; install certbot yourself, then re-run." >&2
  command -v certbot >/dev/null || exit 1
fi

echo "[INFO] Stopping nginx-proxy if it holds port 80 (ignore errors if not running)..."
docker stop nginx-proxy 2>/dev/null || true

echo "[INFO] Requesting certificate (standalone on :80)..."
certbot certonly --standalone \
  "${DOMAIN_ARGS[@]}" \
  --non-interactive --agree-tos -m "$EMAIL" \
  --preferred-challenges http

echo "[INFO] Starting nginx-proxy again (from cloud/)..."
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLOUD_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
if [[ -f "${CLOUD_DIR}/docker-compose.vps.yml" ]]; then
  (cd "${CLOUD_DIR}" && docker compose -f docker-compose.vps.yml up -d nginx) || true
else
  echo "[WARN] ${CLOUD_DIR}/docker-compose.vps.yml not found; start nginx manually." >&2
fi

echo ""
echo "[DONE] Certificates are under /etc/letsencrypt/live/<primary-domain>/"
echo "       Ensure docker-compose.vps.yml mounts /etc/letsencrypt and publishes 443."
echo ""
echo "Renewal (cron or systemd timer), example monthly:"
echo "  0 3 1 * * certbot renew --deploy-hook 'docker exec nginx-proxy nginx -s reload'"
echo ""
echo "If renew fails, stop nginx-proxy during standalone fallback:"
echo "  certbot renew --pre-hook 'docker stop nginx-proxy' --post-hook 'docker start nginx-proxy'"
