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
  fball_key   VARCHAR(60) NOT NULL COMMENT '号码复合键：期号-红1-红2-红3-红4-红5-红6-蓝',
  fcreated_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
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
  fhit_red     TINYINT COMMENT '命中红球数(0-6,NULL=待开奖)',
  fhit_blue    TINYINT(1) COMMENT '是否命中蓝球(0/1,NULL=待开奖)',
  fprize_level TINYINT COMMENT '命中等级(1-6,0=未中,NULL=待开奖)',
  fcreated_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_ball_key (fball_key)
) COMMENT '预测号码';

-- 存量数据补填 fball_key（已有数据的库执行此脚本时使用）
-- ALTER TABLE t_lottery_result ADD COLUMN fball_key VARCHAR(60) NOT NULL DEFAULT '' COMMENT '号码复合键：期号-红1-红2-红3-红4-红5-红6-蓝', ADD UNIQUE KEY uk_ball_key (fball_key);
-- UPDATE t_lottery_result SET fball_key = CONCAT(fissue,'-',fred1,'-',fred2,'-',fred3,'-',fred4,'-',fred5,'-',fred6,'-',fblue);
-- ALTER TABLE t_purchase_record ADD COLUMN fball_key VARCHAR(60) NOT NULL DEFAULT '' COMMENT '号码复合键：期号-红1-红2-红3-红4-红5-红6-蓝', ADD UNIQUE KEY uk_ball_key (fball_key);
-- UPDATE t_purchase_record SET fball_key = CONCAT(fissue,'-',fred1,'-',fred2,'-',fred3,'-',fred4,'-',fred5,'-',fred6,'-',fblue);
-- ALTER TABLE t_predict_record ADD COLUMN fball_key VARCHAR(60) NOT NULL DEFAULT '' COMMENT '号码复合键：期号-红1-红2-红3-红4-红5-红6-蓝', ADD UNIQUE KEY uk_ball_key (fball_key);
-- UPDATE t_predict_record SET fball_key = CONCAT(fissue,'-',fred1,'-',fred2,'-',fred3,'-',fred4,'-',fred5,'-',fred6,'-',fblue);

-- 如果购买记录表已有 idx_ball_key 普通索引，需先删除再建唯一索引：
-- ALTER TABLE t_purchase_record DROP INDEX idx_ball_key, ADD UNIQUE KEY uk_ball_key (fball_key);
