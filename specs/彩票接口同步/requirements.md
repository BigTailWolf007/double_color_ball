# 需求文档：彩票接口同步

## 背景
当前系统支持 TXT 文件导入和手动录入两种方式添加开奖号码，但缺少从外部彩票 API 自动同步开奖信息的能力。用户希望能在开奖号码列表页面一键从第三方接口拉取指定期号的开奖详情（号码、奖金分配、销售金额、奖池金额、领奖截止日期等），自动填充到系统中。

## 目标
1. 在开奖号码列表页新增"同步开奖信息"按钮
2. 点击后弹出期号输入框，默认填写当前最新期号（可修改）
3. 确认后调用外部彩票 API 获取开奖信息，解析并保存到 `t_lottery_result` 表
4. 扩展 `t_lottery_result` 表结构，新增奖品分配、领奖截止日、销售金额、奖池金额字段
5. 前端列表展示新增的扩展信息

## 功能需求

### F1. 数据库扩展
- `t_lottery_result` 表新增5个字段：
  - `fprize_json`（TEXT，可空）—— 奖品分配原始 JSON，供后台解析计算用
  - `fprize_text`（TEXT，可空）—— 奖品分配可读文本，直接返回前端展示（如：`一等奖8注6,130,798元；二等奖135注268,041元；...`）
  - `fdeadline`（DATE，可空）—— 最后领奖日期
  - `fsale_amount`（DECIMAL(16,2)，可空）—— 本期彩票销售金额（单位：元）
  - `fpool_amount`（DECIMAL(16,2)，可空）—— 奖池总金额（单位：元）

### F2. 后端同步接口
- 新增 `POST /api/lottery/sync` 接口
- 请求参数：`issue`（期号）
- 后端逻辑：
  1. 调用外部 API：`https://api2.tanshuapi.com/api/caipiao/v1/query?key=41169186260d56cdeda190c0f99b8c9f&caipiaoid=11&issueno={期号}`
  2. 校验 API 返回码（code=1 表示成功）
  3. 解析返回数据：
     - 号码解析：`number`（空格分隔6红球） + `refernumber`（蓝球）
     - 期号：`issueno`
     - 开奖日期：`opendate`
     - 截止日期：`deadline`
     - 销售金额：`saleamount`
     - 奖池金额：`totalmoney`
     - 奖品详情：`prize` 数组 → 原始 JSON 存 `fprize_json`；同时生成可读文本存 `fprize_text`（格式：`{prizename}{num}注{单注奖金}元`，多条用分隔符拼接）
  4. 若该期号已存在：更新扩展字段（prize_json、prize_text、deadline、sale_amount、pool_amount），若本地 fdraw_date 为空则用 opendate 补充
  5. 若该期号不存在：解析 opendate 为开奖日期、解析 number+refernumber 为红蓝球 → 构建 ball_key → 插入新记录（含 prize_json、prize_text）
  6. 同步成功后，自动触发该期购买记录的盈亏计算（`POST /api/purchase/calc/{issue}`）

### F3. 前端"同步开奖信息"按钮
- 开奖号码列表页顶部操作栏新增"同步开奖信息"按钮
- 点击弹出弹窗，包含：
  - 期号输入框（默认填写当前数据库最新期号+1，带下拉提示）
  - 确认/取消按钮
- 确认后显示 loading 状态，成功后提示并刷新列表

### F4. 前端列表展示扩展
- 开奖号码列表表格新增列：
  - 奖金详情列（直接展示 `fprize_text` 可读文本）
  - 销售金额列（含千分位格式化）
  - 奖池金额列（含千分位格式化）

## 非功能需求
- 外部 API 调用需超时控制（10秒）
- 外部 API 调用失败需给出明确的错误提示
- 同步操作保证数据一致性（已有期号只更新不重复插入）

## 约束条件
- 后端：Java 8 + Spring Boot 2.7.18 + MyBatis-Plus 3.5.5
- 前端：纯原生 HTML/CSS/JS（IIFE 模块化）
- 数据库：MySQL 8.0
- 外部 API Key 当前硬编码（后续可考虑配置化）
- 遵循现有项目分层规范：Controller → Service → Mapper

## 验收标准
1. 数据库 `t_lottery_result` 新增5个字段，历史数据不受影响
2. `POST /api/lottery/sync` 接口可正常调用，成功同步期号 2026061 的数据
3. 已存在的期号再次同步时，只更新扩展字段，不重复创建
4. 前端列表页可看到"同步开奖信息"按钮，点击弹出期号输入弹窗
5. 同步成功后列表自动刷新，显示奖金详情、销售金额、奖池金额
6. 同步成功后自动触发购买记录的中奖计算
