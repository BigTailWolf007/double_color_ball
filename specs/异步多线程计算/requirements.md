# 需求文档：异步多线程中奖计算

## 背景
当前同步彩票信息后，购买记录和预测记录的盈亏计算是同步单线程执行的。当某期彩票的购买/预测记录达到数万条甚至数十万条时，同步计算会导致接口响应严重延迟，影响用户体验。需要将计算任务改为异步多线程执行。

## 当前问题
- `PurchaseService.calc(issue)` 和 `PredictService.calc(issue)` 一次性加载该期所有待计算记录到内存
- 单线程逐条计算，记录数多时耗时严重（如 10 万条 × 0.1ms = 10 秒+）
- 同步执行导致 `/api/lottery/sync` 接口响应时间随记录数线性增长

## 目标
1. 同步开奖信息后，中奖计算任务改为异步提交，接口立即返回
2. 使用线程池并发处理，每线程处理 1000 条记录
3. 基于 ID 范围分片查询，避免一次性加载全部记录到内存
4. 支持购买记录和预测记录两种计算任务的并行处理

## 功能需求

### F1. 线程池配置
- 新建 `AsyncConfig` 配置类
- 使用 Spring `@EnableAsync` + 自定义 `ThreadPoolTaskExecutor`
- 核心线程数：CPU 核心数（最少 4）
- 最大线程数：CPU 核心数 × 2
- 队列容量：100
- 拒绝策略：`CallerRunsPolicy`（队列满时由调用线程执行）

### F2. 异步计算服务
- 新建 `AsyncCalcService`，注入 `PurchaseRecordMapper`、`PredictRecordMapper`、`LotteryService`
- 暴露两个异步方法：
  - `asyncCalcPurchase(String issue)`：分片计算购买记录
  - `asyncCalcPredict(String issue)`：分片计算预测记录
- 分片逻辑：
  1. 查询该期号待计算记录的最小 ID 和最大 ID
  2. 按 ID 范围每 1000 条切为一个分片：`[minId, minId+1000)`, `[minId+1000, minId+2000)`, ...
  3. 每个分片作为一个独立任务提交到线程池
  4. 每个分片任务在自己的事务中：SELECT → calcAndFill → batchUpdate

### F3. 分片计算任务
- 每个分片任务内部：
  1. 查询 `fissue = ? AND fid >= ? AND fid < ? AND fprize_level IS NULL`
  2. 逐条调用 `calcAndFill`（复用现有逻辑）
  3. 批量更新（复用现有 `batchUpdatePrize` / `batchUpdateHitResult`）
  4. 若发生异常：自动重试最多 3 次（间隔 1s / 2s / 4s 递增）
  5. 3 次重试均失败：记录 ERROR 日志（期号、ID 区间、异常信息），放弃本分片
- 使用 `@Transactional(propagation = Propagation.REQUIRES_NEW)` 保证每个分片独立事务

### F4. 接口改造
- `LotteryController.sync()` 中，同步完成后：
  - 触发异步计算：`asyncCalcService.asyncCalcPurchase(issue)` 和 `asyncCalcService.asyncCalcPredict(issue)`
  - 不再同步等待计算完成，接口立即返回
  - 返回结果中标注 `"calcMode": "async"`

### F5. 分片异常自愈机制
- **分片内重试**：每个分片失败后自动重试，最多 3 次，间隔递增（1s → 2s → 4s）
- **降级留痕（入库）**：3 次全失败后，将错误信息写入 `t_calc_error_log` 数据库表，包含期号、类型（购买/预测）、ID 区间、异常信息、状态
- **手动兜底**：失败分片的记录 `fprize_level` 仍为 NULL，后续可通过错误日志管理页勾选重新计算，或手动调用原同步接口补算
- **幂等安全**：重试/重新计算时 SELECT 仅查 NULL 记录，已成功的分片不会重复处理

### F6. 错误日志数据库表
- 新建 `t_calc_error_log` 表：
  - `fid` BIGINT 主键自增
  - `fissue` VARCHAR(20) 期号
  - `fcalc_type` VARCHAR(10) 计算类型（purchase / predict）
  - `fid_start` BIGINT 分片起始 ID
  - `fid_end` BIGINT 分片结束 ID（不包含）
  - `ferror_msg` TEXT 异常信息
  - `fstatus` TINYINT 状态（0=待处理, 1=已重试成功, 2=已忽略）
  - `fretry_count` INT 已重试次数
  - `fcreated_at` DATETIME 创建时间

