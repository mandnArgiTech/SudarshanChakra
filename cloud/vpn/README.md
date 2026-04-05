# OpenVPN — VPS to edge nodes (G-15)

Templates and a setup script for a **site-to-site style** tunnel: edge nodes get fixed addresses on **`10.8.0.0/24`**, VPS OpenVPN server is **`10.8.0.1`**, edge nodes **`10.8.0.10`** (Node A) and **`10.8.0.11`** (Node B). This matches [edge/docker-compose.yml](../../edge/docker-compose.yml) (repo root). (`VPN_BROKER_IP=10.8.0.1`, healthcheck `ping 10.8.0.1`).

**IPv4 only** in these templates.

## Secret policy

- **Do not commit** CA keys, client keys, `*.crt` issued to real nodes, or `ta.key` / `tls-crypt` secrets.
- Repo files are **templates**; generated material lives on the VPS under e.g. `/etc/openvpn/sc-farm/` (or your chosen directory).
- Edge nodes expect **`edge/vpn/edge-node-a.ovpn`** (gitignored via [`.gitignore`](../../.gitignore) patterns — see below). The OpenVPN config **text** is the same as **`client/edge-node-a.conf`** after you embed or reference certs; rename/copy to **`*.ovpn`** for [dperson/openvpn-client](https://hub.docker.com/r/dperson/openvpn-client) as mounted in compose.

## Layout

| Path | Role |
|------|------|
| [server.conf](server.conf) | Server template (`topology subnet`, `10.8.0.0/24`, `client-config-dir`) |
| [setup_vpn_server.sh](setup_vpn_server.sh) | Ubuntu 22.04+ helper: `easy-rsa`, certs, **CCD** static IPs, next-step hints |
| [client/edge-node-a.conf](client/edge-node-a.conf) | Client template — Node A |
| [client/edge-node-b.conf](client/edge-node-b.conf) | Client template — Node B |
| [ccd/](ccd/) | Example **client-config-dir** snippets (static `ifconfig-push`) |

## Static IPs (CCD)

OpenVPN assigns **`10.8.0.10` / `10.8.0.11`** via **server-side** files named after the **client certificate CN** (e.g. `edge-node-a`). See [ccd/edge-node-a.example](ccd/edge-node-a.example). The server config must include:

```conf
client-config-dir /etc/openvpn/sc-farm/ccd
```

## Alternative: Docker server (kylemanna)

In-repo **templates** complement the Docker workflow in [docs/DEPLOYMENT_GUIDE.md](../../docs/DEPLOYMENT_GUIDE.md) §1.3 (`kylemanna/openvpn`, `ovpn_getclient`, `ccd/`). Use **either** native OpenVPN + `setup_vpn_server.sh` **or** the Docker commands there — not both on the same `tun` without care.

## Farm LAN routing (optional)

If edge must reach cameras on `192.168.x.0/24` through the tunnel, you may need on the server:

- `push "route 192.168.1.0 255.255.255.0"` (example), and/or
- `iroute` in CCD + edge-side NAT — **document your LAN CIDR** before enabling.

## Verification (manual)

From the **VPS** (with both clients connected):

```bash
ping -c 3 10.8.0.10   # Edge Node A
ping -c 3 10.8.0.11   # Edge Node B
```

Operational proof is **not** run in CI. After templates land in the repo, Garuda marks G-15 **done** for **artifacts**; you still must run the above on a real deployment.

## Firewall / NAT checklist

- Allow **UDP 1194** on the VPS (`ufw allow 1194/udp` or cloud SG).
- `net.ipv4.ip_forward=1` and **MASQUERADE** from `tun0` to the public interface if clients need internet or SNAT to the farm (see comments in `setup_vpn_server.sh`).

## Collision warning

Ensure **`10.8.0.0/24`** does not overlap the farm LAN or another VPN.

## References

- [docs/garuda/stories/G-15_OPENVPN.md](../../docs/garuda/stories/G-15_OPENVPN.md)
- [docs/DEPLOYMENT_GUIDE.md](../../docs/DEPLOYMENT_GUIDE.md) §1.3
- [docs/TROUBLESHOOTING.md](../../docs/TROUBLESHOOTING.md) (OpenVPN client)
