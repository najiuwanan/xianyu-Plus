#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

echo "正在从 GitHub 拉取最新代码..."
git pull origin main

echo "正在重新构建并重启容器..."
export APP_GIT_SHA="$(git rev-parse --verify HEAD 2>/dev/null || echo unknown)"
docker compose up -d --build

docker compose ps

echo
echo "更新完成！XianYuPlus 已重启。"
