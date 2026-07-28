#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

LOCAL_BACKUP_CREATED=0
if ! git diff --quiet || ! git diff --cached --quiet || [ -n "$(git ls-files --others --exclude-standard)" ]; then
    UPDATE_BACKUP_LABEL="xianyu-plus-update-backup-$(date +%Y%m%d-%H%M%S)"
    echo "检测到本地文件改动，正在备份后再更新..."
    git stash push --include-untracked -m "$UPDATE_BACKUP_LABEL"
    LOCAL_BACKUP_CREATED=1
fi

echo "正在从 GitHub 拉取最新代码..."
git pull --ff-only origin main

if [ "$LOCAL_BACKUP_CREATED" -eq 1 ]; then
    echo "本地改动已安全保存在 Git stash 中，不会覆盖新版文件。"
    echo "如需查看备份：git stash list"
fi

if [ ! -f .env ]; then
    echo "缺少 .env，无法安全更新。请先执行 ./install.sh。" >&2
    exit 1
fi

# 清理早期第三容器更新器遗留配置；当前在线更新使用宿主机 systemd，不增加容器。
sed -i '/^ONLINE_UPDATE_ENABLED=/d;/^ONLINE_UPDATE_BRANCH=/d;/^ONLINE_UPDATE_DOWNTIME_SECONDS=/d;/^UPDATER_IMAGE=/d;/^HOST_PROJECT_DIR=/d' .env

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
ensure_setting UPDATE_HOST_DIR ./runtime/update

# 仅迁移项目过去的默认镜像名；用户自行配置的远程镜像保持不变。
if grep -q '^APP_IMAGE=xianyusmart:latest$' .env; then
    sed -i 's/^APP_IMAGE=xianyusmart:latest$/APP_IMAGE=xianyu-plus:latest/' .env
fi

UPDATE_HOST_DIR="$(sed -n 's/^UPDATE_HOST_DIR=//p' .env | tail -n 1)"
UPDATE_HOST_DIR="${UPDATE_HOST_DIR:-./runtime/update}"
if [[ "$UPDATE_HOST_DIR" != /* ]]; then UPDATE_HOST_DIR="$ROOT_DIR/$UPDATE_HOST_DIR"; fi
mkdir -p "$UPDATE_HOST_DIR"
chmod 1777 "$UPDATE_HOST_DIR"
if [ -f "$UPDATE_HOST_DIR/request.json" ]; then
    echo "检测到网页在线更新正在执行，请等待完成后再运行 update.sh。" >&2
    exit 1
fi
if [ -f "$UPDATE_HOST_DIR/app.jar" ]; then
    mv -f "$UPDATE_HOST_DIR/app.jar" "$UPDATE_HOST_DIR/app.jar.source-update-backup"
    echo "已停用在线JAR覆盖，本次将运行刚构建的源码版本。"
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

docker compose up -d --build --remove-orphans

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
docker image rm xianyu-plus-updater:latest >/dev/null 2>&1 || true
docker network rm xianyusmart_xianyusmart >/dev/null 2>&1 || true

docker compose ps

echo
echo "更新完成！XianYuPlus 已重启。"
