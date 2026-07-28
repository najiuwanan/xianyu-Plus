#!/usr/bin/env bash

set -Eeuo pipefail

: "${PROJECT_DIR:?PROJECT_DIR is required}"
: "${UPDATE_DIR:?UPDATE_DIR is required}"

ENV_FILE="$PROJECT_DIR/.env"
COMPOSE_FILE="$PROJECT_DIR/compose.yaml"
REQUEST="$UPDATE_DIR/request.json"
STATUS="$UPDATE_DIR/status.json"
ACTIVE_JAR="$UPDATE_DIR/app.jar"
BACKUP_JAR="$UPDATE_DIR/app.jar.previous"
INSTALL_MARKER="$UPDATE_DIR/installing.task"
MAINTENANCE_FLAG="$UPDATE_DIR/maintenance.flag"
RELEASE_API="${UPDATE_RELEASE_API:-https://api.github.com/repos/najiuwanan/xianyu-Plus/releases/latest}"
WORK_DIR="$(mktemp -d "$UPDATE_DIR/work.XXXXXX")"
DOWNLOAD_ATTEMPTS=5
TASK_ID=""
TARGET_VERSION=""
REQUESTED_AT=""
REQUEST_UID=""
REQUEST_GID=""
CURRENT_PROGRESS=0
DOWNLOADED_BYTES=0
TOTAL_BYTES=0
ROLLBACK_REQUIRED=false
HAD_ACTIVE_JAR=false
FAILURE_MESSAGE="在线更新失败，当前可用版本已保留"

compose() {
    docker compose --project-directory "$PROJECT_DIR" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

request_task_id() {
    [[ -f "$REQUEST" ]] || return 1
    python3 - "$REQUEST" <<'PY'
import json, sys
print(json.load(open(sys.argv[1], encoding="utf-8")).get("taskId", ""))
PY
}

task_is_current() {
    [[ -n "$TASK_ID" ]] || return 0
    [[ "$(request_task_id 2>/dev/null || true)" == "$TASK_ID" ]]
}

remove_current_request() {
    if [[ -z "$TASK_ID" ]] || task_is_current; then
        rm -f "$REQUEST"
    fi
}

status() {
    local state="$1" progress="${2:-$CURRENT_PROGRESS}" message="${3:-在线更新处理中}"
    local downloaded="${4:-$DOWNLOADED_BYTES}" total="${5:-$TOTAL_BYTES}"
    task_is_current || return 0
    CURRENT_PROGRESS="$progress"
    DOWNLOADED_BYTES="$downloaded"
    TOTAL_BYTES="$total"
    python3 - "$STATUS.tmp" "$TASK_ID" "$TARGET_VERSION" "$state" "$progress" "$message" "$downloaded" "$total" "$REQUESTED_AT" <<'PY'
import json, sys
from datetime import datetime, timezone
path, task_id, version, state, progress, message, downloaded, total, requested_at = sys.argv[1:]
payload = {"taskId": task_id, "version": version, "status": state, "progress": int(progress),
           "message": message, "downloadedBytes": int(downloaded), "totalBytes": int(total),
           "requestedAt": requested_at,
           "updatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")}
with open(path, "w", encoding="utf-8") as output:
    json.dump(payload, output, ensure_ascii=False, separators=(",", ":"))
PY
    chown "$REQUEST_UID:$REQUEST_GID" "$STATUS.tmp"
    chmod 0644 "$STATUS.tmp"
    mv -f "$STATUS.tmp" "$STATUS"
}

wait_for_app() {
    local container_id state
    for _ in $(seq 1 90); do
        container_id="$(compose ps -q app 2>/dev/null || true)"
        if [[ -n "$container_id" ]]; then
            state="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id" 2>/dev/null || true)"
            [[ "$state" == "healthy" ]] && return 0
            [[ "$state" == "unhealthy" || "$state" == "exited" || "$state" == "dead" ]] && return 1
        fi
        sleep 2
    done
    return 1
}

wait_for_business_idle() {
    local count
    for _ in $(seq 1 120); do
        if ! count="$(compose exec -T mysql sh -c 'mysql -N -s -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "SELECT (SELECT COUNT(*) FROM xianyu_goods_order WHERE delivery_status = '\''PROCESSING'\'' OR confirm_task_status = '\''PROCESSING'\'') + (SELECT COUNT(*) FROM xianyu_order_automation_record WHERE rate_status = 6) + (SELECT COUNT(*) FROM xianyu_goods_auto_reply_record WHERE state = 2);"' | tr -d '[:space:]')"; then
            return 1
        fi
        [[ "$count" =~ ^[0-9]+$ ]] || return 1
        [[ "$count" == "0" ]] && return 0
        sleep 1
    done
    return 1
}
restore_previous_jar() {
    if [[ -f "$BACKUP_JAR" && ( "$HAD_ACTIVE_JAR" == true || -f "$INSTALL_MARKER" ) ]]; then
        install -m 0644 "$BACKUP_JAR" "$ACTIVE_JAR"
    else
        rm -f "$ACTIVE_JAR"
    fi
}

