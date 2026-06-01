# 任务列表：彩票接口同步

## 任务概览
整体分为后端（数据库 → Entity → VO → Service → Controller）和前端（按钮 + 弹窗 + 列）两大块，共 8 个任务，按依赖顺序执行。

## 任务步骤

### Task 1: 数据库 DDL — 新增字段
- 文件：`dcb-backend/src/main/resources/sql/init.sql`
- 操作：修改
- 内容：在 `t_lottery_result` 表定义中新增 5 个字段，并在文件末尾追加存量表 ALTER 脚本
  ```sql
  fprize_json   TEXT COMMENT '奖品分配JSON',
  fprize_text   TEXT COMMENT '奖品分配可读文本',
  fdeadline     DATE COMMENT '最后领奖日期',
  fsale_amount  DECIMAL(16,2) COMMENT '销售金额',
  fpool_amount  DECIMAL(16,2) COMMENT '奖池金额'
  ```

### Task 2: Entity 实体扩展
- 文件：`dcb-backend/src/main/java/com/dcb/lottery/entity/LotteryResult.java`
- 操作：修改
- 内容：新增 5 个属性，使用 `@TableField` 映射数据库字段

### Task 3: Mapper XML 更新
- 文件：`dcb-backend/src/main/resources/mapper/LotteryResultMapper.xml`
- 操作：修改
- 内容：`resultMap` 和所有 SELECT 语句中新增 5 个字段的列映射

### Task 4: SyncDTO 新建
- 文件：`dcb-backend/src/main/java/com/dcb/lottery/dto/LotterySyncDTO.java`
- 操作：新建
- 内容：包含 `@NotBlank issue` 字段的请求 DTO

### Task 5: VO 展示对象扩展
- 文件：`dcb-backend/src/main/java/com/dcb/lottery/vo/LotteryResultVO.java`
- 操作：修改
- 内容：新增 `prizeText`、`deadline`、`saleAmount`、`poolAmount` 4 个展示字段

### Task 6: Service 同步逻辑
- 文件：`dcb-backend/src/main/java/com/dcb/lottery/service/LotteryService.java`
- 操作：修改
- 内容：
  - 注入 `RestTemplate`（或新建 RestTemplate Bean）
  - 新增 `sync(issue)` 方法：
    1. 调用外部 API 获取 JSON
    2. 校验返回码 code=1
    3. 解析 `number` → 6红球、`refernumber` → 蓝球
    4. 解析 `prize` 数组生成 `fprize_json`（JSON序列化）和 `fprize_text`（可读文本）
    5. 查数据库是否已有该期号
    6. 不存在 → 构建完整 LotteryResult 插入
    7. 已存在 → 更新扩展字段（fprize_json、fprize_text、fdeadline、fsale_amount、fpool_amount），如 fdraw_date 为空则补填
    8. 触发购买记录盈亏计算
    9. 返回同步结果
  - 更新 `toVO()` 方法，映射新字段

### Task 7: Controller 新增同步端点
- 文件：`dcb-backend/src/main/java/com/dcb/lottery/controller/LotteryController.java`
- 操作：修改
- 内容：新增 `POST /api/lottery/sync` 接口，接收 `@Validated @RequestBody LotterySyncDTO`，调用 Service 同步方法

### Task 8: 前端 — 同步按钮 + 弹窗 + 列表列扩展
- 文件：`dcb-frontend/app/pages/lottery-list.js`
- 操作：修改
- 内容：
  - 卡片头部新增"同步开奖信息"按钮
  - 点击打开弹窗：期号输入框（默认最新期号+1，带下拉提示）+ 确认/取消
  - 确认后调用 `POST /api/lottery/sync`，显示 loading → 成功刷新列表
  - 列表表格新增列：奖金详情（展示 `prizeText`）、销售金额（千分位格式化）、奖池金额（千分位格式化）

## 验证步骤
1. 执行 `ALTER TABLE` 脚本，确认 5 个新字段添加成功
2. 启动后端，调用 `POST /api/lottery/sync` 传入 `{"issue":"2026061"}`，确认返回成功
3. 重复调用同一期号，确认不重复插入、只更新扩展字段
4. 打开前端开奖号码列表页，点击"同步开奖信息"，输入期号确认，列表刷新显示扩展数据
5. 检查购买记录列表，该期号的购买记录中奖等级已自动计算
