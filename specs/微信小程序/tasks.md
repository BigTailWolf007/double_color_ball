# 任务列表：微信小程序

## 任务概览

整体分三条线并行推进：
- **后端改造**：微信登录对接真实 API、数据表加 user_id、API 权限分级、微信支付
- **小程序开发**：原生微信小程序项目搭建、5 个核心页面、登录鉴权
- **部署联调**：后端公网部署、小程序审核配置

---

## 任务步骤

### Task 1: 后端 — 微信登录对接真实 API

- 文件：`dcb-backend/src/main/java/com/dcb/auth/service/AuthService.java`（修改）
- 文件：`dcb-backend/src/main/java/com/dcb/common/util/WechatApiUtil.java`（新建）
- 文件：`dcb-backend/src/main/java/com/dcb/auth/controller/AuthController.java`（修改）
- 操作：修改
- 内容：
  - 新建 `WechatApiUtil`，封装微信 `code2Session` 接口调用（GET `https://api.weixin.qq.com/sns/jscode2session`）
  - 修改 `wxLogin` 接口：前端传 `code` 替代 `openid`，后端调微信 API 换 `openid` + `session_key`
  - 保留 mock 模式兼容：若 `wx.appid` 配置为空，沿用旧的直接传 openid 模式（方便本地开发调试）
  - `wxLogin` 返回 `token`、`nickname`、`role`、`subscribeExpireAt`

### Task 2: 后端 — 数据表加 user_id 字段

- 文件：`dcb-backend/src/main/resources/sql/migration_wx_user_id.sql`（新建）
- 文件：`dcb-backend/src/main/resources/sql/init.sql`（修改）
- 文件：`dcb-backend/src/main/java/com/dcb/purchase/entity/PurchaseRecord.java`（修改）
- 文件：`dcb-backend/src/main/java/com/dcb/predict/entity/PredictRecord.java`（修改）
- 操作：修改
- 内容：
  - `t_purchase_record` 新增 `fuser_id BIGINT DEFAULT NULL`，加普通索引
  - `t_predict_record` 新增 `fuser_id BIGINT DEFAULT NULL`，加普通索引
  - 两表存量数据 `fuser_id=NULL` 视为管理员数据，查询时 ADMIN 角色不受限制
  - Entity 类加 `@TableField("fuser_id") private Long userId;`

### Task 3: 后端 — API 权限分级（ADMIN 看全部，USER 看自己）

- 文件：`dcb-backend/src/main/java/com/dcb/purchase/controller/PurchaseController.java`（修改）
- 文件：`dcb-backend/src/main/java/com/dcb/purchase/service/PurchaseService.java`（修改）
- 文件：`dcb-backend/src/main/java/com/dcb/predict/controller/PredictController.java`（修改）
- 文件：`dcb-backend/src/main/java/com/dcb/predict/service/PredictService.java`（修改）
- 文件：`dcb-backend/src/main/java/com/dcb/purchase/mapper/PurchaseRecordMapper.java`（修改）
- 文件：`dcb-backend/src/main/java/com/dcb/predict/mapper/PredictRecordMapper.java`（修改）
- 操作：修改
- 内容：
  - Purchase/Predict 的 `list`、`summary` 接口：ADMIN 查全部，USER 自动加 `fuser_id = 当前用户` 过滤条件
  - Purchase/Predict 的 `add/save` 接口：自动写入 `fuser_id = 当前用户ID`
  - Purchase/Predict 的 `delete` 接口：USER 只能删自己的记录，ADMIN 可删全部
  - Lottery `list`、`analysis`、`issue-suggest` 保持开放，所有角色可访问
  - Recommend `generate`、`save-predict` 所有登录用户可访问
  - `/api/admin/**` 仅 ADMIN 可访问（已有 `PermissionHelper.checkFull`）

### Task 4: 后端 — 微信支付集成