cleanup() {
    local exit_code=$?
    if [[ $exit_code -ne 0 && "$ROLLBACK_REQUIRED" == true ]]; then
        if restore_previous_jar && compose up -d --no-build --no-deps --force-recreate app && wait_for_app; then
            FAILURE_MESSAGE="$FAILURE_MESSAGE，已恢复更新前版本"
            rm -f "$INSTALL_MARKER"
        else
            FAILURE_MESSAGE="$FAILURE_MESSAGE；自动回滚未通过健康检查，请查看应用日志"
        fi
    fi
    rm -f "$MAINTENANCE_FLAG"
    if [[ $exit_code -ne 0 && -n "$TASK_ID" ]]; then
        status "FAILED" "$CURRENT_PROGRESS" "$FAILURE_MESSAGE" "$DOWNLOADED_BYTES" "$TOTAL_BYTES" || true
    fi
    rm -rf "$WORK_DIR"
    remove_current_request
    exit "$exit_code"
}
trap cleanup EXIT

download_jar() {
    local url="$1" target="$2" total="$3" attempt pid downloaded progress
    for attempt in $(seq 1 "$DOWNLOAD_ATTEMPTS"); do
        downloaded="$(stat -c %s "$target" 2>/dev/null || echo 0)"
        if [[ "$downloaded" -gt "$total" ]]; then
            rm -f "$target"
            downloaded=0
        elif [[ "$downloaded" -eq "$total" ]]; then
            status "DOWNLOADING" 70 "更新文件下载完成" "$downloaded" "$total"
            return 0
        fi
        status "DOWNLOADING" "$CURRENT_PROGRESS" "正在下载更新文件（第 $attempt/$DOWNLOAD_ATTEMPTS 次）" "$downloaded" "$total"
        curl -fsSL --connect-timeout 30 --max-time 1800 --continue-at - "$url" -o "$target" &
        pid=$!
        while kill -0 "$pid" 2>/dev/null; do
            downloaded="$(stat -c %s "$target" 2>/dev/null || echo 0)"
            progress=$((10 + downloaded * 60 / total))
            (( progress > 70 )) && progress=70
            status "DOWNLOADING" "$progress" "正在下载更新文件（第 $attempt/$DOWNLOAD_ATTEMPTS 次）" "$downloaded" "$total"
            sleep 2
        done
        wait "$pid" || true
        downloaded="$(stat -c %s "$target" 2>/dev/null || echo 0)"
        if [[ "$downloaded" -eq "$total" ]]; then
            status "DOWNLOADING" 70 "更新文件下载完成" "$downloaded" "$total"
            return 0
        fi
        sleep $((attempt * 3))
    done
    FAILURE_MESSAGE="更新文件下载失败，已重试 $DOWNLOAD_ATTEMPTS 次"
    return 1
}

for command in curl python3 docker flock sha256sum gzip; do command -v "$command" >/dev/null; done
[[ -f "$REQUEST" && -f "$ENV_FILE" && -f "$COMPOSE_FILE" ]]

