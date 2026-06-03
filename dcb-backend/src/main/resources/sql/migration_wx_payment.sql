-- 微信支付配置项迁移
-- 为已有数据库添加支付相关配置

INSERT INTO t_sys_config (fconfig_key, fconfig_value, fconfig_desc, fconfig_type, fconfig_group, fsort_order)
SELECT 'wx.mchid', '', '微信支付商户号', 'STRING', 'AUTH', 4
WHERE NOT EXISTS (SELECT 1 FROM t_sys_config WHERE fconfig_key = 'wx.mchid');

INSERT INTO t_sys_config (fconfig_key, fconfig_value, fconfig_desc, fconfig_type, fconfig_group, fsort_order)
SELECT 'wx.mchkey', '', '微信支付API密钥', 'STRING', 'AUTH', 5
WHERE NOT EXISTS (SELECT 1 FROM t_sys_config WHERE fconfig_key = 'wx.mchkey');

INSERT INTO t_sys_config (fconfig_key, fconfig_value, fconfig_desc, fconfig_type, fconfig_group, fsort_order)
SELECT 'wx.notify-url', '', '支付回调通知地址', 'STRING', 'AUTH', 6
WHERE NOT EXISTS (SELECT 1 FROM t_sys_config WHERE fconfig_key = 'wx.notify-url');
