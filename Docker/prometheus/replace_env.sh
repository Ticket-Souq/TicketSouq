#!/bin/sh
set -e
sed "s|\${EUREKA_HOST}|${EUREKA_HOST:-host.docker.internal}|g" \
    /etc/prometheus/prometheus.tmpl > /etc/prometheus/prometheus.yml
exec /bin/prometheus --config.file=/etc/prometheus/prometheus.yml \
    --enable-feature=exemplar-storage \
    --enable-feature=remote-write-receiver
