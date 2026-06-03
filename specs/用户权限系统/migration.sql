-- ============================================
-- 存量数据库迁移：用户权限系统
-- 新库请使用 init.sql 全新建库
-- ============================================

-- 1. 用户表
CREATE TABLE IF NOT EXISTS t_user (
  fid                    BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  fopenid                VARCHAR(64)  UNIQUE COMMENT '微信openid',
  fusername              VARCHAR(32)  UNIQUE COMMENT '登录用户名',
  fpassword              VARCHAR(128) COMMENT '密码PBKDF2加密',
  fnickname              VARCHAR(32)  COMMENT '昵称',
  favatar                VARCHAR(256) COMMENT '头像URL',
  frole                  VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN/USER',
  fstatus                TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=正常,0=禁用',
  fsubscribe_expire_at   DATETIME     COMMENT '订阅到期时间',
  fcreated_at            DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  flast_login_at         DATETIME     COMMENT '最后登录时间'
) COMMENT '用户表';

-- 2. 用户活跃记录表
CREATE TABLE IF NOT EXISTS t_user_active (
  fid          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  fuser_id     BIGINT       NOT NULL COMMENT '用户ID',
  flogin_type  VARCHAR(8)   NOT NULL COMMENT '登录方式：WEB/WX',
  fip          VARCHAR(45)  COMMENT '登录IP',
  fcreated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '活跃时间',
  INDEX idx_user_date (fuser_id, fcreated_at)
) COMMENT '用户活跃记录';

-- 3. 订单表
CREATE TABLE IF NOT EXISTS t_order (
  fid                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  fuser_id             BIGINT        NOT NULL COMMENT '用户ID',
  forder_no            VARCHAR(32)   UNIQUE COMMENT '订单号',
  fwx_transaction_id   VARCHAR(64)   COMMENT '微信支付交易号',
  famount              DECIMAL(8,2)  NOT NULL COMMENT '金额',
  fstart_at            DATETIME      NOT NULL COMMENT '订阅开始时间',
  fend_at              DATETIME      NOT NULL COMMENT '订阅到期时间',
  fstatus              TINYINT       NOT NULL DEFAULT 0 COMMENT '0=待支付,1=已支付,2=已取消',
  fpaid_at             DATETIME      COMMENT '支付时间',
  fcreated_at          DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT '订单表';

-- 4. 补充系统配置：JWT密钥 + 微信小程序
INSERT IGNORE INTO t_sys_config (fconfig_key, fconfig_value, fconfig_desc, fconfig_type, fconfig_group, fsort_order) VALUES
('jwt.secret', 'dcb-jwt-secret-key-2026', 'JWT签名密钥', 'STRING', 'AUTH', 1),
('wx.appid', '',                           '微信小程序AppID',  'STRING', 'AUTH', 2),
('wx.secret','',                           '微信小程序Secret', 'STRING', 'AUTH', 3);
