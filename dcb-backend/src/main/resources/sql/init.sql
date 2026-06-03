CREATE DATABASE IF NOT EXISTS double_color_ball DEFAULT CHARSET utf8mb4;
USE double_color_ball;

-- 开奖号码表
CREATE TABLE IF NOT EXISTS t_lottery_result (
  fid         BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  fissue      VARCHAR(20) NOT NULL UNIQUE COMMENT '期号',
  fdraw_date  DATE COMMENT '开奖日期',
  fred1       TINYINT NOT NULL COMMENT '红球1',
  fred2       TINYINT NOT NULL COMMENT '红球2',
  fred3       TINYINT NOT NULL COMMENT '红球3',
  fred4       TINYINT NOT NULL COMMENT '红球4',
  fred5       TINYINT NOT NULL COMMENT '红球5',
  fred6       TINYINT NOT NULL COMMENT '红球6',
  fblue       TINYINT NOT NULL COMMENT '蓝球',
  fball_key    VARCHAR(60) NOT NULL COMMENT '号码复合键：期号-红1-红2-红3-红4-红5-红6-蓝',
  fsum_val         INT COMMENT '红球和值',
  fzone_ratio      VARCHAR(16) COMMENT '区间比（低:中:高）',
  fodd_even_ratio  VARCHAR(8) COMMENT '奇偶比（奇:偶）',
  frange_val       INT COMMENT '跨度',
  fprize_json  TEXT COMMENT '奖品分配JSON（原始数据，供后台解析）',
  fprize_text  TEXT COMMENT '奖品分配可读文本（如：一等奖8注6,130,798元；二等奖135注268,041元；...）',
  fdeadline    DATE COMMENT '最后领奖日期',
  fsale_amount DECIMAL(16,2) COMMENT '本期销售金额（元）',
  fpool_amount DECIMAL(16,2) COMMENT '奖池总金额（元）',
  fcreated_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_ball_key (fball_key)
) COMMENT '开奖号码';

-- 购买记录表
CREATE TABLE IF NOT EXISTS t_purchase_record (
  fid          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  fissue       VARCHAR(20) NOT NULL COMMENT '期号',
  fred1        TINYINT NOT NULL COMMENT '红球1',
  fred2        TINYINT NOT NULL COMMENT '红球2',
  fred3        TINYINT NOT NULL COMMENT '红球3',
  fred4        TINYINT NOT NULL COMMENT '红球4',
  fred5        TINYINT NOT NULL COMMENT '红球5',
  fred6        TINYINT NOT NULL COMMENT '红球6',
  fblue        TINYINT NOT NULL COMMENT '蓝球',
  fball_key    VARCHAR(60) NOT NULL COMMENT '号码复合键：期号-红1-红2-红3-红4-红5-红6-蓝',
  fsum_val         INT COMMENT '红球和值',
  fzone_ratio      VARCHAR(16) COMMENT '区间比（低:中:高）',
  fodd_even_ratio  VARCHAR(8) COMMENT '奇偶比（奇:偶）',
  frange_val       INT COMMENT '跨度',
  fquantity    INT NOT NULL DEFAULT 1 COMMENT '注数',
  fprize_level TINYINT COMMENT '中奖等级(1-6,0=未中,NULL=待计算)',
  fprize_money DECIMAL(12,2) COMMENT '总奖金',
  fremark      VARCHAR(200) COMMENT '备注',
  fcreated_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_ball_key (fball_key)
) COMMENT '购买记录';

