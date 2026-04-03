# G-15: OpenVPN Setup

## Files to CREATE
```
cloud/vpn/
├── server.conf           # OpenVPN server config (10.8.0.0/24)
├── setup_vpn_server.sh   # Install + configure on VPS
└── client/
    ├── edge-node-a.conf  # Client config for Node A (10.8.0.10)
    └── edge-node-b.conf  # Client config for Node B (10.8.0.11)
```

Follow standard OpenVPN setup. VPS = 10.8.0.1 (server), edge nodes = 10.8.0.10/11 (clients).

## Verification
```bash
# From VPS:
ping -c 3 10.8.0.10  # Edge Node A
ping -c 3 10.8.0.11  # Edge Node B
# Both should succeed
```

---

