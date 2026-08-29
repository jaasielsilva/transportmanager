#!/bin/bash
# =============================================================
# Setup inicial da VPS (Hostinger) — molde do kit padrao-projeto
# Executar como root na primeira configuracao. Ubuntu 22.04 LTS.
# Cria os DOIS ambientes: producao (app.) e homologacao (homolog.)
#
# Idempotente: pode rodar de novo sem quebrar nada.
# O Nginx e criado SOMENTE em HTTP; o certbot --nginx (passo final)
# injeta o SSL. Nao inverta a ordem — sem os certificados no disco
# um server block com ssl_certificate faz o `nginx -t` falhar.
# =============================================================

set -euo pipefail

APP="transportmanager"                 # slug do projeto (ex.: gestao-empresarial)
DOMAIN_PROD="app.transportmanager.erpcorporativo.shop"          # ex.: app.erpcorporativo.shop
DOMAIN_HOMOLOG="homolog.transportmanager.erpcorporativo.shop"   # ex.: homolog.erpcorporativo.shop
MONITORING_IP="187.77.245.121"       # VPS de monitoring (Prometheus central)
ADMIN_EMAIL="silvajasiel30@gmail.com"          # usado pelo certbot
DEPLOY_USER="deploy"
MAX_UPLOAD="10M"
PORT_PROD="8081"
PORT_HOMOLOG="8082"
DB_PORT_PROD="3306"
DB_PORT_HOMOLOG="3307"

DB_SLUG="${APP//-/_}"

echo "========================================"
echo " Setup VPS — $APP"
echo "========================================"

# ---- 1. Sistema + swap + updates automaticos ----
echo "[1/9] Sistema, swap e atualizacoes automaticas..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get upgrade -y -qq
apt-get install -y -qq curl ufw fail2ban unattended-upgrades apache2-utils

# Swap de 2G — VPS pequena com MySQL + JVM morre por OOM sem isso
if [ ! -f /swapfile ]; then
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
    sysctl -w vm.swappiness=10
    echo 'vm.swappiness=10' > /etc/sysctl.d/99-swappiness.conf
fi

dpkg-reconfigure -f noninteractive unattended-upgrades

# ---- 2. Docker + rotacao de log ----
echo "[2/9] Docker..."
if ! command -v docker &> /dev/null; then
    curl -fsSL https://get.docker.com | sh
fi
# Sem isso o log dos containers enche o disco e dispara o alerta de "disco > 85%"
cat > /etc/docker/daemon.json << 'EOF'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "20m", "max-file": "5" }
}
EOF
systemctl enable docker
systemctl restart docker

# ---- 3. Hardening de SSH e firewall ----
echo "[3/9] Hardening SSH + firewall..."
cat > /etc/ssh/sshd_config.d/99-hardening.conf << 'EOF'
PermitRootLogin prohibit-password
PasswordAuthentication no
ChallengeResponseAuthentication no
X11Forwarding no
MaxAuthTries 3
EOF
sshd -t && systemctl reload ssh

ufw --force reset > /dev/null
ufw default deny incoming
ufw default allow outgoing
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
# Exporters da stack de monitoring (host network) — so o Prometheus central enxerga
for porta in 9100 8091 9104 9080; do
    ufw allow from "$MONITORING_IP" to any port "$porta" proto tcp
done
ufw --force enable

systemctl enable fail2ban && systemctl restart fail2ban

# ---- 4. Usuario de deploy (sem senha, so chave SSH) ----
echo "[4/9] Usuario deploy..."
if ! id "$DEPLOY_USER" &>/dev/null; then
    useradd -m -s /bin/bash "$DEPLOY_USER"
fi
usermod -aG docker "$DEPLOY_USER"
install -d -m 700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "/home/$DEPLOY_USER/.ssh"
touch "/home/$DEPLOY_USER/.ssh/authorized_keys"
chmod 600 "/home/$DEPLOY_USER/.ssh/authorized_keys"
chown -R "$DEPLOY_USER:$DEPLOY_USER" "/home/$DEPLOY_USER/.ssh"
# Colar a chave publica do GitHub Actions:
#   echo "ssh-ed25519 AAAA... github-actions" >> /home/deploy/.ssh/authorized_keys

# ---- 5. Diretorios ----
echo "[5/9] Diretorios..."
mkdir -p "/opt/$APP" "/opt/$APP-homolog" "/opt/monitoring-agents" "/opt/backups"
chown -R "$DEPLOY_USER:$DEPLOY_USER" "/opt/$APP" "/opt/$APP-homolog" "/opt/monitoring-agents" "/opt/backups"

# ---- 6. Nginx (HTTP-only; o certbot adiciona o SSL depois) ----
echo "[6/9] Nginx..."
apt-get install -y -qq nginx certbot python3-certbot-nginx

