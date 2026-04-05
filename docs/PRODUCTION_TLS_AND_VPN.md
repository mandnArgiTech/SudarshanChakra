# Production TLS and VPN (scaffold)

**Status:** Reference architecture — **OpenVPN/WireGuard and public TLS certs are not automated in-repo.** Use this as a checklist when hardening a deployment.

## TLS termination (recommended)

Terminate HTTPS at a **reverse proxy** in front of the API gateway and dashboard static host.

### Option A — Nginx + Let’s Encrypt (Certbot)

**SudarshanChakra VPS (Docker nginx):** Use [cloud/scripts/setup_tls.sh](../cloud/scripts/setup_tls.sh) (`certbot certonly --standalone`) and mount **`/etc/letsencrypt`** into the **`nginx-proxy`** container; details in [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) §1.4.

1. Point DNS `api.example.com` and `app.example.com` to the VPS.
2. Install nginx and certbot; obtain certificates for both hostnames.
3. Proxy:
   - `location /` → dashboard static files or `npm run preview` upstream.
   - `location /api/` → `http://127.0.0.1:8080` (Spring Cloud Gateway).
4. WebSocket: `proxy_http_version 1.1`, `Upgrade` and `Connection` headers for `/ws` → alert-service (e.g. `127.0.0.1:8081`).

### Option B — Caddy (automatic HTTPS)

- Use a `Caddyfile` with `reverse_proxy` to the same upstreams; TLS is automatic with public DNS.

## MQTT over TLS

- Prefer **MQTTS** (`ssl://` / `mqtts://`) on port **8883** with broker-issued or CA-signed certs.
- Android `Server connection` already supports `ssl://host:8883` when configured in-app.

## VPN (site-to-site or road-warrior)

- **WireGuard** or **OpenVPN** between farm LAN and cloud VPC so edge nodes are not directly exposed.
- Edge devices reach gateway private IP; cameras stay on farm LAN RTSP.

## Ports reference

See [PORTS_AND_CREDENTIALS.md](PORTS_AND_CREDENTIALS.md) for defaults (8080 gateway, 8082 device-service, 1883/8883 MQTT, etc.).

## Android debug note

Debug builds merge `android/app/src/debug/AndroidManifest.xml` to allow **cleartext HTTP** for LAN edge snapshots. **Release** builds should rely on HTTPS or VPN; do not ship with permissive cleartext in production.
