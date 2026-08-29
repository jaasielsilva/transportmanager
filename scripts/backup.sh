#!/bin/bash
# =============================================================
# Backup diario do banco — molde do kit padrao-projeto
# Copiar para /opt/transportmanager/backup.sh (chmod +x).
# O setup-vps.sh cria o cron (03:15, usuario deploy).
#
# Backup que nunca foi restaurado nao e backup.
# RESTORE DRILL obrigatorio antes do go-live e a cada 3 meses:
#   gunzip < /opt/backups/transportmanager-AAAA-MM-DD.sql.gz | \
#     docker compose exec -T db mysql -u root -p"$DB_ROOT_PASSWORD" nome_do_banco_teste
# =============================================================

set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_DIR="/opt/backups"
RETENCAO_DIAS=30

cd "$APP_DIR"
set -a; source .env; set +a

DATA=$(date +%F-%H%M)
ARQUIVO="$BACKUP_DIR/${DB_NAME}-${DATA}.sql.gz"
mkdir -p "$BACKUP_DIR"

echo "[$(date -Is)] Iniciando backup de $DB_NAME"

# --single-transaction: dump consistente sem travar a aplicacao (InnoDB)
docker compose exec -T db \
    mysqldump -u root -p"$DB_ROOT_PASSWORD" \
        --single-transaction --quick --routines --triggers --events \
        "$DB_NAME" | gzip -9 > "$ARQUIVO"

TAMANHO=$(stat -c%s "$ARQUIVO")
if [ "$TAMANHO" -lt 10240 ]; then
    echo "[$(date -Is)] ERRO: dump com apenas ${TAMANHO}B — provavel falha"
    rm -f "$ARQUIVO"
    exit 1
fi

echo "[$(date -Is)] OK: $ARQUIVO ($(numfmt --to=iec "$TAMANHO"))"

# Uploads (se o projeto ainda nao migrou para storage S3-compatible)
if docker volume ls -q | grep -q uploads_data; then
    tar czf "$BACKUP_DIR/uploads-${DATA}.tar.gz" \
        -C /var/lib/docker/volumes/$(basename "$APP_DIR")_uploads_data/_data . 2>/dev/null || true
fi

find "$BACKUP_DIR" -name '*.gz' -mtime +$RETENCAO_DIAS -delete

# ---- Copia off-site ----
# Backup na mesma VPS nao protege contra perda da VPS. Descomente uma das opcoes
# e configure a credencial no .env. Isto NAO e opcional em producao.
#
# rclone copy "$ARQUIVO" remoto:transportmanager-backups/
# aws s3 cp "$ARQUIVO" s3://transportmanager-backups/ --storage-class STANDARD_IA

# Marca de vida para o alerta "backup nao rodou" (Prometheus/Alertmanager)
date +%s > "$BACKUP_DIR/.ultimo-backup-${DB_NAME}"
