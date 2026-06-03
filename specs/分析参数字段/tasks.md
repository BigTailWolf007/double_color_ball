# 任务列表：新增分析参数字段

## 任务概览
为三张核心表（开奖号码、购买记录、预测号码）新增和值、区间比、奇偶比、跨度四个分析参数。整体思路：先改数据库 Schema → 改 Entity → 改工具类（计算方法）→ 改各业务写入点调用计算 → 改 Mapper XML → 改 VO 供前端展示 → 改前端页面 → 历史数据补算。

## 任务步骤

### Task 1: 数据库新增字段
- 文件：SQL 脚本（提供 DDL）
- 操作：新增
- 内容：为 `t_lottery_result`、`t_purchase_record`、`t_predict_record` 三张表各添加4列：
  - `fsum_val` INT
  - `fzone_ratio` VARCHAR(16)
  - `fodd_even_ratio` VARCHAR(8)
  - `frange_val` INT

### Task 2: LotteryUtils 新增计算方法
- 文件：`dcb-backend/src/main/java/com/dcb/common/util/LotteryUtils.java`
- 操作：修改
- 内容：新增两个静态方法：
  - `calcSum(int... reds)` — 计算和值
  - `calcZoneRatio(int... reds)` — 计算区间比（低1-11:中12-22:高23-33）
  - `calcOddEvenRatio(int... reds)` — 计算奇偶比
  - `calcRange(int... reds)` — 计算跨度（max-min）
  或者合并为一个方法返回包含4个值的结果对象

### Task 3: 三个 Entity 新增字段
- 文件：
  - `dcb-backend/src/main/java/com/dcb/lottery/entity/LotteryResult.java`
  - `dcb-backend/src/main/java/com/dcb/purchase/entity/PurchaseRecord.java`
  - `dcb-backend/src/main/java/com/dcb/predict/entity/PredictRecord.java`
- 操作：修改
- 内容：各新增4个 `@TableField` 字段：
  - `sumVal` (Integer)
  - `zoneRatio` (String)
  - `oddEvenRatio` (String)
  - `rangeVal` (Integer)

### Task 4: 三个 VO 新增字段
- 文件：
  - `dcb-backend/src/main/java/com/dcb/lottery/vo/LotteryResultVO.java`
  - `dcb-backend/src/main/java/com/dcb/purchase/vo/PurchaseRecordVO.java`
  - `dcb-backend/src/main/java/com/dcb/predict/vo/PredictRecordVO.java`
- 操作：修改
- 内容：各新增4个字段（sumVal, zoneRatio, oddEvenRatio, rangeVal）

### Task 5: 业务写入点填充新字段
- 文件：
  - `dcb-backend/src/main/java/com/dcb/lottery/service/LotteryService.java`
  - `dcb-backend/src/main/java/com/dcb/purchase/service/PurchaseService.java`
  - `dcb-backend/src/main/java/com/dcb/predict/service/PredictService.java`
- 操作：修改
- 内容：在以下5个写入位置调用工具方法填充新字段：
  1. `LotteryService.add()` — 手动录入
  2. `LotteryService.sync()` — API同步（新增记录分支）
  3. `LotteryService.parseLine()` — TXT导入
  4. `PurchaseService.add()` — 批量录入
  5. `PredictService.save()` — 保存预测

### Task 6: Mapper XML 更新
- 文件：
  - `dcb-backend/src/main/resources/mapper/LotteryResultMapper.xml`
  - `dcb-backend/src/main/resources/mapper/PurchaseRecordMapper.xml`
  - `dcb-backend/src/main/resources/mapper/PredictRecordMapper.xml`
- 操作：修改
- 内容：
  - LotteryResultMapper: resultMap 加4列映射，3个 SELECT 和 selectByIssues 补充字段
  - PurchaseRecordMapper: resultMap 加4列映射，selectPageByCondition 补充字段
  - PredictRecordMapper: resultMap 加4列映射，selectPageByIssue、selectByIssues 补充字段，batchInsert 加4列

### Task 7: Service 层 VO 转换补充
- 文件：
  - `LotteryService.toVO()`
  - `PurchaseService.toVO()`
  - `PredictService.toVO()`
- 操作：修改
- 内容：toVO 方法中补充4个新字段的赋值

### Task 8: 前端三页面新增显示列
- 文件：
  - `dcb-frontend/app/pages/lottery-list.js`
  - `dcb-frontend/app/pages/purchase-list.js`
  - `dcb-frontend/app/pages/predict-list.js`
- 操作：修改
- 内容：表格表头和行渲染中新增"和值/区间比/奇偶比/跨度"列

### Task 9: 历史数据补算 SQL
- 文件：`specs/分析参数字段/backfill.sql`
- 操作：新增
- 内容：提供 UPDATE 语句，对三张表中已有记录的4个新字段进行批量计算填充

## 验证步骤
1. 执行 DDL，确认三张表新增4列
2. 启动后端，手动录入一条开奖号码，查询数据库确认4个新字段有值
3. 录入一条购买记录，确认4个新字段有值
4. 保存预测号码，确认4个新字段有值
5. 前端三个列表页面确认能展示新字段
6. 执行历史数据补算 SQL，确认旧记录被正确填充
