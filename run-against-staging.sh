#!/bin/bash

STAGING_AUTH_PUBKEY=${1:?"Location of the public key used for user auth encryption, should be in keybase/witan/staging/"}
STAGING_ACCESS_PEM=${2:?"Pem file for accessing staging"}

DATASTORE_IP=$(curl "http://masters.staging.witan.mastodonc.net/marathon/v2/apps/kixi.datastore/tasks" 2> /dev/null | jq '.tasks[].host' | sort -R | head -n 1 | xargs echo)
HEIMDALL_IP=$(curl "http://masters.staging.witan.mastodonc.net/marathon/v2/apps/kixi.heimdall/tasks" 2> /dev/null | jq '.tasks[].host' | sort -R | head -n 1 | xargs echo)

echo "Adding datastore hosts line"
echo "127.0.0.1 kixi.datastore.marathon.mesos" | sudo tee -a /etc/hosts

ssh core@$DATASTORE_IP -i $STAGING_ACCESS_PEM -L 18080:$DATASTORE_IP:18080 -N &
DATASTORE_TUNNEL_PID=$!
ssh core@$HEIMDALL_IP -i $STAGING_ACCESS_PEM -L 10010:$HEIMDALL_IP:10010 -N &
HEIMDALL_TUNNEL_PID=$!

function cleanup {
  echo "Tearing down tunnels"
  kill $DATASTORE_TUNNEL_PID $HEIMDALL_TUNNEL_PID

  echo "Removing datastore hosts line"
  sudo head -n -1 /etc/hosts > temp_hosts ; sudo mv temp_hosts /etc/hosts
}
trap cleanup EXIT

DATASTORE_HOST=kixi.datastore.marathon.mesos HEIMDALL_PORT=10010 DATASTORE_PORT=18080 ZOOKEEPER=masters.staging.witan.mastodonc.net SUPER_SECRET_PUBLIC_PEM_FILE=$STAGING_AUTH_PUBKEY lein run -m witan.gateway.system development
