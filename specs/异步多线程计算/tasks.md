# 任务列表：异步多线程中奖计算

## 任务概览
整体分 4 个阶段：基础配置 → 核心异步服务 → 定时任务 + 错误管理 → 前端页面。共 12 个任务，按依赖顺序执行。

## 任务步骤

### Task 1: 数据库 — 错误日志表
- 文件：`dcb-backend/src/main/resources/sql/init.sql`
- 操作：修改
- 内容：新增 `t_calc_error_log` 建表语句 + ALTER 脚本

### Task 2: 启动类 — 开启异步+定时
- 文件：`dcb-backend/src/main/java/com/dcb/DcbApplication.java`
- 操作：修改
- 内容：加 `@EnableAsync` 和 `@EnableScheduling` 注解

### Task 3: 线程池配置
- 文件：`dcb-backend/src/main/java/com/dcb/common/config/AsyncConfig.java`
- 操作：新建
- 内容：`@Configuration` + `@EnableAsync`，定义 `ThreadPoolTaskExecutor` Bean（核心=CPU核数最少4，最大=2×，队列=100，拒绝=CallerRunsPolicy）

### Task 4: 错误日志 Entity
- 文件：`dcb-backend/src/main/java/com/dcb/calcerror/entity/CalcErrorLog.java`
- 操作：新建
- 内容：对应 `t_calc_error_log` 表，字段：id, issue, calcType, idStart, idEnd, errorMsg, status(0/1/2), retryCount, createdAt

### Task 5: 错误日志 Mapper
- 文件：`dcb-backend/src/main/java/com/dcb/calcerror/mapper/CalcErrorLogMapper.java` + `CalcErrorLogMapper.xml`
- 操作：新建
- 内容：MyBatis-Plus BaseMapper + 分页查询 + 按 status 更新

### Task 6: 异步计算服务（核心）
- 文件：`dcb-backend/src/main/java/com/dcb/common/service/AsyncCalcService.java`
- 操作：新建
- 内容：
  - `asyncCalcPurchase(issue)`：查 minId/maxId → 切分片 → 提交线程池
  - `asyncCalcPredict(issue)`：同上
  - `executePurchaseShard(issue, idStart, idEnd)`：@Transactional(REQUIRES_NEW) → SELECT → calcAndFill → batchUpdate → 异常时重试3次 → 失败写 t_calc_error_log
  - `executePredictShard(...)`：同上
  - `retryErrorLog(Long errorLogId)`：按错误日志记录的 ID 区间重新执行分片

### Task 7: 定时同步服务
- 文件：`dcb-backend/src/main/java/com/dcb/common/service/ScheduledSyncService.java`
- 操作：新建
- 内容：
  - `@Scheduled(cron="0 20 21 ? * 3,5,1")` → syncLatestIssue()
  - `@Scheduled(cron="0 0 8 ? * 4,6,2")` → syncLatestIssue()
  - syncLatestIssue()：查 DB 最新期号+1 → 调外部 API → 有数据则 LotteryService.sync() → 触发 AsyncCalcService

### Task 8: 错误日志管理接口
- 文件：`dcb-backend/src/main/java/com/dcb/calcerror/controller/CalcErrorController.java`
- 操作：新建
- 内容：
  - `GET /api/calc-error/list`：分页查询 + issue 筛选
  - `POST /api/calc-error/retry`：按 ID 列表重新执行分片

### Task 9: Controller 改造
- 文件：`dcb-backend/src/main/java/com/dcb/lottery/controller/LotteryController.java`
- 操作：修改
- 内容：sync 方法中去掉同步 calc 调用，改为 `asyncCalcService.asyncCalcPurchase(issue)` + `asyncCalcService.asyncCalcPredict(issue)`，返回结果加 `calcMode: "async"`

### Task 10: 前端错误日志页
- 文件：`dcb-frontend/app/pages/calc-error-log.js`
- 操作：新建
- 内容：IIFE 模块，列表展示（期号/类型/ID区间/异常信息截断展开/状态标签/时间），多选+重新计算按钮

### Task 11: 前端路由+导航
- 文件：`dcb-frontend/app/router.js`、`dcb-frontend/app/index.html`
- 操作：修改
- 内容：添加 `#calc-error-log` 路由映射 + 导航菜单项 + 引入 JS

### Task 12: 全局验证
- 编译后端
- 检查前端文件完整性
- 更新 tasks.md 进度

## 验证步骤
1. 启动后端，确认 `@EnableAsync`、`@EnableScheduling` 和线程池 Bean 加载成功
2. 调 `POST /api/lottery/sync` 传入有大量记录的期号，确认接口 1s 内返回 `calcMode: "async"`
3. 观察日志确认分片任务并行执行，购买和预测记录被正确更新
4. 模拟分片异常，确认重试 3 次后错误写入 `t_calc_error_log`
5. 调 `GET /api/calc-error/list` 确认可查询错误日志
6. 调 `POST /api/calc-error/retry` 确认可重新执行
7. 前端错误日志页展示正常，勾选+重新计算功能正常
8. 调整系统时间验证定时任务 Cron 表达式正确触发