-- 预测号码表
CREATE TABLE IF NOT EXISTS t_predict_record (
  fid          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  fissue       VARCHAR(20) NOT NULL COMMENT '目标期号',
  fred1        TINYINT NOT NULL COMMENT '红球1',
  fred2        TINYINT NOT NULL COMMENT '红球2',
  fred3        TINYINT NOT NULL COMMENT '红球3',
  fred4        TINYINT NOT NULL COMMENT '红球4',
  fred5        TINYINT NOT NULL COMMENT '红球5',
  fred6        TINYINT NOT NULL COMMENT '红球6',
  fblue        TINYINT NOT NULL COMMENT '蓝球',
  fball_key    VARCHAR(60) NOT NULL COMMENT '号码复合键：期号-红1-红2-红3-红4-红5-红6-蓝',
  fsum_val         INT COMMENT '红球和值',
  fzone_ratio      VARCHAR(16) COMMENT '区间比（低:中:高）',
  fodd_even_ratio  VARCHAR(8) COMMENT '奇偶比（奇:偶）',
  frange_val       INT COMMENT '跨度',
  fhit_red     TINYINT COMMENT '命中红球数(0-6,NULL=待开奖)',
  fhit_blue    TINYINT(1) COMMENT '是否命中蓝球(0/1,NULL=待开奖)',
  fprize_level TINYINT COMMENT '命中等级(1-6,0=未中,NULL=待开奖)',
  fcreated_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_ball_key (fball_key)
) COMMENT '预测号码';

