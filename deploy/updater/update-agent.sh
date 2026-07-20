#!/usr/bin/env bash

set -uo pipefail

WORKSPACE="${UPDATE_WORKSPACE:-/workspace}"
CONTROL_ROOT="${UPDATE_CONTROL_ROOT:-/control/update}"
REQUEST_FILE="$CONTROL_ROOT/request.json"
STATUS_FILE="$CONTROL_ROOT/status.json"
LOG_FILE="$CONTROL_ROOT/update.log"
HEARTBEAT_FILE="$CONTROL_ROOT/agent.heartbeat"
BRANCH="${ONLINE_UPDATE_BRANCH:-main}"
ESTIMATED_DOWNTIME_SECONDS="${ONLINE_UPDATE_DOWNTIME_SECONDS:-120}"

mkdir -p "$CONTROL_ROOT" "$CONTROL_ROOT/backups"
chmod 0777 "$CONTROL_ROOT" 2>/dev/null || true

heartbeat_loop() {
    while true; do
        touch "$HEARTBEAT_FILE"
        chmod 666 "$HEARTBEAT_FILE" 2>/dev/null || true
        sleep 5
    done
}

heartbeat_loop &
HEARTBEAT_PID=$!
cleanup() {
    kill "$HEARTBEAT_PID" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 0' INT TERM
chmod 700 "$CONTROL_ROOT/backups" 2>/dev/null || true
touch "$LOG_FILE"
chmod 666 "$LOG_FILE" 2>/dev/null || true
git config --global --add safe.directory "$WORKSPACE"

now() { date -u +"%Y-%m-%dT%H:%M:%SZ"; }

write_status() {
    local state="$1" stage="$2" progress="$3" message="$4" target_commit="${5:-}"
    local temp="$STATUS_FILE.tmp"
    jq -n \
        --arg requestId "$CURRENT_REQUEST_ID" \
        --arg state "$state" \
        --arg stage "$stage" \
        --arg message "$message" \
        --arg startedAt "$CURRENT_STARTED_AT" \
        --arg updatedAt "$(now)" \
        --arg targetCommit "$target_commit" \
        --argjson progress "$progress" \
        --argjson downtime "$ESTIMATED_DOWNTIME_SECONDS" \
        '{enabled:true,requestId:$requestId,state:$state,stage:$stage,progress:$progress,message:$message,estimatedDowntimeSeconds:$downtime,startedAt:$startedAt,updatedAt:$updatedAt,targetCommit:$targetCommit,logs:[]}' > "$temp"
    chmod 666 "$temp" 2>/dev/null || true
    mv -f "$temp" "$STATUS_FILE"
}

log_step() {
    printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$1" | tee -a "$LOG_FILE"
}

fail_update() {
    local message="$1"
    log_step "失败：$message"
    write_status "FAILED" "FAILED" 100 "$message" "${TARGET_COMMIT:-}"
}

wait_mysql() {
    for attempt in $(seq 1 60); do
        if docker compose exec -T mysql sh -c 'mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' >/dev/null 2>&1; then
            return 0
        fi
        sleep 2
    done
    return 1
}

wait_app_health() {
    local app_container_id
    app_container_id="$(docker compose ps -q app)"
    [ -n "$app_container_id" ] || return 1
    for attempt in $(seq 1 90); do
        local health
        health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$app_container_id" 2>/dev/null || true)"
        [ "$health" = "healthy" ] && return 0
        [ "$health" = "unhealthy" ] && return 1
        sleep 2
    done
    return 1
}

run_update() {
    cd "$WORKSPACE" || { fail_update "项目目录不可用"; return; }
    : > "$LOG_FILE"
    chmod 666 "$LOG_FILE" 2>/dev/null || true

    write_status "RUNNING" "PREPARING" 5 "正在检查磁盘空间、代码状态与 Docker 环境" "$TARGET_COMMIT"
    log_step "开始在线更新，请求 $CURRENT_REQUEST_ID"
    if ! docker compose version >> "$LOG_FILE" 2>&1; then
        fail_update "Docker Compose 不可用"
        return
    fi
    if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
        fail_update "服务器存在未提交的代码修改，为避免覆盖已停止在线更新"
        return
    fi

    write_status "RUNNING" "BACKUP" 12 "正在备份数据库；此时服务仍可正常使用" "$TARGET_COMMIT"
    log_step "启动并检查 MySQL"
    if ! docker compose up -d mysql >> "$LOG_FILE" 2>&1 || ! wait_mysql; then
        fail_update "MySQL 未能就绪，更新已停止"
        return
    fi
    local backup_file="$CONTROL_ROOT/backups/xianyu-plus-$(date '+%Y%m%d-%H%M%S').sql.gz"
    log_step "备份数据库到持久化更新目录"
    if ! docker compose exec -T mysql sh -c 'mysqldump --single-transaction --quick -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' 2>> "$LOG_FILE" | gzip > "$backup_file"; then
        rm -f "$backup_file"
        fail_update "数据库备份失败，未执行更新"
        return
    fi
    find "$CONTROL_ROOT/backups" -maxdepth 1 -type f -name 'xianyu-plus-*.sql.gz' -printf '%T@ %p\n' 2>/dev/null | sort -nr | tail -n +6 | cut -d' ' -f2- | xargs -r rm -f

    write_status "RUNNING" "DOWNLOADING" 22 "正在从 GitHub 下载并校验最新代码" "$TARGET_COMMIT"
    log_step "从 origin/$BRANCH 获取最新代码"
    if ! git fetch --progress origin "$BRANCH" >> "$LOG_FILE" 2>&1; then
        fail_update "GitHub 代码下载失败，请检查网络"
        return
    fi
    TARGET_COMMIT="$(git rev-parse "origin/$BRANCH" 2>/dev/null || true)"
    if ! git merge --ff-only "origin/$BRANCH" >> "$LOG_FILE" 2>&1; then
        fail_update "本地分支与 GitHub 存在分叉，请使用 update.sh 人工处理"
        return
    fi

    write_status "RUNNING" "BUILDING" 42 "代码下载完成，正在构建新镜像；服务仍可正常使用，耗时通常最长" "$TARGET_COMMIT"
    log_step "构建新的应用镜像"
    export APP_GIT_SHA="$TARGET_COMMIT"
    local app_image="${APP_IMAGE:-xianyu-plus:latest}"
    local old_image_id
    old_image_id="$(docker image inspect "$app_image" --format '{{.Id}}' 2>/dev/null || true)"
    if ! docker compose build app >> "$LOG_FILE" 2>&1; then
        fail_update "新镜像构建失败，现有服务未被中断"
        return
    fi

    write_status "RUNNING" "DATABASE_CHECK" 70 "镜像构建完成，正在执行数据库兼容检查" "$TARGET_COMMIT"
    log_step "执行历史数据库兼容检查"
    local v21_failed
    v21_failed="$(docker compose exec -T mysql sh -c 'mysql -N -s -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "SELECT COUNT(*) FROM flyway_schema_history WHERE version='"'"'21'"'"' AND success=0"' 2>/dev/null || true)"
    if [ "${v21_failed//$'\r'/}" = "1" ]; then
        if ! docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' < deploy/sql/repair-v21-buyer-blacklist.sql >> "$LOG_FILE" 2>&1; then
            fail_update "数据库兼容修复失败，现有服务未被替换"
            return
        fi
    fi

    write_status "RESTARTING" "RESTARTING" 80 "正在切换到新版本，预计中断 30～120 秒，请不要关闭页面" "$TARGET_COMMIT"
    log_step "重建应用容器，短暂停机从此处开始"
    if ! docker compose up -d --no-deps --force-recreate app >> "$LOG_FILE" 2>&1; then
        fail_update "新应用容器启动失败"
        return
    fi

    write_status "RESTARTING" "HEALTH_CHECK" 90 "新版本已启动，正在等待数据库迁移和应用健康检查" "$TARGET_COMMIT"
    log_step "等待新应用通过健康检查"
    if ! wait_app_health; then
        log_step "新版本健康检查失败，准备恢复旧镜像"
        if [ -n "$old_image_id" ]; then
            docker tag "$old_image_id" "$app_image" >> "$LOG_FILE" 2>&1 || true
            docker compose up -d --no-deps --force-recreate app >> "$LOG_FILE" 2>&1 || true
            wait_app_health || true
            fail_update "新版本健康检查失败，已尝试恢复旧版本；请查看更新日志"
        else
            fail_update "新版本健康检查失败且没有可用旧镜像，请执行 update.sh 排查"
        fi
        return
    fi

    log_step "更新完成，新应用已通过健康检查"
    write_status "SUCCEEDED" "COMPLETED" 100 "更新完成，新版本已恢复服务" "$TARGET_COMMIT"
}

log_step "安全在线更新代理已启动"
while true; do
    if [ -s "$REQUEST_FILE" ]; then
        CURRENT_REQUEST_ID="$(jq -r '.requestId // empty' "$REQUEST_FILE" 2>/dev/null || true)"
        TARGET_COMMIT="$(jq -r '.targetCommit // empty' "$REQUEST_FILE" 2>/dev/null || true)"
        if [[ "$CURRENT_REQUEST_ID" =~ ^[0-9a-fA-F-]{36}$ ]]; then
            CURRENT_STARTED_AT="$(now)"
            rm -f "$REQUEST_FILE"
            run_update
        else
            rm -f "$REQUEST_FILE"
            CURRENT_REQUEST_ID="invalid-request"
            CURRENT_STARTED_AT="$(now)"
            fail_update "更新请求格式无效"
        fi
    fi
    sleep 2
done
