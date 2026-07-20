#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

echo "正在从 GitHub 拉取最新代码..."
git pull origin main

if [ ! -f .env ]; then
    echo "缺少 .env，无法安全更新。请先执行 ./install.sh。" >&2
    exit 1
fi

# V1.8.0 品牌迁移：旧安装继续复用原有数据卷；全新安装使用 xianyu-plus 名称。
ensure_volume_setting() {
    local key="$1"
    local new_name="$2"
    local legacy_name="$3"
    if grep -q "^${key}=" .env; then
        return
    fi
    if docker volume inspect "$legacy_name" >/dev/null 2>&1; then
        printf '\n%s=%s\n' "$key" "$legacy_name" >> .env
        echo "检测到旧数据卷 $legacy_name，将继续安全复用。"
    else
        printf '\n%s=%s\n' "$key" "$new_name" >> .env
    fi
}

ensure_setting() {
    local key="$1"
    local value="$2"
    if ! grep -q "^${key}=" .env; then
        printf '\n%s=%s\n' "$key" "$value" >> .env
    fi
}

ensure_volume_setting MYSQL_DATA_VOLUME xianyu-plus-mysql-data xianyusmart_mysql-data
ensure_volume_setting APP_DATA_VOLUME xianyu-plus-app-data xianyusmart_app-data
ensure_volume_setting APP_LOGS_VOLUME xianyu-plus-app-logs xianyusmart_app-logs
ensure_setting APP_NETWORK_NAME xianyu-plus
ensure_setting APP_IMAGE xianyu-plus:latest

# 仅迁移项目过去的默认镜像名；用户自行配置的远程镜像保持不变。
if grep -q '^APP_IMAGE=xianyusmart:latest$' .env; then
    sed -i 's/^APP_IMAGE=xianyusmart:latest$/APP_IMAGE=xianyu-plus:latest/' .env
fi

if docker ps -aq --filter label=com.docker.compose.project=xianyusmart | grep -q .; then
    echo "正在停止旧名称的 XianYuSmart 容器（不会删除数据卷）..."
    docker compose -p xianyusmart down --remove-orphans
fi

echo "正在以 XianYuPlus 名称重新构建并启动容器..."
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

echo "正在等待 XianYuPlus 应用通过健康检查..."
APP_CONTAINER_ID="$(docker compose ps -q app)"
for attempt in $(seq 1 90); do
    APP_HEALTH="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$APP_CONTAINER_ID" 2>/dev/null || true)"
    if [ "$APP_HEALTH" = "healthy" ]; then
        break
    fi
    if [ "$APP_HEALTH" = "unhealthy" ] || [ "$attempt" -eq 90 ]; then
        echo "XianYuPlus 未能通过健康检查，旧镜像将保留以便排查。" >&2
        docker compose logs --no-color --tail=120 app >&2
        exit 1
    fi
    sleep 2
done

# 新服务确认健康后再移除旧应用镜像；数据库镜像和数据卷不会删除。
docker image rm xianyusmart:latest >/dev/null 2>&1 || true
docker network rm xianyusmart_xianyusmart >/dev/null 2>&1 || true

docker compose ps

echo
echo "更新完成！XianYuPlus 已重启。"
