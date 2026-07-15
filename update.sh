#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

echo "正在从 GitHub 拉取最新代码..."
git pull origin main

echo "正在重新构建并重启容器..."
docker compose up -d --build

docker compose ps

echo
echo "更新完成！XianYu-Plus 已重启。"
