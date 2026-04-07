#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
# SudarshanChakra — OpenVPN Server Setup (runs on VPS)
# ═══════════════════════════════════════════════════════════════
#
# One-time setup. Generates PKI, server cert, and client configs.
# Uses kylemanna/openvpn Docker image for all crypto operations.
#
# Usage:
#   cd cloud/vpn
#   ./setup_vpn_server.sh
#
# After running:
#   - VPN server ready in Docker Compose
#   - Client configs saved to ./clients/
#   - Copy .ovpn files to edge nodes
# ═══════════════════════════════════════════════════════════════
set -euo pipefail

VPS_DOMAIN="${1:-vivasvan-tech.in}"
COMPOSE_FILE="../docker-compose.vps.yml"
VOLUME="cloud_openvpn-data"

G=$'\033[32m' Y=$'\033[33m' B=$'\033[34m' W=$'\033[1m' X=$'\033[0m'
info(){ printf "${B}[VPN]${X} %s\n" "$1"; }
ok(){ printf "${G}[VPN] ✓${X} %s\n" "$1"; }

info "Setting up OpenVPN server for $VPS_DOMAIN"

# Step 1: Generate server config + PKI
info "Generating server config (UDP, 10.8.0.0/24, DNS push)..."
docker run -v "$VOLUME:/etc/openvpn" --rm kylemanna/openvpn:2.4 \
  ovpn_genconfig -u "udp://$VPS_DOMAIN" \
  -s 10.8.0.0/24 \
  -e 'client-to-client' \
  -e 'topology subnet'

# Step 2: Initialize PKI (will prompt for CA passphrase)
info "Initializing PKI — you will be asked to set a CA passphrase..."
docker run -v "$VOLUME:/etc/openvpn" --rm -it kylemanna/openvpn:2.4 \
  ovpn_initpki

ok "PKI initialized"

# Step 3: Generate client certs for edge nodes
mkdir -p clients
for client in edge-node-a edge-node-b; do
  info "Generating client cert: $client..."
  docker run -v "$VOLUME:/etc/openvpn" --rm -it kylemanna/openvpn:2.4 \
    easyrsa build-client-full "$client" nopass

  info "Exporting $client.ovpn..."
  docker run -v "$VOLUME:/etc/openvpn" --rm kylemanna/openvpn:2.4 \
    ovpn_getclient "$client" > "clients/$client.ovpn"

  ok "Client config: clients/$client.ovpn"
done

# Step 4: Assign static IPs via CCD
info "Setting static IPs (edge-node-a=10.8.0.10, edge-node-b=10.8.0.11)..."
docker run -v "$VOLUME:/etc/openvpn" --rm kylemanna/openvpn:2.4 \
  bash -c 'mkdir -p /etc/openvpn/ccd && \
    echo "ifconfig-push 10.8.0.10 255.255.255.0" > /etc/openvpn/ccd/edge-node-a && \
    echo "ifconfig-push 10.8.0.11 255.255.255.0" > /etc/openvpn/ccd/edge-node-b'

ok "Static IPs configured"

# Summary
printf "\n${W}  OpenVPN Server Setup Complete${X}\n\n"
echo "  VPS domain:    $VPS_DOMAIN:1194/udp"
echo "  VPN subnet:    10.8.0.0/24"
echo "  Server IP:     10.8.0.1"
echo "  Edge Node A:   10.8.0.10  (clients/edge-node-a.ovpn)"
echo "  Edge Node B:   10.8.0.11  (clients/edge-node-b.ovpn)"
echo ""
echo "  Next steps:"
echo "    1. Start server:  docker compose -f $COMPOSE_FILE up -d openvpn-server"
echo "    2. Copy to edge:  scp clients/edge-node-a.ovpn devi@192.168.1.50:~/SudarshanChakra/edge/vpn/"
echo "    3. Start edge:    cd edge && docker compose up -d"
echo "    4. Verify:        ping 10.8.0.10 (from VPS)"
echo ""