mapfile -t REQUEST_META < <(python3 - "$REQUEST" <<'PY'
import json, sys
request = json.load(open(sys.argv[1], encoding="utf-8"))
print(request.get("taskId", "")); print(request.get("version", "")); print(request.get("requestedAt", ""))
PY
)
TASK_ID="${REQUEST_META[0]}"
TARGET_VERSION="${REQUEST_META[1]}"
REQUESTED_AT="${REQUEST_META[2]}"
[[ "$TASK_ID" =~ ^[0-9a-fA-F-]{36}$ && "$TARGET_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+([+-][A-Za-z0-9._-]+)?$ && -n "$REQUESTED_AT" ]]
REQUEST_UID="$(stat -c %u "$REQUEST")"
REQUEST_GID="$(stat -c %g "$REQUEST")"
[[ "$REQUEST_UID" =~ ^[0-9]+$ && "$REQUEST_GID" =~ ^[0-9]+$ ]]

exec 9>"$UPDATE_DIR/deploy.lock"
flock -w 180 9

if [[ -f "$INSTALL_MARKER" ]]; then
    FAILURE_MESSAGE="检测到上次更新被中断，恢复更新前版本失败"
    restore_previous_jar
    compose up -d --no-build --no-deps --force-recreate app
    wait_for_app
    rm -f "$INSTALL_MARKER"
fi

status "CHECKING" 3 "正在读取 GitHub 正式版本信息" 0 0
FAILURE_MESSAGE="读取 GitHub 正式版本信息失败"
curl -fsSL --retry 3 --retry-delay 2 --retry-all-errors --connect-timeout 15 --max-time 60 \
    -H 'Accept: application/vnd.github+json' -H 'User-Agent: XianYuPlus-Updater' "$RELEASE_API" -o "$WORK_DIR/release.json"

python3 - "$WORK_DIR/release.json" "$WORK_DIR/asset-meta" "$TARGET_VERSION" <<'PY'
import json, sys
release = json.load(open(sys.argv[1], encoding="utf-8"))
version = release.get("tag_name", "").lstrip("vV")
if version != sys.argv[3].lstrip("vV"): raise SystemExit("GitHub最新正式版本与请求版本不一致")
assets = release.get("assets") or []
jars = [a for a in assets if a.get("name", "").startswith("xianyu-plus-") and a.get("name", "").endswith(".jar")]
checksums = [a for a in assets if a.get("name") == "SHA256SUMS.txt"]
if len(jars) != 1 or len(checksums) != 1: raise SystemExit("正式版本缺少唯一JAR或SHA256SUMS.txt")
size = jars[0].get("size")
if not isinstance(size, int) or size <= 0: raise SystemExit("正式版本JAR大小无效")
with open(sys.argv[2], "w", encoding="utf-8") as output:
    output.write(jars[0]["browser_download_url"] + "\n" + jars[0]["name"] + "\n" + str(size) + "\n" + checksums[0]["browser_download_url"] + "\n")
PY

mapfile -t ASSET < "$WORK_DIR/asset-meta"
TOTAL_BYTES="${ASSET[2]}"
FAILURE_MESSAGE="更新文件下载失败，当前版本继续运行"
download_jar "${ASSET[0]}" "$WORK_DIR/app.jar" "$TOTAL_BYTES"
FAILURE_MESSAGE="校验文件下载失败"
curl -fsSL --retry 3 --retry-delay 2 --retry-all-errors --connect-timeout 15 --max-time 60 "${ASSET[3]}" -o "$WORK_DIR/SHA256SUMS.txt"
status "VERIFYING" 75 "正在校验更新文件完整性" "$TOTAL_BYTES" "$TOTAL_BYTES"
EXPECTED_SHA="$(awk -v name="${ASSET[1]}" '$2 == name || $2 == "*" name {print $1}' "$WORK_DIR/SHA256SUMS.txt")"
ACTUAL_SHA="$(sha256sum "$WORK_DIR/app.jar" | awk '{print $1}')"
FAILURE_MESSAGE="更新文件完整性校验失败"
[[ "$EXPECTED_SHA" =~ ^[0-9a-fA-F]{64}$ && "${EXPECTED_SHA,,}" == "$ACTUAL_SHA" ]]

status "DRAINING" 78 "更新文件已就绪，正在等待当前自动化任务安全结束" "$TOTAL_BYTES" "$TOTAL_BYTES"
touch "$MAINTENANCE_FLAG"
chmod 0644 "$MAINTENANCE_FLAG"
# 给各调度器一个完整轮询周期，使其停止领取新任务并暴露已有租约。
sleep 3
FAILURE_MESSAGE="等待当前发货、回复或评价任务结束超时"
wait_for_business_idle

status "INSTALLING" 80 "正在备份数据库与当前版本" "$TOTAL_BYTES" "$TOTAL_BYTES"
BACKUP_FILE="$UPDATE_DIR/database-$TASK_ID.sql.gz"
compose exec -T mysql sh -c 'mysqldump --single-transaction --quick -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' | gzip -1 > "$BACKUP_FILE"
chmod 0600 "$BACKUP_FILE"
mapfile -t OLD_BACKUPS < <(find "$UPDATE_DIR" -maxdepth 1 -type f -name 'database-*.sql.gz' -printf '%T@ %p\n' | sort -nr | tail -n +4 | cut -d' ' -f2-)
if [[ ${#OLD_BACKUPS[@]} -gt 0 ]]; then rm -f -- "${OLD_BACKUPS[@]}"; fi

if [[ -f "$ACTIVE_JAR" ]]; then
    install -m 0644 "$ACTIVE_JAR" "$BACKUP_JAR"
    HAD_ACTIVE_JAR=true
else
    rm -f "$BACKUP_JAR"
fi
printf '%s\n' "$TASK_ID" > "$INSTALL_MARKER.tmp"
mv -f "$INSTALL_MARKER.tmp" "$INSTALL_MARKER"
install -m 0644 "$WORK_DIR/app.jar" "$ACTIVE_JAR"
ROLLBACK_REQUIRED=true

status "RESTARTING" 88 "新版本已安装，正在重启应用容器" "$TOTAL_BYTES" "$TOTAL_BYTES"
FAILURE_MESSAGE="新版本容器启动失败"
compose up -d --no-build --no-deps --force-recreate app
status "HEALTH_CHECKING" 94 "应用已重启，正在执行健康检查" "$TOTAL_BYTES" "$TOTAL_BYTES"
FAILURE_MESSAGE="新版本健康检查失败"
wait_for_app

rm -f "$INSTALL_MARKER" "$MAINTENANCE_FLAG"
ROLLBACK_REQUIRED=false
status "SUCCESS" 100 "在线更新完成，自动化服务已恢复" "$TOTAL_BYTES" "$TOTAL_BYTES"
