-- Token 过期时间配置化迁移
-- 如果 jwt.expire-hours 配置已存在则跳过

INSERT INTO t_sys_config (fconfig_key, fconfig_value, fconfig_desc, fconfig_type, fconfig_group, fsort_order)
SELECT 'jwt.expire-hours', '6', 'Token过期时间(小时)', 'INT', 'AUTH', 2
WHERE NOT EXISTS (SELECT 1 FROM t_sys_config WHERE fconfig_key = 'jwt.expire-hours');
