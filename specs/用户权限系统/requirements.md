# 需求文档：用户权限系统

## 背景
当前系统无任何认证鉴权机制，所有 API 裸奔。后续计划开发微信小程序供普通彩民使用，同时保留 Web 管理端给管理员。需要设计一套用户权限体系，区分管理员和普通用户的访问边界。

## 目标
- Web 管理端：管理员通过账号密码登录，拥有全部功能权限
- 微信小程序端：用户通过微信授权一键登录，拥有只读查询权限
- 两套前端共用同一后端，通过 JWT Token 鉴权
- 管理员可管理用户（启用/禁用）

## 用户场景

```
┌─────────────────────────────────────────────────────────────────┐
│                    三档权限模型                                   │
├──────────────┬──────────────────┬───────────────────────────────┤
│   管理员       │   付费/试用用户    │      过期用户                  │
│   (Web端)     │   (小程序端)      │      (小程序端)                │
├──────────────┼──────────────────┼───────────────────────────────┤
│ 全部功能      │ 首月免费全功能     │ 只能看，不能算                   │
│              │ 续费 ¥9.9/月     │                               │
│              │                  │                               │
│ ✅ 开奖管理   │ ✅ 开奖查看       │ ✅ 开奖查看                     │
│ ✅ 购买管理   │ ✅ 规则推荐生成    │ ✅ 冷热号分析                   │
│ ✅ 预测管理   │ ✅ 保存为预测      │ ✅ 手工添加购买                  │
│ ✅ 系统配置   │ ✅ 模拟买入       │ ❌ 规则推荐                     │
│ ✅ 用户管理   │ ✅ 冷热号分析     │ ❌ 保存预测                     │
│ ✅ 日活统计   │                  │ ❌ 模拟买入                     │
└──────────────┴──────────────────┴───────────────────────────────┘
```

## 功能需求

### 1. 数据库设计

#### 1.1 用户表 `t_user`

| 字段 | 类型 | 说明 |
|------|------|------|
| fid | BIGINT PK | 主键 |
| fopenid | VARCHAR(64) UNIQUE | 微信 openid（小程序用户唯一，管理员可为空） |
| fusername | VARCHAR(32) UNIQUE | 登录用户名（管理员必填） |
| fpassword | VARCHAR(128) | 密码 BCrypt 加密（管理员必填） |
| fnickname | VARCHAR(32) | 昵称/显示名 |
| favatar | VARCHAR(256) | 头像 URL |
| frole | VARCHAR(16) NOT NULL | 角色：ADMIN（管理员）/ USER（普通用户） |
| fstatus | TINYINT NOT NULL DEFAULT 1 | 状态：1=正常, 0=禁用 |
| fsubscribe_expire_at | DATETIME | 订阅到期时间（NULL=未订阅/永久有效） |
| fcreated_at | DATETIME | 注册时间（首月免费由此计算） |
| flast_login_at | DATETIME | 最后登录时间 |

> 初始化时插入一条默认管理员记录（admin / 123456）

#### 1.2 用户活跃记录表 `t_user_active`

| 字段 | 类型 | 说明 |
|------|------|------|
| fid | BIGINT PK | 主键 |
| fuser_id | BIGINT NOT NULL | 用户 ID |
| flogin_type | VARCHAR(8) NOT NULL | 登录方式：WEB / WX |
| fip | VARCHAR(45) | 登录 IP |
| fcreated_at | DATETIME | 活跃时间 |

> 每次登录/Token 刷新时写入一条记录，用于统计日活/月活

#### 1.3 订单表 `t_order`

| 字段 | 类型 | 说明 |
|------|------|------|
| fid | BIGINT PK | 主键 |
| fuser_id | BIGINT NOT NULL | 用户 ID |
| forder_no | VARCHAR(32) UNIQUE | 订单号 |
| fwx_transaction_id | VARCHAR(64) | 微信支付交易号 |
| famount | DECIMAL(8,2) NOT NULL | 金额（如 9.90） |
| fstart_at | DATETIME NOT NULL | 订阅开始时间 |
| fend_at | DATETIME NOT NULL | 订阅到期时间 |
| fstatus | TINYINT NOT NULL DEFAULT 0 | 状态：0=待支付, 1=已支付, 2=已取消 |
| fpaid_at | DATETIME | 支付时间 |
| fcreated_at | DATETIME | 创建时间 |

> **订单开始/到期时间计算逻辑**：
> ```
> 用户当前在有效期内（fsSubscribe_expire_at > NOW()）
>   → fstart_at = fsubscribe_expire_at（从当前到期时间接续）
>   → fend_at   = fstart_at + 1个月
>
> 用户已过期或从未订阅（fsSubscribe_expire_at <= NOW() 或 NULL）
>   → fstart_at = NOW()（从当前时间开始）
>   → fend_at   = NOW() + 1个月
> ```
> 支付成功后更新 `t_user.fsubscribe_expire_at = fend_at`