- 文件：`dcb-backend/src/main/java/com/dcb/payment/controller/PaymentController.java`（新建）
- 文件：`dcb-backend/src/main/java/com/dcb/payment/service/PaymentService.java`（新建）
- 文件：`dcb-backend/src/main/resources/sql/migration_wx_payment.sql`（新建）
- 操作：新建
- 内容：
  - 订单表 `t_order` 已有，无需改表（字段 `fwx_transaction_id`、`famount`、`fstart_at`、`fend_at`、`fstatus` 已完备）
  - `POST /api/payment/plans` — 返回套餐列表（月卡/季卡/年卡，价格从系统配置读取）
  - `POST /api/payment/order` — 用户下单，生成预支付订单，调用微信统一下单 API（JSAPI），返回支付参数给小程序端调 `wx.requestPayment`
  - `POST /api/payment/notify` — 微信支付回调，验签后更新订单状态 + 用户订阅到期时间
  - 配置项新增：`wx.mchid`（商户号）、`wx.mchkey`（APIv2密钥）或 `wx.mch-v3-key`（APIv3）

### Task 5: 小程序 — 项目初始化与基础架构

- 文件：`dcb-miniprogram/app.json`（新建）
- 文件：`dcb-miniprogram/app.js`（新建）
- 文件：`dcb-miniprogram/app.wxss`（新建）
- 文件：`dcb-miniprogram/utils/api.js`（新建）
- 文件：`dcb-miniprogram/utils/auth.js`（新建）
- 文件：`dcb-miniprogram/project.config.json`（新建）
- 操作：新建
- 内容：
  - `app.json`：注册所有页面路径、窗口配置（导航栏标题"双色球分析"）、tabBar 配置（首页/推荐/记录/我的）
  - `app.js`：全局登录状态管理，`onLaunch` 时检查 token 有效性
  - `utils/api.js`：封装 `wx.request`，统一处理 JWT 附加、401 跳转登录、错误提示
  - `utils/auth.js`：`wx.login()` 封装 → 调后端 `/api/auth/wx-login` → 存储 token 到 `wx.setStorageSync`
  - `project.config.json`：appid 占位，基础库版本 ≥ 2.10.0

### Task 6: 小程序 — 登录页

- 文件：`dcb-miniprogram/pages/login/login.wxml`（新建）
- 文件：`dcb-miniprogram/pages/login/login.js`（新建）
- 文件：`dcb-miniprogram/pages/login/login.wxss`（新建）
- 操作：新建
- 内容：
  - 首屏显示小程序 logo + "双色球分析系统" 标题
  - "微信一键登录"按钮，调用 `wx.getUserProfile` 获取昵称头像（或使用新版头像昵称填写组件）
  - 登录成功后跳转首页
  - 已登录用户自动跳过此页

### Task 7: 小程序 — 首页仪表盘

- 文件：`dcb-miniprogram/pages/index/index.wxml`（新建）
- 文件：`dcb-miniprogram/pages/index/index.js`（新建）
- 文件：`dcb-miniprogram/pages/index/index.wxss`（新建）
- 操作：新建
- 内容：
  - 顶部：最新一期开奖号码展示（红球 6 个 + 蓝球 1 个，球样式）
  - 中部：今日推荐号码卡片（调用 `/api/recommend/generate`）
  - 底部：个人统计卡片（累计购买注数、中奖次数、总盈亏）
  - 下拉刷新支持

### Task 8: 小程序 — 开奖号码页

- 文件：`dcb-miniprogram/pages/lottery/lottery.wxml`（新建）
- 文件：`dcb-miniprogram/pages/lottery/lottery.js`（新建）
- 文件：`dcb-miniprogram/pages/lottery/lottery.wxss`（新建）
- 操作：新建
- 内容：
  - 列表展示历史开奖号码（分页滚动加载）
  - 日期范围筛选
  - 点击某期查看详情：6 红 + 1 蓝、和值、跨度、区间比、奇偶比、奖金分配
  - 使用 `<scroll-view>` 实现下拉刷新 + 上拉加载更多

### Task 9: 小程序 — 推荐号码页

