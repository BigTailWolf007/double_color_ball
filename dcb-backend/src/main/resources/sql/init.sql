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
  fcreated_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
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
  fquantity    INT NOT NULL DEFAULT 1 COMMENT '注数',
  fprize_level TINYINT COMMENT '中奖等级(1-6,0=未中,NULL=待计算)',
  fprize_money DECIMAL(12,2) COMMENT '总奖金',
  fremark      VARCHAR(200) COMMENT '备注',
  fcreated_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
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
  fhit_red     TINYINT COMMENT '命中红球数(0-6,NULL=待开奖)',
  fhit_blue    TINYINT(1) COMMENT '是否命中蓝球(0/1,NULL=待开奖)',
  fprize_level TINYINT COMMENT '命中等级(1-6,0=未中,NULL=待开奖)',
  fcreated_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT '预测号码';