#### 1.4 日活/月活统计

基于 `t_user_active` 表，按日期范围统计独立用户数：

```sql
-- 日活：当日有多少不同用户登录
SELECT COUNT(DISTINCT fuser_id) FROM t_user_active
WHERE fcreated_at >= '2026-06-02 00:00:00' AND fcreated_at < '2026-06-03 00:00:00';

-- 月活：近30天有多少不同用户登录
SELECT COUNT(DISTINCT fuser_id) FROM t_user_active
WHERE fcreated_at >= DATE_SUB(NOW(), INTERVAL 30 DAY);
```

前端管理首页（或仪表盘）展示：
- 今日日活 / 昨日日活
- 近7天日活趋势（折线或柱状）
- 本月月活 / 上月月活
- 总注册用户数

### 2. 认证流程

#### 2.1 Web 管理员登录
```
前端表单（账号+密码）
  → POST /api/auth/login { username, password, loginType: "web" }
  → 后端查 t_user 验证 BCrypt 密码
  → 生成 JWT（含 userId + role + 过期时间）
  → 返回 { token, user: { nickname, role } }
  → 前端存 token 到 localStorage，后续请求带 Authorization: Bearer {token}
```

#### 2.2 微信小程序登录
```
前端 wx.login() 获取 code
  → POST /api/auth/wx-login { code, nickname, avatar }
  → 后端调微信接口 https://api.weixin.qq.com/sns/jscode2session 换取 openid
  → 根据 openid 查找或创建用户（角色 USER）
  → 更新昵称、头像
  → 生成 JWT 返回
  → 小程序存 token，后续请求带 Authorization: Bearer {token}
```

### 3. 鉴权拦截 + 订阅判断

**权限判定流程**（每次请求执行）：

```
JWT 解析 → userId, role
    │
    ├── role = ADMIN → 全部权限 ✅
    │
    └── role = USER → 检查订阅状态
            │
            ├── 注册 ≤ 30 天（首月免费）→ 全功能 ✅
            │
            ├── 已订阅且在有效期内 → 全功能 ✅
            │
            └── 已过期/未订阅 → 受限权限 ⚠️
                    ├── ✅ 开奖查看
                    ├── ✅ 冷热号分析
                    ├── ✅ 手工添加购买记录
                    ├── ❌ 规则推荐
                    ├── ❌ 保存预测
                    └── ❌ 模拟买入
```

**实现方式**：
- JwtInterceptor 解析后将 userId/role 写入 RequestAttribute
- 新增 `@RequirePermission` 注解，标注接口需要的权限级别
- AOP 切面读取 userId → 查 t_user 判断订阅状态 → 决定放行或拒绝

**订阅到期判断**：
```java
// 首月免费：注册30天内
boolean isFreeTrial = user.getCreatedAt().plusDays(30).isAfter(LocalDateTime.now());
// 已订阅：到期时间在未来
boolean isSubscribed = user.getSubscribeExpireAt() != null
                    && user.getSubscribeExpireAt().isAfter(LocalDateTime.now());
// 有全功能权限
boolean hasFullAccess = "ADMIN".equals(role) || isFreeTrial || isSubscribed;
```

- **JwtInterceptor**：拦截所有 `/api/**` 请求（除 `/api/auth/**` 白名单）
  - 校验 Authorization Header 中的 Bearer Token
  - 解析 JWT 获取 userId、role 和 loginType
  - 写入 RequestAttribute 供后续使用
  - **同时写入 t_user_active 活跃记录**（去重：同一用户每天每种登录方式只记首次）
- **@RequireRole** 注解：标注在需要管理员权限的接口上
  - `@RequireRole("ADMIN")` — 仅管理员
  - `@RequireRole("USER")` — 登录用户即可
  - 由 AOP 切面在拦截器之后二次校验

### 4. 接口权限矩阵

| 接口分组 | 接口 | 管理员 | 付费/试用 | 已过期 |
|----------|------|:---:|:---:|:---:|
| 认证 | `/api/auth/login` | ✅ | — | — |
| 认证 | `/api/auth/wx-login` | — | ✅ | ✅ |
| 开奖 | 查询列表/分析 | ✅ | ✅ | ✅ |
| 开奖 | 录入/同步/导入/删除 | ✅ | ❌ | ❌ |
| 购买 | 查询列表 | ✅ | ✅ | ✅ |
| 购买 | **手工录入** | ✅ | ✅ | ✅ |
| 购买 | 批量录入/编辑/删除/重算 | ✅ | ❌ | ❌ |
| 预测 | 查询列表 | ✅ | ✅ | ✅ |
| 预测 | **保存/同步到购买** | ✅ | ✅ | ❌ |
| 预测 | 删除/导出/按期号清除 | ✅ | ❌ | ❌ |
| 推荐 | 查询推荐号码 | ✅ | ✅ | ❌ |
| 推荐 | **保存为预测** | ✅ | ✅ | ❌ |
| 分析 | 冷热号/和值区间等 | ✅ | ✅ | ✅ |
| 配置 | 系统配置管理 | ✅ | ❌ | ❌ |
| 用户 | 用户管理 | ✅ | ❌ | ❌ |
| 统计 | 日活/月活查询 | ✅ | ❌ | ❌ |

