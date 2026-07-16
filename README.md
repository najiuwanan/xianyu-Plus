# XianYuSmart（闲鱼 Plus）

面向个人卖家的闲鱼虚拟商品管理与自动化助手。项目支持私有部署，可在飞牛 OS、Linux 服务器或安装了 Docker 的电脑上运行。

> 本项目与闲鱼平台无官方关联。请仅在遵守法律法规、平台规则及账号使用规范的前提下使用。

## 功能概览

- 多闲鱼账号管理、登录与连接状态查看
- 商品与 SKU 同步、自动发货开关和发货规则配置
- 文本、图片、卡密等虚拟商品自动发货
- 卡密库存管理、低库存提醒与发货记录查询
- 自动确认发货、自动发送求小红花话术
- 自动评价：按账号配置定时扫描待评价订单
- 关键词、商品规则与 AI 自动回复
- 订单、消息、异常待办、操作日志和数据备份

## 快速安装

### 飞牛 OS / Linux

在飞牛 OS 的终端或 Linux 终端执行：

```bash
git clone https://github.com/najiuwanan/xianyu-Plus.git && cd xianyu-Plus && chmod +x install.sh && ./install.sh
```

安装脚本会创建 `.env` 文件、生成随机运行密码并构建启动服务。

完成后访问：

```text
http://你的设备IP:12400
```

首次打开会进入管理员账号创建页面。

### 更新到最新版

在已安装项目的目录中执行：

```bash
cd xianyu-Plus && chmod +x update.sh && ./update.sh
```

该命令会拉取 GitHub 上的最新代码、重新构建并重启服务。更新前建议先完成数据备份。

## 使用前准备

1. 登录后台并创建管理员账号。
2. 添加闲鱼账号，完成登录并确认账号连接正常。
3. 同步需要管理的商品和 SKU。
4. 为商品配置自动发货内容、图片或卡密，并开启自动发货。
5. 如需使用自动求小红花或自动评价，请在对应账号设置中开启并填写话术。

建议先用一个测试商品完成一次小额订单验证，确认登录状态、消息发送和发货内容均符合预期后，再用于日常商品。

## 自动化说明

### 自动发货

收到订单后，系统会根据商品和 SKU 配置发送文本、图片或卡密。自动发货失败、库存不足或发送结果无法确认时，会保留记录并进入待处理状态，便于人工核对。

### 自动求小红花

当自动发货成功后，若账号已开启“自动求小红花”并设置了话术，系统会延迟发送对应消息。

### 自动评价

开启“自动评价”的账号会定时扫描待评价订单并提交预设评价内容。实际可用性取决于账号登录状态及平台接口状态。

## 部署要求

- Docker Engine 24+ 或 Docker Desktop
- Docker Compose v2
- 建议至少 2 核 CPU、4 GB 内存
- 默认使用 MySQL 8.4 容器，无需额外安装数据库

飞牛 OS 通常可通过 Docker 或 Compose 应用运行本项目。请确保 12400 端口未被其他服务占用。

## 常用运维命令

查看服务状态：

```bash
docker compose ps
```

查看应用日志：

```bash
docker compose logs -f --tail=200 app
```

查看数据库日志：

```bash
docker compose logs -f --tail=200 mysql
```

停止服务：

```bash
docker compose down
```

数据保存在 Docker 数据卷中；停止服务不会删除数据。如需彻底删除数据，请先自行确认并备份。

## 配置文件

首次安装会自动创建 `.env`。常用配置包括：

| 配置项 | 说明 |
| --- | --- |
| `DB_PASSWORD` | 应用数据库密码 |
| `DB_ROOT_PASSWORD` | MySQL 管理员密码 |
| `JWT_SECRET` | 登录会话密钥 |
| `ALLOWED_ORIGINS` | 允许访问的前端来源 |
| `TZ` | 时区，默认 `Asia/Shanghai` |
| `RED_FLOWER_INTERVAL_MS` | 求小红花扫描间隔，默认 5 分钟 |

日常使用不需要修改这些值。若手动调整 `.env`，修改后执行 `docker compose up -d --build` 使其生效。

## 本地开发

开发环境需要 Java 21、Node.js 20+ 和 MySQL 8.0+。

前端：

```bash
cd vue-code
npm install
npm run dev
```

后端：

```bash
./mvnw spring-boot:run
```

Windows 环境请使用 `mvnw.cmd`。

## 重要说明

- 闲鱼的登录、接口和平台规则可能调整，自动化功能需要关注账号状态和异常待办。
- 涉及真实订单、卡密或资金时，请保持库存、发货记录和异常任务的日常核对。
- 不要将 `.env`、Cookie、Token、密码或卡密提交到 GitHub 或公开分享。
- 更新、迁移或重装前，请先备份业务数据。

## 许可证

本项目采用 [PolyForm Noncommercial License 1.0.0](LICENSE)，仅限非商业用途。完整使用限制与免责声明见 [DISCLAIMER.md](DISCLAIMER.md)。
