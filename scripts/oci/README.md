# OCI Deployment Guide — JobTrackr

This directory contains the provisioning and runtime configurations for hosting JobTrackr's active microservices on Oracle Cloud Infrastructure (OCI) Ampere A1 (Always Free Tier).

---

## 1. OCI Security List Configuration (Network Ingress)
Before provisioning the VM, ensure your OCI Virtual Cloud Network (VCN) allows ingress traffic:
1. In the OCI Console, navigate to **Networking > Virtual Cloud Networks > [Your VCN] > Security Lists**.
2. Click **Default Security List for [Your VCN]** and add **Ingress Rules**:
   * **HTTP**: Source CIDR `0.0.0.0/0`, Protocol `TCP`, Destination Port `80`.
   * **HTTPS**: Source CIDR `0.0.0.0/0`, Protocol `TCP`, Destination Port `443`.
   * **SSH**: Port `22` (already open by default).

---

## 2. Server Provisioning on OCI VM
SSH into your Ubuntu 24.04 ARM64 instance:
```bash
ssh -i /path/to/your/ssh_key ubuntu@<YOUR_OCI_PUBLIC_IP>
```

Run the automated provisioning script:
```bash
# Clone the repository or copy scripts/oci/setup-server.sh
curl -fsSL https://raw.githubusercontent.com/tkrsatyam/jobtrackr/main/scripts/oci/setup-server.sh -o setup-server.sh
chmod +x setup-server.sh
./setup-server.sh
```
This script:
* Updates Ubuntu packages.
* Configures a 2 GB swapfile (protects against sudden OOM kills while preserving physical RAM).
* Installs Docker Engine and Compose plugin.
* Installs Caddy reverse proxy.
* Opens internal firewall rules for ports 80 and 443.

---

## 3. Deploying JobTrackr Containers

1. Copy `docker-compose.prod.yml` and your populated `.env` file to `/opt/jobtrackr`:
```bash
cd /opt/jobtrackr
# Create or copy .env with your Neon, Upstash, and Gemini secrets
nano .env
```

2. Start the services:
```bash
docker compose -f docker-compose.prod.yml up -d
```

3. Check container status:
```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f
```

---

## 4. Reverse Proxy & SSL Setup with Caddy

1. Copy `Caddyfile` to `/etc/caddy/Caddyfile`:
```bash
sudo nano /etc/caddy/Caddyfile
```
Set your domain name (e.g. `api.yourdomain.com`).

2. Reload Caddy:
```bash
sudo systemctl reload caddy
```
Caddy automatically obtains and provisions Let's Encrypt / ZeroSSL TLS certificates.

---

## 5. GitHub Actions Continuous Deployment Setup
To enable automatic deployments when commits land on `main`:
1. In your GitHub repository, navigate to **Settings > Secrets and variables > Actions**.
2. Add the following repository secrets:
   * `OCI_HOST`: Public IP of your OCI VM.
   * `OCI_USERNAME`: `ubuntu`
   * `OCI_SSH_KEY`: The private SSH key used to log in to the OCI instance.
   * `DOCKERHUB_USERNAME`: Your Docker Hub username.
   * `DOCKERHUB_TOKEN`: Your Docker Hub access token.