> **粗体** = 付费核心功能：规则推荐 + 保存预测 + 模拟买入

### 5. JWT 设计

```
Header:  { "alg": "HS256", "typ": "JWT" }
Payload: {
  "userId": 1,
  "role": "ADMIN",
  "loginType": "web",     // web / wx
  "iat": 1717334400,
  "exp": 1717420800       // 24小时过期
}
Signature: HMAC-SHA256(base64(header) + "." + base64(payload), secret)
```

- 密钥存在 `t_sys_config` 中（`jwt.secret`），支持动态更换
- 过期时间 24 小时，可配置

### 6. 前端改造

#### 6.1 Web 管理端
- 新增登录页面：username + password 表单
- 未登录时重定向到登录页
- token 存 localStorage，请求自动带 Authorization Header
- 页面顶部显示当前用户昵称 + 退出按钮
- 管理员专属功能不受影响，普通接口正常展示

#### 6.2 微信小程序（后续开发）
- 启动时自动调 `wx.login` → 后端换 token
- token 存本地，后续请求自动附加
- 只显示读权限内的页面和功能

### 7. 初始化数据
- 默认管理员：admin / 123456（BCrypt 加密存储）
- `jwt.secret` 配置项加入 `t_sys_config`
- `wx.appid` 和 `wx.secret` 配置项加入 `t_sys_config`

### 8. 订阅计费流程

#### 8.1 生命周期
```
注册 ──▶ 首月免费（30天全功能）──▶ 到期
                                      │
                            ┌─────────┴─────────┐
                            ▼                   ▼
                       续费 ¥9.9/月          不续费
                            │                   │
                            ▼                   ▼
                       全功能恢复            降级为受限用户
                     （到期日 +30天）        （只能看，不能算）
                            │                   │
                            │ 到期              │ 续费
                            ▼                   ▼
                       再次判断...          全功能恢复
```

#### 8.2 支付流程

```
用户发起续费
    │
    ▼
后端判断当前订阅状态
    │
    ├── 有效期内 → fstart_at = fsubscribe_expire_at（接续）
    │              fend_at = fstart_at + 1个月
    │
    └── 已过期   → fstart_at = NOW()
                   fend_at = NOW() + 1个月
    │
    ▼
创建订单（t_order，status=待支付）
    │
    ▼
调微信支付统一下单 → 返回支付参数给小程序
    │
    ▼
用户支付成功 → 微信回调通知
    │
    ▼
更新订单：status=已支付, fpaid_at=NOW(), fwx_transaction_id=xxx
更新用户：fsSubscribe_expire_at = fend_at
    │
    ▼
用户权限立即恢复为全功能
```

#### 8.3 续费举例

| 场景 | 当前到期时间 | 订单开始 | 订单到期 | 用户新到期 |
|------|-------------|----------|----------|-----------|
| 有效期内续费 | 6月15日 | **6月15日** | 7月15日 | 7月15日 |
| 刚过期续费 | 5月30日（已过期） | **6月2日** | 7月2日 | 7月2日 |
| 首月免费续费 | NULL | **6月2日** | 7月2日 | 7月2日 |

#### 8.4 实现要点
- `fsSubscribe_expire_at`：订阅到期时间，由支付回调更新（接续逻辑保证不浪费剩余天数）
- 首月免费判断：`fcreated_at + 30天 > NOW()`，无需创建订单
- 订单驱动：支付成功 → 更新 t_order 状态 → 更新 t_user 到期时间
- 微信支付回调（后续集成）：验签 → 更新 t_order + t_user

## 非功能需求
- JWT 密钥支持动态更换（修改配置后新 token 立即生效，旧 token 自然过期）
- 密码使用 BCrypt 加密，不可逆
- 接口响应从 200ms 级别，鉴权开销 <5ms

## 约束条件
- 技术栈：Java 8 + Spring Boot + MyBatis Plus
- JWT 库：jjwt（轻量，无需 Spring Security）
- 微信小程序 AppID/AppSecret 配置在 `t_sys_config` 中
- Web 端为 HTML/JS 单页应用，小程序端后续独立开发

## 验收标准
1. 不登录直接访问任意 API 返回 401
2. 管理员登录后能访问所有接口
3. 普通用户登录后只能访问查询类接口，写操作返回 403
4. Token 过期后自动失效
5. 前端登录页面正常跳转
