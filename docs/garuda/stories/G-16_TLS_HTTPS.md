# G-16: TLS / HTTPS (Let's Encrypt)

## File to MODIFY

### `cloud/nginx/nginx-vps.conf`
Add HTTPS server block:
```nginx
server {
    listen 443 ssl http2;
    server_name vivasvan-tech.in;
    ssl_certificate /etc/letsencrypt/live/vivasvan-tech.in/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/vivasvan-tech.in/privkey.pem;
    # ... proxy_pass to services (same as existing HTTP block)
}
server {
    listen 80;
    server_name vivasvan-tech.in;
    return 301 https://$server_name$request_uri;
}
```

## File to CREATE
### `cloud/scripts/setup_tls.sh`
```bash
#!/bin/bash
apt install -y certbot python3-certbot-nginx
certbot --nginx -d vivasvan-tech.in --non-interactive --agree-tos -m admin@vivasvan-tech.in
```

## Verification
```bash
curl -sI https://vivasvan-tech.in | head -3
# Expected: HTTP/2 200
```

---

