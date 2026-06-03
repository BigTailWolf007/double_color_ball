# 任务列表：用户权限系统

## 任务概览
建表 → JWT 工具类 → 认证服务 → 拦截器 + 权限注解 → 各接口加权限 → Web 端登录页 → 日活统计 → 小程序接口预留。

## 任务步骤

### Task 1: 数据库新增用户/活跃/订单表 + 初始化数据
- 文件：`init.sql`
- 操作：修改
- 内容：
  - 新建 `t_user` 表（用户信息 + 订阅到期时间）
  - 新建 `t_user_active` 表（活跃记录）
  - 新建 `t_order` 表（订单记录）
  - 插入默认管理员（admin/123456 BCrypt）
  - 在 `t_sys_config` 中新增 `jwt.secret`、`wx.appid`、`wx.secret`

### Task 2: 新建 JWT 工具类
- 文件：`dcb-backend/src/main/java/com/dcb/common/util/JwtUtils.java`
- 操作：新增
- 内容：
  - `generateToken(userId, role, loginType)` — 生成 JWT（24h过期）
  - `parseToken(token)` — 解析 JWT 返回 Claims
  - `validateToken(token)` — 校验合法性
  - 密钥从 ConfigService 读取 `jwt.secret`

### Task 3: 新建 Entity + Mapper（User / UserActive / Order）
- 文件：
  - `dcb-backend/src/main/java/com/dcb/auth/entity/User.java`
  - `dcb-backend/src/main/java/com/dcb/auth/entity/UserActive.java`
  - `dcb-backend/src/main/java/com/dcb/auth/entity/Order.java`
  - `dcb-backend/src/main/java/com/dcb/auth/mapper/UserMapper.java`
  - `dcb-backend/src/main/java/com/dcb/auth/mapper/UserActiveMapper.java`
  - `dcb-backend/src/main/java/com/dcb/auth/mapper/OrderMapper.java`
- 操作：新增
- 内容：MyBatis Plus Entity + BaseMapper

### Task 4: 新建 AuthService（登录 + JWT 签发）
- 文件：`dcb-backend/src/main/java/com/dcb/auth/service/AuthService.java`
- 操作：新增
- 内容：
  - `webLogin(username, password)` — 账号密码登录，BCrypt 校验
  - `wxLogin(code, nickname, avatar)` — 微信小程序登录，openid 换 token
  - `registerByOpenid(openid, nickname, avatar)` — 自动注册
  - 生成 JWT 返回

### Task 5: 新建 JwtInterceptor（Token 校验 + 活跃记录）
- 文件：`dcb-backend/src/main/java/com/dcb/auth/interceptor/JwtInterceptor.java`
- 文件：`dcb-backend/src/main/java/com/dcb/auth/config/WebMvcConfig.java`
- 操作：新增 + 修改
- 内容：
  - 拦截 `/api/**`（白名单 `/api/auth/**`）
  - 校验 Bearer Token，解析 userId/role/loginType
  - 写入 RequestAttribute
  - 写入 `t_user_active`（同用户每天每种登录方式只记首次）

### Task 6: 新建 @RequirePermission 注解 + AOP 切面
- 文件：`dcb-backend/src/main/java/com/dcb/auth/annotation/RequirePermission.java`
- 文件：`dcb-backend/src/main/java/com/dcb/auth/aspect/PermissionAspect.java`
- 操作：新增
- 内容：
  - 注解定义：`@RequirePermission("FULL")` / `@RequirePermission("READ")`
  - 切面读取 userId → 查 t_user → 判断订阅状态 → 放行/拒绝
  - 权限判断逻辑（ADMIN / 首月免费 / 已订阅 → FULL，否则 READ）

### Task 7: 新建 AuthController
- 文件：`dcb-backend/src/main/java/com/dcb/auth/controller/AuthController.java`
- 操作：新增
- 内容：
  - `POST /api/auth/login` — Web 管理员登录
  - `POST /api/auth/wx-login` — 微信小程序登录
  - `GET /api/auth/me` — 获取当前用户信息

### Task 8: 所有业务 Controller 加权限注解
- 文件：所有 `*Controller.java`
- 操作：修改
- 内容：
  - 查询类接口加 `@RequirePermission("READ")`
  - 写入类接口加 `@RequirePermission("FULL")`
  - 管理员专有接口加 `@RequirePermission("FULL")`

### Task 9: Web 前端登录页 + Token 管理
- 文件：
  - `dcb-frontend/app/pages/login.js` — 新增
  - `dcb-frontend/app/api.js` — 修改（请求自动带 token）
  - `dcb-frontend/app/index.html` — 修改（未登录跳转登录页）
- 操作：新增 + 修改
- 内容：
  - 登录表单：用户名 + 密码
  - token 存 localStorage
  - 所有 API 请求自动附加 `Authorization: Bearer {token}`
  - 401 响应自动跳转登录页
  - 顶部显示当前用户 + 退出按钮

### Task 10: 日活/月活统计接口
- 文件：`AuthController` 或新建 `StatsController`
- 内容：
  - `GET /api/stats/dau` — 今日日活、昨日日活、近7天趋势
  - `GET /api/stats/mau` — 本月月活、上月月活
  - `GET /api/stats/users` — 总注册用户数
  - 需 `@RequirePermission("FULL")`

### Task 11: 订单创建 + 支付回调接口（骨架）
- 文件：`AuthController` 或新建 `OrderController`
- 内容：
  - `POST /api/order/create` — 创建续费订单（判断 fstart_at / fend_at）
  - `POST /api/order/callback` — 微信支付回调（验签 + 更新状态）

### Task 12: 存量库迁移脚本
- 文件：`specs/用户权限系统/migration.sql`
- 操作：新增
- 内容：三张表的 CREATE + INSERT 初始化数据

## 验证步骤
1. 不登录直接访问 API → 401
2. 用 admin/123456 登录 → 获取 token → 访问所有接口正常
3. 小程序登录 → 注册新用户 → 首月免费期内全功能可用
4. 模拟过期用户 → 规则推荐/保存预测返回 403
5. 创建订单 → start_at/end_at 根据当前状态正确计算
6. 前端登录页正常跳转、token 自动携带