criar_site() {
    local DOMAIN="$1" PORT="$2" NAME="$3"
    cat > "/etc/nginx/sites-available/$NAME" << EOF
# Gerado pelo setup-vps.sh — HTTP only.
# Rode 'certbot --nginx -d $DOMAIN' para adicionar o bloco 443 e o redirect.
server {
    listen 80;
    server_name $DOMAIN;

    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;

    client_max_body_size $MAX_UPLOAD;

    # Metricas — somente a VPS de monitoring
    location = /actuator/prometheus {
        allow $MONITORING_IP;
        deny all;
        proxy_pass http://127.0.0.1:$PORT;
        proxy_set_header Host \$host;
    }

    # Demais endpoints do actuator nunca sao publicos.
    # O smoke test do CI usa 127.0.0.1 direto no container, nao passa por aqui.
    location /actuator/ {
        deny all;
    }

    location / {
        proxy_pass http://127.0.0.1:$PORT;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_read_timeout 60s;
    }
}
EOF
    ln -sf "/etc/nginx/sites-available/$NAME" /etc/nginx/sites-enabled/
}

criar_site "$DOMAIN_PROD"    "$PORT_PROD"    "$APP"
criar_site "$DOMAIN_HOMOLOG" "$PORT_HOMOLOG" "$APP-homolog"
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl reload nginx

# ---- 7. .env dos dois ambientes (secrets gerados, nao "TROCAR") ----
echo "[7/9] Arquivos .env..."
criar_env() {
    local DIR="$1" DOMAIN="$2" TAG="$3" PORT="$4" DB_PORT="$5" PROFILE="$6" DB_NAME="$7"
    if [ -f "$DIR/.env" ]; then
        echo "  - $DIR/.env ja existe, mantido"
        return
    fi
    cat > "$DIR/.env" << ENVEOF
# Gerado pelo setup-vps.sh. Secrets ja aleatorios — nao precisa trocar.
IMAGE_TAG=$TAG
SPRING_PROFILE=$PROFILE
FRONTEND_PORT=$PORT
DB_PORT_HOST=$DB_PORT
DB_NAME=$DB_NAME
DB_USER=${DB_SLUG}_user
DB_PASSWORD=$(openssl rand -base64 32 | tr -d '/+=')
DB_ROOT_PASSWORD=$(openssl rand -base64 32 | tr -d '/+=')
JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
CORS_ALLOWED_ORIGINS=https://$DOMAIN
APP_BASE_URL=https://$DOMAIN
# Preencher com o provedor de e-mail real antes do go-live
MAIL_HOST=
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=noreply@$DOMAIN
ENVEOF
    chown "$DEPLOY_USER:$DEPLOY_USER" "$DIR/.env"
    chmod 600 "$DIR/.env"
}
criar_env "/opt/$APP"         "$DOMAIN_PROD"    "latest"  "$PORT_PROD"    "$DB_PORT_PROD"    "prod"    "${DB_SLUG}"
criar_env "/opt/$APP-homolog" "$DOMAIN_HOMOLOG" "homolog" "$PORT_HOMOLOG" "$DB_PORT_HOMOLOG" "homolog" "${DB_SLUG}_homolog"

# ---- 8. Backup diario da producao ----
echo "[8/9] Backup diario..."
if [ -f "/opt/$APP/backup.sh" ]; then
    chmod +x "/opt/$APP/backup.sh"
    cat > /etc/cron.d/$APP-backup << EOF
# Backup diario as 03:15 — restore drill obrigatorio antes do go-live
15 3 * * * $DEPLOY_USER /opt/$APP/backup.sh >> /var/log/$APP-backup.log 2>&1
EOF
    echo "  - cron criado"
else
    echo "  - AVISO: copie backup.sh para /opt/$APP/ e rode este script de novo"
fi

# ---- 9. Instrucoes finais ----
echo ""
echo "[9/9] Concluido. Proximos passos:"
echo " 1. DNS na Hostinger: $DOMAIN_PROD e $DOMAIN_HOMOLOG -> IP desta VPS (registro A)"
echo " 2. Chave publica do GitHub Actions em /home/$DEPLOY_USER/.ssh/authorized_keys"
echo " 3. SSL:  certbot --nginx -d $DOMAIN_PROD -d $DOMAIN_HOMOLOG --non-interactive --agree-tos -m $ADMIN_EMAIL --redirect"
echo " 4. Copiar docker-compose.vps.yml + backup.sh para /opt/$APP e /opt/$APP-homolog"
echo " 5. GitHub Secrets: SSH_HOST, SSH_USER=$DEPLOY_USER, SSH_KEY, GHCR_TOKEN (read:packages)"
echo " 6. Login no registry como $DEPLOY_USER:  docker login ghcr.io"
echo " 7. 1o deploy manual: cd /opt/$APP && docker compose pull && docker compose up -d"
echo " 8. Agentes de monitoring em /opt/monitoring-agents (repo monitoring)"
echo " 9. Registrar targets no Prometheus central + Uptime Kuma + bot Telegram do SaaS"
echo "10. RESTORE DRILL: rodar backup.sh, restaurar num banco de teste e conferir. Sem isso nao ha go-live."
echo ""
