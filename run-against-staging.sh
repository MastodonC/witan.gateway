#!/usr/bin/env bash

STAGING_AUTH_PUBKEY=${1:?"Location of the public key used for user auth encryption, should be in keybase/witan/staging/"}
STAGING_ACCESS_PEM=${2:?"Pem file for accessing staging"}

DATASTORE_IP=$(curl "http://masters.staging.witan.mastodonc.net/marathon/v2/apps/kixi.datastore/tasks" 2> /dev/null | jq '.tasks[].host' | sort -R | head -n 1 | xargs echo)
HEIMDALL_IP=$(curl "http://masters.staging.witan.mastodonc.net/marathon/v2/apps/kixi.heimdall/tasks" 2> /dev/null | jq '.tasks[].host' | sort -R | head -n 1 | xargs echo)

echo "Adding datastore hosts line"
sudo cp /etc/hosts .temp_hosts
echo "127.0.0.1 kixi.datastore.marathon.mesos" | sudo tee -a /etc/hosts
echo "127.0.0.1 kixi.heimdall.marathon.mesos" | sudo tee -a /etc/hosts

ssh -oStrictHostKeyChecking=no core@$DATASTORE_IP -i $STAGING_ACCESS_PEM -L 18080:$DATASTORE_IP:18080 -N &
DATASTORE_TUNNEL_PID=$!
ssh -oStrictHostKeyChecking=no core@$HEIMDALL_IP -i $STAGING_ACCESS_PEM -L 10010:$HEIMDALL_IP:10010 -N &
HEIMDALL_TUNNEL_PID=$!

function cleanup {

    echo "Tearing down tunnels"
    kill $DATASTORE_TUNNEL_PID $HEIMDALL_TUNNEL_PID

    echo "Restoring original hosts file"
    sudo mv .temp_hosts /etc/hosts
}
trap cleanup EXIT
echo "Creating resources/local.edn"
echo "{:zk \"$(dig masters.staging.witan.mastodonc.net A +noall +answer | tail -n 1 | awk '{print $NF}')\" :auth-pubkey \"$STAGING_AUTH_PUBKEY\"}" > resources/local.edn
echo "You can now run witan.gateway with the :dev-staging profile. Ctrl+C to exit..."
while true; do sleep 2; done
