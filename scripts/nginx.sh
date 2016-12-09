#!/usr/bin/env bash

SERVER_ADDR=${NGINX_SERVER_ADDR:-localhost}
SERVER_PORT=${NGINX_SERVER_PORT:-30015}

PROXY_CONFIG_FILE=/etc/nginx/sites-available/witan-gateway

echo "SERVER_ADDR is ${SERVER_ADDR}:${SERVER_PORT}"

cat > ${PROXY_CONFIG_FILE} <<EOF
server {

        listen 80 default_server;

        client_max_body_size 1000M;

        error_log /var/log/nginx/error.log;

        server_name witan-gateway;

        location /ws {
            access_log /var/log/nginx/access.log;

            # Assumes we are already behind a reverse proxy (e.g. ELB)
            real_ip_header X-Forwarded-For;
            set_real_ip_from 0.0.0.0/0;

            proxy_pass http://${SERVER_ADDR}:${SERVER_PORT};
            proxy_http_version 1.1;
            proxy_set_header Upgrade \$http_upgrade;
            proxy_set_header Connection "upgrade";
        }

        location / {
            access_log /var/log/nginx/access.log;

            # Assumes we are already behind a reverse proxy (e.g. ELB)
            real_ip_header X-Forwarded-For;
            set_real_ip_from 0.0.0.0/0;

            proxy_pass http://${SERVER_ADDR}:${SERVER_PORT};
        }
}
EOF

rm /etc/nginx/sites-enabled/*

ln -sf ${PROXY_CONFIG_FILE} /etc/nginx/sites-enabled/default

nginx  >>/var/log/nginx.log 2>&1
