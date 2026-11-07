#!/bin/sh
set -eu

envsubst < /etc/coturn/turnserver.conf.template > /tmp/turnserver.conf
exec turnserver -c /tmp/turnserver.conf --no-cli
