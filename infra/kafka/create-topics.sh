#!/bin/sh
# Tópicos são infraestrutura: o broker não cria tópico sozinho, e as partições
# seguem o contrato em docs/eventos.md.
set -eu

create_topic() {
    /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --create --if-not-exists \
        --topic "$1" --partitions "$2" --replication-factor 1
}

create_topic payment-events 3
create_topic payment-events-dlt 3
