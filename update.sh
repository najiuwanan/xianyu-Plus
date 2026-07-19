#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

echo "正在从 GitHub 拉取最新代码..."
git pull origin main

echo "正在重新构建并重启容器..."
export APP_GIT_SHA="$(git rev-parse --verify HEAD 2>/dev/null || echo unknown)"

# V1.4.0 compatibility recovery: some legacy databases reject the optional
# blacklist -> account foreign key and leave Flyway V21 in a failed state.
# Repair only that exact failed migration before rebuilding the application.
docker compose up -d mysql
for attempt in $(seq 1 60); do
    if docker compose exec -T mysql sh -c 'mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' >/dev/null 2>&1; then
        break
    fi
    if [ "$attempt" -eq 60 ]; then
        echo "MySQL 未能在规定时间内就绪，更新已停止。"
        exit 1
    fi
    sleep 2
done

V21_FAILED="$(docker compose exec -T mysql sh -c 'mysql -N -s -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "SELECT COUNT(*) FROM flyway_schema_history WHERE version='"'"'21'"'"' AND success=0"' 2>/dev/null || true)"
if [ "${V21_FAILED//$'\r'/}" = "1" ]; then
    echo "检测到 V21 黑名单迁移失败，正在自动兼容修复..."
    docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' < deploy/sql/repair-v21-buyer-blacklist.sql
fi

docker compose up -d --build

docker compose ps

echo
echo "更新完成！XianYuPlus 已重启。"
