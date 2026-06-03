# 任务列表：系统配置管理

## 任务概览
建配置表 → 写 ConfigService（内存缓存+热加载）→ 业务类去硬编码 → 定时任务改为 TaskScheduler 动态调度 → 前端管理页 → application.yml 安全改造。

## 任务步骤

### Task 1: 数据库新增配置表 + 初始化数据
- 文件：`dcb-backend/src/main/resources/sql/init.sql`
- 操作：修改（新增 CREATE TABLE）+ 新增 INSERT 初始化数据
- 内容：
  - 新建 `t_sys_config` 表（fid, fconfig_key, fconfig_value, fconfig_desc, fconfig_type, fconfig_group, fsort_order, fupdated_at）
  - 预置所有默认配置值

### Task 2: 新建 ConfigService（配置加载/缓存/热更新）
- 文件：`dcb-backend/src/main/java/com/dcb/common/service/ConfigService.java`
- 操作：新增
- 内容：
  - `@PostConstruct` 启动时从 DB 加载全部配置到 `ConcurrentHashMap`
  - `getInt(key)` / `getString(key)` / `getCron(key)` 读取方法
  - `update(key, value)` 写 DB 并刷新缓存
  - `getAllByGroup()` 按分组返回
  - 值校验（CRON 格式、整数范围）

### Task 3: 新建 ConfigController（配置 API）
- 文件：`dcb-backend/src/main/java/com/dcb/common/controller/ConfigController.java`
- 操作：新增
- 内容：
  - `GET /api/config/list` — 按分组返回全部配置
  - `PUT /api/config` — 修改单个配置（含校验）
  - `POST /api/config/refresh` — 手动刷新缓存

### Task 4: 业务类去硬编码，改为读取 ConfigService
- 文件：
  - `LotteryService.java` — API_URL、API_KEY、API_TIMEOUT → ConfigService
  - `AsyncCalcService.java` — SHARD_SIZE、MAX_RETRIES → ConfigService
  - `RecommendCacheService.java` — MAX_RESULT → ConfigService
- 操作：修改
- 内容：去掉 `private static final` 常量，注入 ConfigService 动态读取

### Task 5: 定时任务改为 TaskScheduler 动态调度
- 文件：
  - `ScheduledSyncService.java` — 去掉 `@Scheduled` 硬编码，改为 `TaskScheduler.schedule()` 动态注册
  - `dcb-backend/src/main/java/com/dcb/common/config/ScheduleConfig.java` — 新增调度器配置
- 操作：修改 + 新增
- 内容：
  - 从 ConfigService 读取 cron 表达式
  - 配置修改后重新调度（cancel 旧任务 + schedule 新任务）
  - 首次启动时按 DB 配置的 cron 注册

### Task 6: 文件上传限制改为动态读取
- 文件：`dcb-backend/src/main/java/com/dcb/common/config/WebConfig.java` 或新增
- 操作：修改/新增
- 内容：Servlet multipart 限制改为从 ConfigService 读取

### Task 7: 推荐缓存改为动态配置
- 文件：`dcb-backend/src/main/java/com/dcb/common/config/CacheConfig.java`
- 操作：修改
- 内容：Caffeine 缓存的 maximumSize 和 expireAfterAccess 改为从 ConfigService 读取

### Task 8: 前端新增"系统配置"页面
- 文件：`dcb-frontend/app/pages/sys-config.js` — 新增
- 文件：`dcb-frontend/app/router.js` — 修改（注册新 Tab）
- 操作：新增 + 修改
- 内容：
  - 按分组卡片展示所有配置项
  - 文本输入框编辑配置值
  - 点击保存调用 API，显示成功/失败反馈
  - 值类型校验提示（如 cron 格式、数字范围）

### Task 9: application.yml 安全改造
- 文件：`dcb-backend/src/main/resources/application.yml`
- 操作：修改
- 内容：数据库密码等敏感信息改为 Spring 占位符 `${ENV:默认值}`

### Task 10: 存量库迁移脚本 + 说明文档
- 文件：`specs/系统配置管理/migration.sql` — 新增
- 操作：新增
- 内容：CREATE TABLE + INSERT 初始化数据（供存量库执行）

## 验证步骤
1. 启动系统，前端"系统配置"Tab 显示所有配置项
2. 修改分片大小为 500，验证异步计算使用新值
3. 修改晚间同步 cron，验证定时任务按新时间调度
4. 修改 API 密钥，验证同步调用使用新密钥
5. 检查 `application.yml`，确认密码为占位符格式
6. 重启后配置不丢失（从 DB 重新加载）
