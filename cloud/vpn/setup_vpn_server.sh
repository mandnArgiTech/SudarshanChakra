#!/usr/bin/env bash
# SudarshanChakra — OpenVPN server bootstrap (Ubuntu 22.04+ VPS)
# Uses easy-rsa under $OVPN_ROOT/easy-rsa. Run as root. See cloud/vpn/README.md
set -euo pipefail

if [[ "${EUID:-0}" -ne 0 ]]; then
  echo "Run as root: sudo $0"
  exit 1
fi

OVPN_ROOT="${OVPN_ROOT:-/etc/openvpn/sc-farm}"
EASYRSA_CA_CN="${EASYRSA_CA_CN:-SudarshanChakra-VPN-CA}"
export EASYRSA_BATCH="${EASYRSA_BATCH:-1}"

echo "==> Installing packages..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq openvpn easy-rsa openssl

mkdir -p "$OVPN_ROOT/ccd" /var/log/openvpn
chmod 755 "$OVPN_ROOT" "$OVPN_ROOT/ccd"

EASY_SRC="/usr/share/easy-rsa"
EASY_HOME="$OVPN_ROOT/easy-rsa"
if [[ ! -d "$EASY_SRC" ]]; then
  echo "easy-rsa not found at $EASY_SRC"
  exit 1
fi

if [[ ! -f "$EASY_HOME/pki/ca.crt" ]]; then
  echo "==> Installing easy-rsa into $EASY_HOME and building PKI ..."
  mkdir -p "$EASY_HOME"
  cp -a "$EASY_SRC/." "$EASY_HOME/"
  cd "$EASY_HOME"
  ./easyrsa init-pki
  EASYRSA_REQ_CN="$EASYRSA_CA_CN" ./easyrsa build-ca nopass
  EASYRSA_REQ_CN="server" ./easyrsa gen-req server nopass
  ./easyrsa sign-req server server
  ./easyrsa gen-dh
  for cn in edge-node-a edge-node-b; do
    EASYRSA_REQ_CN="$cn" ./easyrsa gen-req "$cn" nopass
    ./easyrsa sign-req client "$cn"
  done
  cd - >/dev/null
  openvpn --genkey secret "$OVPN_ROOT/ta.key"
  chmod 600 "$OVPN_ROOT/ta.key"
else
  echo "==> PKI already present at $EASY_HOME/pki — skipping cert generation"
  [[ -f "$OVPN_ROOT/ta.key" ]] || openvpn --genkey secret "$OVPN_ROOT/ta.key"
  chmod 600 "$OVPN_ROOT/ta.key"
fi

echo "==> Writing CCD (static IPs) ..."
install -m 0644 /dev/stdin "$OVPN_ROOT/ccd/edge-node-a" <<'CCD'
ifconfig-push 10.8.0.10 255.255.255.0
CCD
install -m 0644 /dev/stdin "$OVPN_ROOT/ccd/edge-node-b" <<'CCD'
ifconfig-push 10.8.0.11 255.255.255.0
CCD

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
install -m 0644 "$SCRIPT_DIR/server.conf" "$OVPN_ROOT/server.conf"
sed -i "s|/etc/openvpn/sc-farm|$OVPN_ROOT|g" "$OVPN_ROOT/server.conf"

echo ""
echo "==> Next steps (manual):"
echo "  1) IP forwarding:"
echo "     echo 'net.ipv4.ip_forward=1' > /etc/sysctl.d/99-openvpn-forward.conf"
echo "     sysctl -p /etc/sysctl.d/99-openvpn-forward.conf"
echo "  2) Firewall: ufw allow 1194/udp && ufw reload"
echo "  3) NAT (optional): iptables -t nat -A POSTROUTING -s 10.8.0.0/24 -o \$(ip route | awk '/default/ {print \$5; exit}') -j MASQUERADE"
echo "  4) Start:"
echo "     openvpn --config $OVPN_ROOT/server.conf --daemon --writepid /var/run/openvpn-sc.pid"
echo ""
echo "==> Client certs for .ovpn bundles:"
echo "     CA:     $EASY_HOME/pki/ca.crt"
echo "     Node A: $EASY_HOME/pki/issued/edge-node-a.crt + private/edge-node-a.key"
echo "     Node B: $EASY_HOME/pki/issued/edge-node-b.crt + private/edge-node-b.key"
echo "     ta.key: $OVPN_ROOT/ta.key  (client key-direction 1)"
echo ""
echo "==> Verify from VPS when connected:"
echo "     ping -c 3 10.8.0.10 && ping -c 3 10.8.0.11"
