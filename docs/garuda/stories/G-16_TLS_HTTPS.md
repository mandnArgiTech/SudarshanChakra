# G-16: TLS / HTTPS (Let's Encrypt) — DONE

## Summary

HTTPS termination for the VPS stack uses **nginx in Docker** with **Let's Encrypt** certs on the **host** mounted at **`/etc/letsencrypt`**. `certbot --nginx` is **not** used (no host nginx config for Certbot to edit). Issuance uses **`certbot certonly --standalone`** while port **80** is free.

## Files

| File | Role |
|------|------|
| [cloud/nginx/nginx-vps.conf](../../../cloud/nginx/nginx-vps.conf) | `listen 443 ssl http2` + same `/`, `/api/`, `/ws/`, `/health` as HTTP; FQDN **`server_name`** on **80** → **301** to **https**; **`default_server`** on **80** keeps **`http://<ip>:9080`** |
| [cloud/nginx/nginx-vps-http.conf](../../../cloud/nginx/nginx-vps-http.conf) | HTTP-only config when no certs (swap compose mount; drop **443** + **letsencrypt** volume) |
| [cloud/docker-compose.vps.yml](../../../cloud/docker-compose.vps.yml) | **`443:443`** (or **`8443:443`** if host **443** busy), **`/etc/letsencrypt:/etc/letsencrypt:ro`** |
| [cloud/scripts/setup_tls.sh](../../../cloud/scripts/setup_tls.sh) | **`certbot certonly --standalone`**, **`-d` / `--domain`**, **`-m` / `--email`**, renewal **cron** hint |
| [docs/DEPLOYMENT_GUIDE.md](../../DEPLOYMENT_GUIDE.md) | §1.4 aligned with script, HTTP-01 / port **80** / **9080** vs **443**, mixed content note |

Domain **`vivasvan-tech.in`** in nginx is a **concrete default**; replace in **`nginx-vps.conf`** if certs use another CN.

## Verification (operator)

```bash
curl -sI https://vivasvan-tech.in | head -3
# Expect HTTP/2 200 or 301 (depending on path). Use https://vivasvan-tech.in:8443 if mapped.
```

Live check depends on DNS, open **443** (or mapped port), and valid certs.

## Edge cases

- **HTTP-01** needs **port 80** on the name’s public IP during standalone issuance; conflict with another app on **:80** → DNS-01 or temporarily free **80**.
- **`http://<ip>:9080`** uses the **default_server** block; **`http://vivasvan-tech.in:9080`** hits the FQDN block and redirects to **`https://vivasvan-tech.in/...`** (no **:9080** on redirect — standard **443**).
- **`privkey.pem`** permissions: container runs as **nginx** user; host **letsencrypt** perms are usually readable for world on **fullchain**, **root** on **privkey** — if reload fails, see Certbot docs for group **ssl-cert** or adjust (deployment-specific).

---