-- 计算错误日志表
CREATE TABLE IF NOT EXISTS t_calc_error_log (
  fid         BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  fissue      VARCHAR(20) NOT NULL COMMENT '期号',
  fcalc_type  VARCHAR(10) NOT NULL COMMENT '计算类型：purchase / predict',
  fid_start   BIGINT NOT NULL COMMENT '分片起始ID',
  fid_end     BIGINT NOT NULL COMMENT '分片结束ID（不包含）',
  ferror_msg  TEXT COMMENT '异常信息',
  fstatus     TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0=待处理,1=已重试成功,2=已忽略',
  fretry_count INT DEFAULT 0 COMMENT '已重试次数',
  fcreated_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT '计算错误日志';

-- 用户表
CREATE TABLE IF NOT EXISTS t_user (
  fid                    BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  fopenid                VARCHAR(64)  UNIQUE COMMENT '微信openid',
  fusername              VARCHAR(32)  UNIQUE COMMENT '登录用户名',
  fpassword              VARCHAR(128) COMMENT '密码BCrypt加密',
  fnickname              VARCHAR(32)  COMMENT '昵称',
  favatar                VARCHAR(256) COMMENT '头像URL',
  frole                  VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN/USER',
  fstatus                TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=正常,0=禁用',
  fsubscribe_expire_at   DATETIME     COMMENT '订阅到期时间',
  fcreated_at            DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  flast_login_at         DATETIME     COMMENT '最后登录时间'
) COMMENT '用户表';

-- 用户活跃记录表
CREATE TABLE IF NOT EXISTS t_user_active (
  fid          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  fuser_id     BIGINT       NOT NULL COMMENT '用户ID',
  flogin_type  VARCHAR(8)   NOT NULL COMMENT '登录方式：WEB/WX',
  fip          VARCHAR(45)  COMMENT '登录IP',
  fcreated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '活跃时间',
  INDEX idx_user_date (fuser_id, fcreated_at)
) COMMENT '用户活跃记录';

-- 订单表
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

-- 系统配置表
CREATE TABLE IF NOT EXISTS t_sys_config (
  fid            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  fconfig_key    VARCHAR(64)  NOT NULL UNIQUE COMMENT '配置键',
  fconfig_value  VARCHAR(512) NOT NULL COMMENT '配置值',
  fconfig_desc   VARCHAR(128) COMMENT '配置说明',
  fconfig_type   VARCHAR(16)  NOT NULL DEFAULT 'STRING' COMMENT '值类型：STRING/INT/CRON',
  fconfig_group  VARCHAR(32)  NOT NULL DEFAULT 'SYSTEM' COMMENT '分组：API/SYSTEM/SCHEDULE/UPLOAD/CACHE',
  fsort_order    INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
  fupdated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间'
) COMMENT '系统配置表';

-- 预置系统配置数据
INSERT INTO t_sys_config (fconfig_key, fconfig_value, fconfig_desc, fconfig_type, fconfig_group, fsort_order) VALUES
('lottery.api.url',      'https://api2.tanshuapi.com/api/caipiao/v1/query', '彩票API地址',      'STRING', 'API',         1),
('lottery.api.key',      '41169186260d56cdeda190c0f99b8c9f',               '彩票API密钥',      'STRING', 'API',         2),
('lottery.api.timeout',  '10000',                                          'API调用超时(毫秒)', 'INT',    'API',         3),
('async.shard.size',     '1000',                                           '异步计算分片大小',  'INT',    'SYSTEM',      1),
('async.max.retries',    '3',                                              '分片最大重试次数',  'INT',    'SYSTEM',      2),
('recommend.max.result', '1000',                                           '推荐号码最大数量',  'INT',    'SYSTEM',      3),
('scheduled.sync.night',   '0 30 21 ? * 3,5,1',                            '开奖日晚间同步cron', 'CRON',   'SCHEDULE',    1),
('scheduled.sync.morning', '0 0 8 ? * 4,6,2',                              '开奖次日早晨同步cron','CRON',  'SCHEDULE',    2),
('upload.max.file.size', '10MB',                                           '文件上传大小限制',  'STRING', 'UPLOAD',      1),
('cache.recommend.size', '50',                                             '推荐缓存最大条目',  'INT',    'CACHE',       1),
('cache.recommend.expire','600',                                           '推荐缓存过期(秒)',  'INT',    'CACHE',       2),
('jwt.secret',            'dcb-jwt-secret-key-2026',                       'JWT签名密钥',       'STRING', 'AUTH',        1),
('wx.appid',              '',                                               '微信小程序AppID',   'STRING', 'AUTH',        2),
('wx.secret',             '',                                               '微信小程序Secret',  'STRING', 'AUTH',        3);

-- 存量数据补填 fball_key（已有数据的库执行此脚本时使用）
-- ALTER TABLE t_lottery_result ADD COLUMN fball_key VARCHAR(60) NOT NULL DEFAULT '' COMMENT '号码复合键：期号-红1-红2-红3-红4-红5-红6-蓝', ADD UNIQUE KEY uk_ball_key (fball_key);
-- UPDATE t_lottery_result SET fball_key = CONCAT(fissue,'-',fred1,'-',fred2,'-',fred3,'-',fred4,'-',fred5,'-',fred6,'-',fblue);
-- ALTER TABLE t_purchase_record ADD COLUMN fball_key VARCHAR(60) NOT NULL DEFAULT '' COMMENT '号码复合键：期号-红1-红2-红3-红4-红5-红6-蓝', ADD UNIQUE KEY uk_ball_key (fball_key);
-- UPDATE t_purchase_record SET fball_key = CONCAT(fissue,'-',fred1,'-',fred2,'-',fred3,'-',fred4,'-',fred5,'-',fred6,'-',fblue);
-- ALTER TABLE t_predict_record ADD COLUMN fball_key VARCHAR(60) NOT NULL DEFAULT '' COMMENT '号码复合键：期号-红1-红2-红3-红4-红5-红6-蓝', ADD UNIQUE KEY uk_ball_key (fball_key);
-- UPDATE t_predict_record SET fball_key = CONCAT(fissue,'-',fred1,'-',fred2,'-',fred3,'-',fred4,'-',fred5,'-',fred6,'-',fblue);

-- 如果购买记录表已有 idx_ball_key 普通索引，需先删除再建唯一索引：
-- ALTER TABLE t_purchase_record DROP INDEX idx_ball_key, ADD UNIQUE KEY uk_ball_key (fball_key);

-- 分析参数字段（新库已包含在 CREATE TABLE 中，存量库请执行 specs/分析参数字段/migration.sql）
