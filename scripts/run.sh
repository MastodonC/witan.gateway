#!/usr/bin/env bash
set -o errexit
set -o nounset
set -o xtrace

/root/download-secrets.sh

export BIND_ADDR="${BIND_ADDR:-$(hostname --ip-address)}"
export APP_NAME=$(echo "witan.gateway" | sed s/"-"/"_"/g)
exec java ${PEER_JAVA_OPTS:-} -jar /srv/witan.gateway.jar
