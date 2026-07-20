#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

random_hex() {
    local bytes="$1"
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -hex "$bytes"
        return
    fi
    od -An -N "$bytes" -tx1 /dev/urandom | tr -d ' \n'
}

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "缺少命令: $1" >&2
        exit 1
    fi
}

require_command docker

if ! docker compose version >/dev/null 2>&1; then
    echo "需要 Docker Compose v2。" >&2
    exit 1
fi

if [ ! -f .env ]; then
    cp .env.example .env
    DB_PASSWORD="$(random_hex 24)"
    DB_ROOT_PASSWORD="$(random_hex 24)"
    JWT_SECRET="$(random_hex 48)"

    # 首次安装生成独立密钥，避免示例凭据进入运行环境。
    sed -i "s/change-me-database-password/$DB_PASSWORD/" .env
    sed -i "s/change-me-root-password/$DB_ROOT_PASSWORD/" .env
    sed -i "s/change-me-to-at-least-32-random-bytes/$JWT_SECRET/" .env
    chmod 600 .env
fi

export APP_GIT_SHA="$(git rev-parse --verify HEAD 2>/dev/null || echo unknown)"
docker compose up -d --build --remove-orphans
docker compose ps

echo
echo "XianYuPlus 已启动: http://localhost:12400"
echo "公网部署需先配置 deploy/nginx/certs、ALLOWED_ORIGINS 和 TRUST_PROXY，再执行:"
echo "docker compose --profile proxy up -d"
