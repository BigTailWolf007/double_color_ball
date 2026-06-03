# 需求文档：系统配置管理

## 背景
当前系统配置分散在多处，不利于运维管理：
- **硬编码常量**：API 地址/密钥、超时时间、分片大小、重试次数、推荐上限
- **配置文件**：数据库连接、日志级别、缓存策略、文件上传限制
- **注解硬编码**：定时任务 cron 表达式

业务人员修改配置需要懂代码或 SSH 登录服务器，效率极低且有误操作风险。

## 目标
设计一套系统配置管理方案，支持管理员在前端界面查看和修改系统配置，支持运行时热加载（无需重启），对历史操作有审计日志。

## 功能需求

### 1. 配置分类
将配置分为两个层级：

#### 基础设施配置（application.yml，不在前端管理）
Spring Boot 启动必需，无法热加载，修改需重启。

> ⚠️ **Git 安全方案**：数据库密码等敏感信息不在 `application.yml` 中明文写死，改为 Spring 占位符 `${ENV_VAR:默认值}`，生产环境通过系统环境变量注入，确保配置文件可安全提交 Git。
| 配置项 | 说明 |
|--------|------|
| `spring.datasource.*` | 数据库连接（url、账号、密码） |
| `server.port` | 服务端口 |
| `logging.level` | 日志级别 |

#### 业务配置（t_sys_config，前端可管理，热加载）
运行时动态读取，修改即时生效：

| 分组 | 配置项 | 值类型 | 说明 |
|------|--------|:---:|------|
| API | `lottery.api.url` | STRING | 彩票API地址 |
| API | `lottery.api.key` | STRING | API密钥 |
| API | `lottery.api.timeout` | INT | API超时(毫秒) |
| SYSTEM | `async.shard.size` | INT | 异步计算分片大小 |
| SYSTEM | `async.max.retries` | INT | 分片最大重试次数 |
| SYSTEM | `recommend.max.result` | INT | 推荐号码最大数量 |
| SCHEDULE | `scheduled.sync.night` | CRON | 开奖日晚间同步 |
| SCHEDULE | `scheduled.sync.morning` | CRON | 开奖次日早晨同步 |
| UPLOAD | `upload.max.file.size` | STRING | 文件上传大小限制 |
| CACHE | `cache.recommend.max.size` | INT | 推荐缓存最大条目 |
| CACHE | `cache.recommend.expire` | INT | 推荐缓存过期(秒) |

### 2. 数据存储
新增系统配置表 `t_sys_config`：

| 字段 | 类型 | 说明 |
|------|------|------|
| fid | BIGINT PK | 主键 |
| fconfig_key | VARCHAR(64) UNIQUE | 配置键（如 `lottery.api.url`） |
| fconfig_value | VARCHAR(512) | 配置值 |
| fconfig_desc | VARCHAR(128) | 配置说明 |
| fconfig_type | VARCHAR(16) | 值类型：STRING/INT/CRON |
| fconfig_group | VARCHAR(32) | 分组：SYSTEM/API/SCHEDULE/UPLOAD/CACHE |
| fsort_order | INT | 排序序号 |
| fupdated_at | DATETIME | 最后修改时间 |

### 3. 后端实现
- **ConfigService**：配置加载、缓存、热更新
  - 启动时从 DB 加载全部配置到内存 Map
  - 修改时同时更新 DB + 刷新内存缓存
  - 对定时任务类配置，修改后重新注册 `@Scheduled` 任务（使用 `TaskScheduler` 动态调度）
- **ConfigController**：配置查询和修改接口
  - GET `/api/config/list` — 按分组返回所有配置
  - PUT `/api/config` — 修改单个配置
  - POST `/api/config/refresh` — 手动刷新缓存
- **各业务类改造**：原来硬编码的常量改为从 ConfigService 读取

### 4. 前端页面
新增"系统配置"Tab 页：
- 按分组卡片展示配置项
- 配置值支持文本输入编辑
- 修改后即时保存，显示操作结果
- 简单直观，管理员友好

### 5. 默认值初始化
`init.sql` 中预置初始配置数据，确保系统启动即有默认值。

## 非功能需求
- 配置修改即时生效，无需重启服务
- 内存缓存减少 DB 查询
- 非法值校验（如 cron 格式、整数范围）

## 约束条件
- 当前技术栈：Java 8 + Spring Boot + MyBatis Plus + HTML/JS
- 不使用 Spring Cloud Config 等重量级方案
- 数据库表遵循项目 `f` 前缀命名规范

## 补充：Git 安全策略

### 问题
`application.yml` 含数据库密码，提交 Git 后生产环境不安全。

### 方案：环境变量注入

**application.yml 改造**（可安全提交 Git）：
```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/double_color_ball?...}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:dev123}
```

**生产部署时注入**：
```bash
# 方式1：系统环境变量
export DB_PASSWORD=生产真实密码
java -jar dcb-backend.jar

# 方式2：启动参数
java -jar dcb-backend.jar --spring.datasource.password=生产真实密码
```

- 占位符 `:dev123` 只给本地开发用，生产环境变量会覆盖
- Git 中永远只有开发默认值，生产密码不落盘

## 验收标准
1. 前端可查看所有配置项，按分组展示
2. 修改 API 密钥后，下次同步调用使用新密钥
3. 修改定时任务 cron 后，定时任务按新时间执行
4. 修改分片大小后，异步计算使用新分片大小
5. 配置修改即时生效，无需重启
