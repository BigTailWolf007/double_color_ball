-- ============================================
-- 存量数据库变更脚本：为已存在的表添加分析参数字段
-- 如果使用 init.sql 全新建库，则无需执行此脚本（字段已包含在建表语句中）
-- ============================================

-- 1. 开奖号码表
ALTER TABLE t_lottery_result
    ADD COLUMN fsum_val         INT          COMMENT '红球和值',
    ADD COLUMN fzone_ratio      VARCHAR(16)  COMMENT '区间比（低:中:高）',
    ADD COLUMN fodd_even_ratio  VARCHAR(8)   COMMENT '奇偶比（奇:偶）',
    ADD COLUMN frange_val       INT          COMMENT '跨度';

-- 2. 购买记录表
ALTER TABLE t_purchase_record
    ADD COLUMN fsum_val         INT          COMMENT '红球和值',
    ADD COLUMN fzone_ratio      VARCHAR(16)  COMMENT '区间比（低:中:高）',
    ADD COLUMN fodd_even_ratio  VARCHAR(8)   COMMENT '奇偶比（奇:偶）',
    ADD COLUMN frange_val       INT          COMMENT '跨度';

-- 3. 预测号码表
ALTER TABLE t_predict_record
    ADD COLUMN fsum_val         INT          COMMENT '红球和值',
    ADD COLUMN fzone_ratio      VARCHAR(16)  COMMENT '区间比（低:中:高）',
    ADD COLUMN fodd_even_ratio  VARCHAR(8)   COMMENT '奇偶比（奇:偶）',
    ADD COLUMN frange_val       INT          COMMENT '跨度';
