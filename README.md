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

公共配置在 `application.yml`。生产环境敏感信息建议用环境变量覆盖，不要写进仓库。

## 快速开始

1. 建库并执行 DDL：`src/main/resources/db/schema.sql`
2. 本地改 `application-dev.yml` 中 MySQL / Redis；生产用环境变量或改 `application-prod.yml`
3. 启动：

```bash
# 本地开发（默认 dev）
mvn spring-boot:run

# 生产环境（云服务器）
java -jar target/lottery-server-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod
```

生产环境常用启动方式：

```bash
# 服务器上（已安装 JDK17、MySQL、Redis，并已执行 schema.sql）
mvn -DskipTests package
chmod +x scripts/*.sh
./scripts/start-prod.sh
# 停止: ./scripts/stop-prod.sh
```

Nginx Proxy Manager 转发目标填：`http://127.0.0.1:8080`（应用 context-path 为 `/api`）。

生产环境账号写在 `application-prod.yml`；也可用环境变量覆盖（见历史说明）。

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
