-- ============================================
-- 存量数据库迁移：系统配置表 + 初始数据
-- 新库请使用 init.sql 全新建库
-- ============================================

-- 1. 建表
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

-- 2. 插入初始数据（使用 IGNORE 避免重复插入）
INSERT IGNORE INTO t_sys_config (fconfig_key, fconfig_value, fconfig_desc, fconfig_type, fconfig_group, fsort_order) VALUES
('lottery.api.url',      'https://api2.tanshuapi.com/api/caipiao/v1/query', '彩票API地址',      'STRING', 'API',         1),
('lottery.api.key',      '41169186260d56cdeda190c0f99b8c9f',               '彩票API密钥',      'STRING', 'API',         2),
('lottery.api.timeout',  '10000',                                          'API调用超时(毫秒)', 'INT',    'API',         3),
('async.shard.size',     '1000',                                           '异步计算分片大小',  'INT',    'SYSTEM',      1),
('async.max.retries',    '3',                                              '分片最大重试次数',  'INT',    'SYSTEM',      2),
('recommend.max.result', '1000',                                          '推荐号码最大数量',  'INT',    'SYSTEM',      3),
('scheduled.sync.night',   '0 30 21 ? * 3,5,1',                            '开奖日晚间同步cron', 'CRON',   'SCHEDULE',    1),
('scheduled.sync.morning', '0 0 8 ? * 4,6,2',                              '开奖次日早晨同步cron','CRON',  'SCHEDULE',    2),
('upload.max.file.size', '10MB',                                           '文件上传大小限制',  'STRING', 'UPLOAD',      1),
('cache.recommend.size', '50',                                             '推荐缓存最大条目',  'INT',    'CACHE',       1),
('cache.recommend.expire','600',                                           '推荐缓存过期(秒)',  'INT',    'CACHE',       2);