- 文件：`dcb-miniprogram/pages/recommend/recommend.wxml`（新建）
- 文件：`dcb-miniprogram/pages/recommend/recommend.js`（新建）
- 文件：`dcb-miniprogram/pages/recommend/recommend.wxss`（新建）
- 操作：新建
- 内容：
  - 显示系统推荐的号码组合（调用 `/api/recommend/generate`）
  - 支持切换推荐策略（如：冷热号、区间比、奇偶比偏好）
  - 每组号码旁有"采纳"按钮，点击后将号码保存为购买记录（调用 `/api/purchase/add`）
  - 采纳成功后 toast 提示

### Task 10: 小程序 — 我的购买记录页

- 文件：`dcb-miniprogram/pages/purchase/purchase.wxml`（新建）
- 文件：`dcb-miniprogram/pages/purchase/purchase.js`（新建）
- 文件：`dcb-miniprogram/pages/purchase/purchase.wxss`（新建）
- 操作：新建
- 内容：
  - 购买记录列表，每项显示：期号、号码球、中奖等级标签、奖金
  - 顶部汇总统计：总投入 / 总奖金 / 盈亏（红绿标识）
  - 按中奖等级筛选
  - 未订阅用户限制查看条数（如最近 10 条）

### Task 11: 小程序 — 我的预测记录页

- 文件：`dcb-miniprogram/pages/predict/predict.wxml`（新建）
- 文件：`dcb-miniprogram/pages/predict/predict.js`（新建）
- 文件：`dcb-miniprogram/pages/predict/predict.wxss`（新建）
- 操作：新建
- 内容：
  - 预测记录列表，每项显示：期号、号码球、命中红球数、命中蓝球、等级
  - 未开奖的记录标注"待开奖"
  - 按期号筛选

### Task 12: 小程序 — 个人中心页

- 文件：`dcb-miniprogram/pages/mine/mine.wxml`（新建）
- 文件：`dcb-miniprogram/pages/mine/mine.js`（新建）
- 文件：`dcb-miniprogram/pages/mine/mine.wxss`（新建）
- 操作：新建
- 内容：
  - 用户头像 + 昵称
  - 订阅状态卡片：已订阅显示到期时间，未订阅/已过期显示"立即订阅"入口
  - 菜单列表：我的购买、我的预测、订阅续费、关于我们
  - 退出登录按钮

### Task 13: 小程序 — 订阅付费页

- 文件：`dcb-miniprogram/pages/subscribe/subscribe.wxml`（新建）
- 文件：`dcb-miniprogram/pages/subscribe/subscribe.js`（新建）
- 文件：`dcb-miniprogram/pages/subscribe/subscribe.wxss`（新建）
- 操作：新建
- 内容：
  - 套餐卡片（月卡/季卡/年卡），展示价格和原价
  - 当前订阅状态展示
  - 选择套餐 → 调用后端下单 → 获取支付参数 → 调起 `wx.requestPayment`
  - 支付成功/失败/取消的状态处理
  - 非订阅用户访问受限功能时自动跳转此页

### Task 14: 部署与配置

- 文件：`dcb-miniprogram/project.config.json`（修改）
- 文件：`dcb-backend/src/main/resources/application.yml`（修改）
- 操作：配置
- 内容：
  - 小程序：填写真实 AppID，配置合法域名（`request` + `uploadFile` 域名）
  - 后端：公网部署，配置 HTTPS（微信要求），域名指向后端服务
  - 系统配置表填入 `wx.appid`、`wx.secret`、`wx.mchid`、`wx.mchkey`
  - 微信支付回调地址配置

---

## 验证步骤

1. 小程序扫码进入 → 微信一键登录成功
2. 首页正常展示最新开奖号码和推荐号码
3. 开奖号码列表可滚动加载，日期筛选正常
4. 推荐号码可"采纳"为购买记录
5. 购买记录仅显示当前用户的，中奖状态正确
6. 预测记录仅显示当前用户的，命中结果正确
7. 个人中心展示订阅状态，到期后功能受限
8. 订阅付费流程完整：选择套餐 → 微信支付 → 订阅生效
9. 管理员 Web 端不受影响，仍可查看全部数据
10. 微信审核通过并上架
