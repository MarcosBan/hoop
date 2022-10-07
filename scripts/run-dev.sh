#!/bin/bash

trap ctrl_c INT

function ctrl_c() {
    ps aux |grep -i /tmp/hoop |awk {'print $2'} |xargs kill -9
    docker stop xtdb 2> /dev/null || true
    exit 130
}

echo "--> STARTING XTDB..."
docker rm xtdb 2>/dev/null || true
docker stop xtdb 2>/dev/null || true
docker run --name xtdb --rm -d -p 3000:3000 runops/xtdb-in-memory > /dev/null
until curl -s -f -o /dev/null "http://127.0.0.1:3000/_xtdb/status"
do
  echo -n "."
  sleep 1
done
echo " done!"
echo "--> STARTING GATEWAY ..."

export PORT=8009
export PROFILE=dev
export GIN_MODE=release
export XTDB_ADDRESS=http://127.0.0.1:3000
go build -o /tmp/hoop github.com/runopsio/hoop/client
/tmp/hoop start gateway &

unset PORT PROFILE GIN_MODE XTDB_ADDRESS

until curl -s -f -o /dev/null "http://127.0.0.1:8009/api/agents"
do
    sleep 1
done
echo "--> GATEWAY IS READY!"
echo "--> STARTING AGENT ..."
/tmp/hoop start agent