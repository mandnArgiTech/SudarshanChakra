# G-15: OpenVPN setup — DONE (artifacts in repo)

## Status

**Delivered:** [cloud/vpn/](../../../cloud/vpn/) — OpenVPN **templates** and **`setup_vpn_server.sh`** for **`10.8.0.0/24`**: VPS **`10.8.0.1`**, Edge Node A **`10.8.0.10`**, Node B **`10.8.0.11`**, matching [edge/docker-compose.yml](../../../edge/docker-compose.yml) (`VPN_BROKER_IP`, `ping 10.8.0.1` healthcheck).

**Operational proof** (ping from VPS) is **manual** on a real deployment — see [cloud/vpn/README.md](../../../cloud/vpn/README.md).

## Files (as planned)

```
cloud/vpn/
├── README.md
├── server.conf
├── setup_vpn_server.sh
├── ccd/
│   ├── edge-node-a.example
│   └── edge-node-b.example
└── client/
    ├── edge-node-a.conf
    └── edge-node-b.conf
```

## Verification

```bash
# On VPS after clients connect:
ping -c 3 10.8.0.10   # Edge Node A
ping -c 3 10.8.0.11   # Edge Node B
```

Bootstrap (native easy-rsa, Ubuntu 22.04+):

```bash
# From cloned repo root on the VPS:
sudo OVPN_ROOT=/etc/openvpn/sc-farm ./cloud/vpn/setup_vpn_server.sh
```

**Alternative:** Docker-based server in [DEPLOYMENT_GUIDE.md](../../DEPLOYMENT_GUIDE.md) §1.3 (`kylemanna/openvpn`).

## Edge client

Compose expects **`edge/vpn/edge-node-a.ovpn`** — same config text as **`client/edge-node-a.conf`** after certs are inlined or pathed; see README.

## Garuda checklist

[OpenVPN tunnel (G-15)](../GARUDA_CHECKLIST.md) — marked complete for **in-repo automation/templates**; tunnel **ping** remains an operator check.

---
