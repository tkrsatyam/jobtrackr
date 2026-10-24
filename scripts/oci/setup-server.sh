#!/usr/bin/env bash
# ==============================================================================
# JobTrackr - OCI Ubuntu 24.04 ARM64 Provisioning Script
# ==============================================================================
set -euo pipefail

echo "==> [1/5] Updating system packages..."
sudo apt-get update -y && sudo apt-get upgrade -y
sudo apt-get install -y curl wget git ufw debian-keyring debian-archive-keyring apt-transport-https

echo "==> [2/5] Setting up 2 GB swapfile as OOM safeguard..."
if [ ! -f /swapfile ]; then
    sudo fallocate -l 2G /swapfile || sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
    # Lower swappiness so swap is only used under real memory pressure
    sudo sysctl vm.swappiness=10
    echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf
    echo "Swap configured successfully."
else
    echo "Swapfile already exists."
fi

echo "==> [3/5] Installing Docker Engine & Compose plugin..."
if ! command -v docker &> /dev/null; then
    curl -fsSL https://get.docker.com | sudo sh
    sudo usermod -aG docker "$USER"
    sudo systemctl enable docker
    sudo systemctl start docker
    echo "Docker installed."
else
    echo "Docker already installed."
fi

echo "==> [4/5] Installing Caddy Reverse Proxy..."
if ! command -v caddy &> /dev/null; then
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
    sudo apt-get update -y
    sudo apt-get install -y caddy
    sudo systemctl enable caddy
    echo "Caddy installed."
else
    echo "Caddy already installed."
fi

echo "==> [5/5] Configuring firewall (UFW & iptables)..."
# Oracle Cloud Ubuntu images include default iptables rules that block incoming 80/443.
# Open ports 80 (HTTP) and 443 (HTTPS) in iptables:
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT || true
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT || true
if command -v netfilter-persistent &> /dev/null; then
    sudo netfilter-persistent save
fi

echo "==> Creating /opt/jobtrackr directory..."
sudo mkdir -p /opt/jobtrackr
sudo chown -R "$USER:$USER" /opt/jobtrackr

echo "=============================================================================="
echo "Server provisioning complete!"
echo "Next steps:"
echo " 1. Copy docker-compose.prod.yml and .env to /opt/jobtrackr"
echo " 2. Configure /etc/caddy/Caddyfile with your domain name and restart caddy"
echo " 3. Run: cd /opt/jobtrackr && docker compose -f docker-compose.prod.yml up -d"
echo "=============================================================================="
