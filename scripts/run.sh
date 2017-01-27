#!/usr/bin/env bash
set -o errexit
set -o nounset
set -o xtrace

/root/download-secrets.sh

SANDBOX=${MESOS_SANDBOX:-"."}

export BIND_ADDR="${BIND_ADDR:-$(hostname --ip-address)}"
export APP_NAME=$(echo "witan.gateway" | sed s/"-"/"_"/g)
exec java -XX:+HeapDumpOnOutOfMemoryError -XX:+UseG1GC -Xloggc:$SANDBOX/gc.log -XX:+PrintGCCause -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=3 -XX:GCLogFileSize=2M ${JAVA_OPTS:-} -jar /srv/witan.gateway.jar
