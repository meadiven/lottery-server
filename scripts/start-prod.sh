#!/usr/bin/env bash
# 生产启动脚本
# 推荐：先 source /etc/lottery-server.env 或 export 必要变量后再执行
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${LOTTERY_ENV_FILE:-/etc/lottery-server.env}"
JAR="${APP_DIR}/target/lottery-server-1.0.0-SNAPSHOT.jar"
PID_FILE="${APP_DIR}/lottery-server.pid"
LOG_DIR="${APP_DIR}/logs"

# 若存在环境文件则加载（不提交到 git）
# 兼容 Windows 上传带来的 CRLF：先去 \r 再 source
if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  set -a
  # shellcheck disable=SC1091
  source <(sed 's/\r$//' "${ENV_FILE}")
  set +a
  echo "已加载环境变量: ${ENV_FILE}"
fi

mkdir -p "${LOG_DIR}"

if [[ ! -f "${JAR}" ]]; then
  echo "未找到 ${JAR}，请先执行: mvn -DskipTests package"
  exit 1
fi

if [[ -f "${PID_FILE}" ]] && kill -0 "$(cat "${PID_FILE}")" 2>/dev/null; then
  echo "服务已在运行, pid=$(cat "${PID_FILE}")"
  exit 0
fi

: "${MYSQL_HOST:?请设置 MYSQL_HOST}"
: "${MYSQL_PORT:?请设置 MYSQL_PORT}"
: "${MYSQL_DB:?请设置 MYSQL_DB}"
: "${MYSQL_USER:?请设置 MYSQL_USER}"
: "${MYSQL_PASSWORD:?请设置 MYSQL_PASSWORD}"
: "${SPRING_DATA_REDIS_PASSWORD:?请设置 SPRING_DATA_REDIS_PASSWORD}"

export REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
export REDIS_PORT="${REDIS_PORT:-6379}"
export REDIS_DB="${REDIS_DB:-0}"

nohup java -jar "${JAR}" --spring.profiles.active=prod \
  > "${LOG_DIR}/stdout.log" 2>&1 &
echo $! > "${PID_FILE}"
echo "已启动 production, pid=$(cat "${PID_FILE}")"
echo "健康检查: curl -s http://127.0.0.1:8080/api/ 或查看 ${LOG_DIR}/stdout.log"
