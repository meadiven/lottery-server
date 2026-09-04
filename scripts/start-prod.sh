#!/usr/bin/env bash
# 生产启动脚本：在云服务器项目目录或 jar 所在目录执行
# 启动前请 export MYSQL_PASSWORD / SPRING_DATA_REDIS_PASSWORD 等变量
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAR="${APP_DIR}/target/lottery-server-1.0.0-SNAPSHOT.jar"
PID_FILE="${APP_DIR}/lottery-server.pid"
LOG_DIR="${APP_DIR}/logs"

mkdir -p "${LOG_DIR}"

if [[ ! -f "${JAR}" ]]; then
  echo "未找到 ${JAR}，请先执行: mvn -DskipTests package"
  exit 1
fi

if [[ -f "${PID_FILE}" ]] && kill -0 "$(cat "${PID_FILE}")" 2>/dev/null; then
  echo "服务已在运行, pid=$(cat "${PID_FILE}")"
  exit 0
fi

: "${MYSQL_PASSWORD:?请先 export MYSQL_PASSWORD}"
: "${SPRING_DATA_REDIS_PASSWORD:?请先 export SPRING_DATA_REDIS_PASSWORD}"

export SPRING_DATA_REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
export SPRING_DATA_REDIS_PORT="${REDIS_PORT:-6379}"
export MYSQL_HOST="${MYSQL_HOST:-gz-cdb-lns1pwrz.sql.tencentcdb.com}"
export MYSQL_PORT="${MYSQL_PORT:-28293}"
export MYSQL_DB="${MYSQL_DB:-lottery_db}"
export MYSQL_USER="${MYSQL_USER:-root}"

nohup java -jar "${JAR}" --spring.profiles.active=prod \
  > "${LOG_DIR}/stdout.log" 2>&1 &
echo $! > "${PID_FILE}"
echo "已启动 production, pid=$(cat "${PID_FILE}")"
echo "健康检查: curl -s http://127.0.0.1:8080/api/ 或查看 ${LOG_DIR}/stdout.log"
