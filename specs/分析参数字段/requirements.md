# 需求文档：新增分析参数字段

## 背景
当前系统的三张核心表（开奖号码 `t_lottery_result`、购买记录 `t_purchase_record`、预测号码 `t_predict_record`）仅存储了红球6个和蓝球1个的原始号码值，缺少衍生分析参数。用户在做数据分析时（如筛选符合某种特征的号码组合），需要手动计算和值、区间比、奇偶比、跨度等参数，效率低下且不便于 SQL 层面的聚合统计。

## 目标
为三张表均新增4个分析参数字段，在数据写入时自动计算并持久化，在查询时随 VO 返回给前端展示，方便后续数据分析。

## 功能需求

### 1. 新增数据库字段
在以下三张表中新增4个字段：

| 字段名 | 类型 | 说明 | 取值范围 |
|--------|------|------|----------|
| `fsum_val` | INT | 红球和值（6个红球之和） | 21 ~ 183 |
| `fzone_ratio` | VARCHAR(16) | 区间比（低区1-11:中区12-22:高区23-33） | 如 `2:2:2` |
| `fodd_even_ratio` | VARCHAR(8) | 奇偶比（奇数个数:偶数个数） | 如 `3:3` |
| `frange_val` | INT | 跨度（红球最大值 - 红球最小值） | 5 ~ 32 |

### 2. 后端代码修改

#### 2.1 Entity 层
- `LotteryResult.java` - 新增4个字段
- `PurchaseRecord.java` - 新增4个字段
- `PredictRecord.java` - 新增4个字段

#### 2.2 工具类
- `LotteryUtils.java` - 新增静态方法，传入6个红球，计算并返回和值、区间比、奇偶比、跨度

#### 2.3 业务层
所有创建/插入记录的入口，在构造 Entity 时调用工具类方法填充4个新字段：
- `LotteryService.add()` - 手动录入
- `LotteryService.sync()` - API同步（新增记录时）
- `LotteryService.parseLine()` - TXT导入
- `PurchaseService.add()` - 批量录入购买记录
- `PredictService.save()` - 保存预测记录

#### 2.4 VO 层
- `LotteryResultVO` - 新增4个字段（和值、区间比、奇偶比、跨度）
- `PurchaseRecordVO` - 新增4个字段
- `PredictRecordVO` - 新增4个字段

#### 2.5 Mapper XML
- `LotteryResultMapper.xml` - resultMap 新增4列映射，所有 SELECT 语句补充4个字段
- `PurchaseRecordMapper.xml` - resultMap 新增4列映射，所有 SELECT 语句补充4个字段
- `PredictRecordMapper.xml` - resultMap + batchInsert 新增4个字段

### 3. 前端展示
在三个列表页面中新增显示列：
- **开奖号码列表** (`lottery-list.js`)：表格新增"和值/区间比/奇偶比/跨度"列
- **购买记录列表** (`purchase-list.js`)：表格新增"和值/区间比/奇偶比/跨度"列
- **预测号码列表** (`predict-list.js`)：表格新增"和值/区间比/奇偶比/跨度"列

## 非功能需求
- 新增字段在写入时自动计算，无需用户手动输入
- 对已有历史数据：需提供 SQL 脚本批量补算
- 不影响现有接口的兼容性（DTO 入参不变，仅 VO 出参扩展）

## 约束条件
- 技术栈：Java 8 + Spring Boot + MyBatis Plus + Vue（微信小程序前端）
- 数据库：MySQL，字段命名遵循项目已有的 `f` 前缀+下划线规范
- 项目使用 Lombok，Entity/VO/DTO 均使用 `@Builder` 模式
- 前端是微信小程序，使用原生 JS 编写

## 验收标准
1. 新增开奖号码时，4个分析参数自动计算并保存到数据库
2. 新增购买记录时，4个分析参数自动计算并保存
3. 新增预测号码时，4个分析参数自动计算并保存
4. 前端列表中能正确展示这4个参数
5. 历史数据可通过 SQL 脚本补充计算
