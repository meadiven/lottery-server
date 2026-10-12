# lottery-server

官方公开开奖数据采集后台服务（Spring Boot 3）。

## 合规声明

本系统**仅采集**中国福彩、体彩官方对外公开的开奖公示数据，用于个人数据学习与统计研究。

- 不提供购彩、投注、荐号等任何相关功能
- 采集请求带限流、重试与间隔控制，禁止高频请求数据源
- 统计分析接口仅输出描述性统计，不做号码推荐

## 技术栈

Spring Boot 3.2 + JDK 17 + Maven + MyBatis-Plus + MySQL 8 + Redis

## 环境配置

| Profile | 文件 | 用途 |
|---------|------|------|
| `dev`（默认） | `application-dev.yml` | 本地开发 |
| `prod` | `application-prod.yml` | 云服务器生产环境 |

公共配置在 `application.yml`。**生产密码只放服务器环境变量文件**，不要写进仓库。

## 快速开始（本地）

1. 建库并执行 DDL：`src/main/resources/db/schema.sql`
2. 改 `application-dev.yml` 中 MySQL / Redis
3. 启动：`mvn spring-boot:run`

## 生产发布操作（详细）

### 一、本机打包

```bash
cd lottery-server
mvn -DskipTests package
```

产物：`target/lottery-server-1.0.0-SNAPSHOT.jar`

### 二、服务器准备（首次）

1. 已安装 JDK 17、本机 Redis；MySQL 可用腾讯云 CDB  
2. CDB 白名单加入**云服务器公网 IP**  
3. 建库/迁移（按需）：
   - 全量：`src/main/resources/db/schema.sql`
   - 增量：`migration_simulation.sql`、`migration_simulation_eval.sql`
4. 创建环境变量文件（**不要提交 git**）：

```bash
sudo cp scripts/lottery-server.env.example /etc/lottery-server.env
sudo chmod 600 /etc/lottery-server.env
sudo nano /etc/lottery-server.env
```

填入真实值（示例）：

```bash
MYSQL_HOST=gz-cdb-lns1pwrz.sql.tencentcdb.com
MYSQL_PORT=28293
MYSQL_DB=lottery_db
MYSQL_USER=root
MYSQL_PASSWORD=你的MySQL密码
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_DB=0
SPRING_DATA_REDIS_PASSWORD=你的Redis密码
```

5. 授权脚本：`chmod +x scripts/*.sh`

### 三、上传与启动

上传到服务器项目目录（至少包含）：

- `target/lottery-server-1.0.0-SNAPSHOT.jar`
- `scripts/start-prod.sh`、`scripts/stop-prod.sh`

```bash
# 可选：自定义 env 路径
# export LOTTERY_ENV_FILE=/etc/lottery-server.env

./scripts/stop-prod.sh
./scripts/start-prod.sh
```

`start-prod.sh` 会自动 `source /etc/lottery-server.env`。

### 四、验收

```bash
# 进程
cat lottery-server.pid && ps -p $(cat lottery-server.pid)

# 本机接口
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8080/api/

# 日志
tail -f logs/stdout.log
```

模拟相关（登录拿 token 后）：

```text
GET  /api/analysis/DLT/simulations/latest
GET  /api/analysis/DLT/simulations/evaluate/history?limit=20
GET  /api/analysis/DLT/simulations/select
POST /api/analysis/DLT/simulations/generate
```

### 五、反向代理（可选）

Nginx Proxy Manager 转发到：`http://127.0.0.1:8080`（context-path 为 `/api`）。  
小程序 `BASE_URL` 改为 `https://你的域名/api` 或 `http://公网IP:8080/api`。

### 六、停止

```bash
./scripts/stop-prod.sh
```

## 主要接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/lottery/types` | 彩种列表 |
| GET | `/api/lottery/draws?lotteryCode=SSQ&pageNum=1&pageSize=10` | 按彩种分页 |
| GET | `/api/lottery/draws/{code}/{issueNo}` | 按期号查单期 |
| GET | `/api/lottery/draws/{code}/latest` | 最新一期 |
| POST | `/api/lottery/collect/trigger` | 手动触发采集 |
| GET | `/api/analysis/{code}/frequency?recentIssues=50` | 频次统计（扩展） |

## 采集配置

```yaml
lottery:
  collect:
    enabled: true
    cron: "0 35 21 * * ?"
    request-interval-ms: 3000
    max-retry: 3
```

## 工程结构

见包路径 `com.lottery`：`controller` / `service` / `mapper` / `entity` / `dto` / `collector` / `parser` / `schedule` / `analysis`。
