-- ============================================
-- 微信小程序：购买/预测记录表加 fuser_id 字段
-- 存量数据为 NULL（管理员公共数据），小程序用户新增数据写入具体 user_id
-- ============================================

-- t_purchase_record 加 fuser_id
ALTER TABLE t_purchase_record
  ADD COLUMN fuser_id BIGINT DEFAULT NULL COMMENT '所属用户ID（NULL=管理员公共数据）',
  ADD INDEX idx_user_id (fuser_id);

-- 修改唯一约束：从单字段 ball_key 改为 (fuser_id, ball_key)，允许不同用户拥有相同号码组合
ALTER TABLE t_purchase_record
  DROP INDEX uk_ball_key,
  ADD UNIQUE KEY uk_user_ball_key (fuser_id, fball_key);

-- t_predict_record 加 fuser_id
ALTER TABLE t_predict_record
  ADD COLUMN fuser_id BIGINT DEFAULT NULL COMMENT '所属用户ID（NULL=管理员公共数据）',
  ADD INDEX idx_user_id (fuser_id);

ALTER TABLE t_predict_record
  DROP INDEX uk_ball_key,
  ADD UNIQUE KEY uk_user_ball_key (fuser_id, fball_key);