### F7. 错误日志管理接口
- `GET /api/calc-error/list`：分页查询错误日志（支持按期号筛选）
- `POST /api/calc-error/retry`：接收 ID 数组，对选中的错误日志重新触发分片计算
  - 调用 `AsyncCalcService` 按原 ID 区间重新执行
  - 成功后更新状态为"已重试成功"

### F8. 前端错误日志管理页
- 新增导航菜单"错误日志"（子菜单 of 开奖号码 or 独立入口）
- 列表展示：期号、类型、ID区间、异常信息（截断+点击展开）、状态标签、创建时间
- 支持多选勾选 + "重新计算"按钮
- 重试成功后自动刷新列表

### F9. 日志与监控
- 每次异步计算启动时记录 INFO 日志：期号、总分片数
- 每个分片完成时记录 DEBUG/INFO 日志：分片序号、处理条数、耗时
- 全部完成时记录 INFO 日志：总处理条数、总耗时

### F10. 定时同步任务
- 双色球开奖日为每周 **二、四、日**，设置两个定时任务：
  - **开奖日晚 9:20**：第一时间拉取开奖号码（此时奖金可能未完全统计）
  - **开奖次日早 8:00**：二次拉取补全奖金数据
- 定时逻辑：
  1. 从数据库查询最新期号，期号+1 得到预期新期号
  2. 调用外部 API 查询该期号，若无数据则跳过（说明尚未开奖或 API 暂无数据）
  3. 有数据则调用 `LotteryService.sync()` 同步入库
  4. 同步成功后自动触发异步计算（复用 F4 异步链路）
- Cron 表达式：
  - 周二/四/日 21:20 → `0 20 21 ? * 3,5,1`
  - 周三/五/一 08:00 → `0 0 8 ? * 4,6,2`

### F11. 定时任务配置
- `DcbApplication.java` 加 `@EnableScheduling`
- 新建 `ScheduledSyncService.java`，包含两个 `@Scheduled` 方法
- 定时任务需幂等：同一期号已存在时，sync 内部自动更新而非重复插入

## 非功能需求
- 线程池资源可控，不会因大量并发请求导致线程爆炸
- 分片查询使用 ID 范围而非 OFFSET，避免深分页性能问题
- 每个分片独立事务，分片失败不影响其他分片
- 异步任务异常记录 ERROR 日志，不中断其他分片

## 涉及文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `AsyncConfig.java` | **新建** | 线程池配置 |
| `AsyncCalcService.java` | **新建** | 异步分片计算 + 重试 + 错误入库 |
| `ScheduledSyncService.java` | **新建** | 定时同步任务（二四日 21:20 + 三五日 08:00） |
| `CalcErrorLog.java` | **新建** | 错误日志 Entity |
| `CalcErrorLogMapper.java` + XML | **新建** | 错误日志 Mapper |
| `CalcErrorController.java` | **新建** | 错误日志管理接口 |
| `calc-error-log.js` | **新建** | 前端错误日志管理页 |
| `DcbApplication.java` | 修改 | 加 `@EnableAsync` + `@EnableScheduling` |
| `init.sql` | 修改 | 加 `t_calc_error_log` 建表 |
| `LotteryController.java` | 修改 | sync 改为调异步服务 |
| `index.html` | 修改 | 加导航菜单 + 引入新 JS |
| `router.js` | 修改 | 加路由映射 |

## 约束条件
- 后端：Java 8 + Spring Boot 2.7.18 + MyBatis-Plus 3.5.5
- 线程池使用 Spring 管理的 `ThreadPoolTaskExecutor`
- 分片大小固定 1000 条
- 复用现有 `calcAndFill` 计算逻辑，不做修改

## 验收标准
1. 启动后端，`@EnableAsync` 和线程池 Bean 正常加载
2. 调用 `/api/lottery/sync` 同步一期大量记录，接口 1 秒内返回
3. 查看日志确认分片任务被提交到线程池并行执行
4. 所有待计算记录最终被正确更新（中奖等级、奖金金额）
5. 模拟某个分片数据库异常，确认重试 3 次后错误记录写入 `t_calc_error_log` 表
6. 前端错误日志页可查看失败记录，勾选后点击"重新计算"可成功重试
7. 定时任务在二/四/日 21:20 自动触发同步，日志确认执行
8. 同一期号第二次定时同步时不会重复插入，只更新扩展字段
