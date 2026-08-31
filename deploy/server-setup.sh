#!/usr/bin/env bash
#
# First-time setup for the SAIComex platform on the existing Hetzner VPS.
# Run once, as root, ON THE SERVER. Idempotent: safe to re-run.
#
#   scp deploy/server-setup.sh root@89.167.106.195:/tmp/
#   ssh root@89.167.106.195 'bash /tmp/server-setup.sh'
#
# It does NOT touch anything belonging to the SAI Fleet stacks.

set -euo pipefail

APP_DIR=/opt/saicomex
DOMAIN="${DOMAIN:-comex.saifleet.co.za}"
CERT_EMAIL="${CERT_EMAIL:-ramyasri.adusumilli@gmail.com}"

say() { printf '\n\033[1;33m==> %s\033[0m\n' "$*"; }

say "Checking the ports this stack wants are free"
for port in 8090 8091 5434 9200 9201; do
  if ss -lnt | awk '{print $4}' | grep -qE "[:.]${port}\$"; then
    echo "  PORT ${port} IS ALREADY IN USE — resolve this before continuing:"
    ss -lntp | grep -E "[:.]${port}\s" || true
    exit 1
  fi
  echo "  ${port} free"
done

say "Creating ${APP_DIR}"
mkdir -p "${APP_DIR}"/{backups,nginx}
chmod 750 "${APP_DIR}"

if [[ ! -f "${APP_DIR}/.env" ]]; then
  say "No .env yet — writing one with generated secrets"
  DB_PASSWORD=$(openssl rand -base64 32 | tr -d '/+=' | head -c 32)
  JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
  MINIO_ACCESS_KEY=comexadmin
  MINIO_SECRET_KEY=$(openssl rand -base64 32 | tr -d '/+=' | head -c 32)

  cat > "${APP_DIR}/.env" <<EOF
PUBLIC_URL=https://${DOMAIN}
DB_NAME=saicomex
DB_USER=comex_admin
DB_PASSWORD=${DB_PASSWORD}
JWT_SECRET=${JWT_SECRET}
MINIO_ACCESS_KEY=${MINIO_ACCESS_KEY}
MINIO_SECRET_KEY=${MINIO_SECRET_KEY}
MINIO_BUCKET=comex-files
REPORTING_CURRENCY=USD
TZ=Africa/Johannesburg
ADMIN_EMAIL=${CERT_EMAIL}
MAIL_FROM=noreply@saicomex.com
SWAGGER_ENABLED=false
EOF
  chmod 600 "${APP_DIR}/.env"
  echo "  Generated secrets are in ${APP_DIR}/.env (chmod 600)."
  echo "  Copy them into your password manager now — they are not stored anywhere else."
else
  say ".env already exists — leaving it alone"
fi

say "Installing the nginx site"
if [[ -f "${APP_DIR}/nginx/saicomex.conf" ]]; then
  cp "${APP_DIR}/nginx/saicomex.conf" /etc/nginx/sites-available/saicomex.conf
elif [[ -f /tmp/saicomex.conf ]]; then
  cp /tmp/saicomex.conf /etc/nginx/sites-available/saicomex.conf
else
  echo "  saicomex.conf not found — copy deploy/nginx/saicomex.conf to /tmp first"
  exit 1
fi
ln -sf /etc/nginx/sites-available/saicomex.conf /etc/nginx/sites-enabled/saicomex.conf

say "Obtaining the TLS certificate for ${DOMAIN}"
# The site file references certificate paths that do not exist yet, so nginx
# cannot be reloaded until certbot has written them. --nginx handles the
# ordering itself; if DNS is not pointing here yet this is the step that fails,
# and it fails clearly.
if [[ ! -d "/etc/letsencrypt/live/${DOMAIN}" ]]; then
  certbot --nginx -d "${DOMAIN}" --non-interactive --agree-tos -m "${CERT_EMAIL}" --redirect
else
  echo "  Certificate already present"
fi

say "Testing and reloading nginx"
nginx -t
systemctl reload nginx

say "Done"
cat <<EOF

Next steps, from your workstation:

  1. Build and ship the images   ->  deploy/deploy.ps1  (or the manual steps in docs/DEPLOYMENT.md)
  2. Start the stack             ->  ssh root@<server> 'cd ${APP_DIR} && docker compose up -d'
  3. Create the MinIO bucket     ->  see docs/DEPLOYMENT.md, "First-time MinIO bucket"
  4. Sign in at https://${DOMAIN} as admin@saicomex.com and set a real password.

EOF
