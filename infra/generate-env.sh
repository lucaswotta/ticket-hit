#!/bin/sh
# Gera o .env com senhas aleatórias e o par de chaves EC P-256 do passe da fila.
# Os nomes vêm do .env.example, que é a lista oficial de variáveis; as que já
# têm valor lá são configuração e são copiadas como estão.
set -eu
cd "$(dirname "$0")/.."

if [ -e .env ] && [ "${1:-}" != "--force" ]; then
    echo ".env já existe; use --force para recriar (as senhas dos volumes deixam de valer)" >&2
    exit 1
fi

key=$(mktemp)
trap 'rm -f "$key"' EXIT
# Em DER, o genpkey grava EC no formato SEC1; o Java só lê PKCS#8.
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 \
    | openssl pkcs8 -topk8 -nocrypt -outform DER -out "$key"

umask 077
grep -E '^[A-Z_]+=' .env.example | while IFS='=' read -r name default; do
    if [ -n "$default" ]; then
        echo "$name=$default"
        continue
    fi
    case "$name" in
        QUEUE_PASS_PRIVATE_KEY) echo "$name=$(openssl base64 -A < "$key")" ;;
        QUEUE_PASS_PUBLIC_KEY) echo "$name=$(openssl pkey -inform DER -in "$key" -pubout -outform DER | openssl base64 -A)" ;;
        *) echo "$name=$(openssl rand -hex 24)" ;;
    esac
done > .env
