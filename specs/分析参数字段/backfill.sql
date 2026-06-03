-- ============================================
-- 历史数据补算：为已有记录的4个分析参数字段填充值
-- 执行前请先确认 migration.sql 中的 ALTER TABLE 已执行
-- ============================================

SET SQL_SAFE_UPDATES = 0;

-- 1. 补算开奖号码表
UPDATE t_lottery_result
SET
    fsum_val = fred1 + fred2 + fred3 + fred4 + fred5 + fred6,
    fzone_ratio = CONCAT(
        (IF(fred1<=11,1,0) + IF(fred2<=11,1,0) + IF(fred3<=11,1,0) + IF(fred4<=11,1,0) + IF(fred5<=11,1,0) + IF(fred6<=11,1,0)),
        ':',
        (IF(fred1 BETWEEN 12 AND 22,1,0) + IF(fred2 BETWEEN 12 AND 22,1,0) + IF(fred3 BETWEEN 12 AND 22,1,0) + IF(fred4 BETWEEN 12 AND 22,1,0) + IF(fred5 BETWEEN 12 AND 22,1,0) + IF(fred6 BETWEEN 12 AND 22,1,0)),
        ':',
        (IF(fred1>=23,1,0) + IF(fred2>=23,1,0) + IF(fred3>=23,1,0) + IF(fred4>=23,1,0) + IF(fred5>=23,1,0) + IF(fred6>=23,1,0))
    ),
    fodd_even_ratio = CONCAT(
        (IF(fred1%2=1,1,0) + IF(fred2%2=1,1,0) + IF(fred3%2=1,1,0) + IF(fred4%2=1,1,0) + IF(fred5%2=1,1,0) + IF(fred6%2=1,1,0)),
        ':',
        (IF(fred1%2=0,1,0) + IF(fred2%2=0,1,0) + IF(fred3%2=0,1,0) + IF(fred4%2=0,1,0) + IF(fred5%2=0,1,0) + IF(fred6%2=0,1,0))
    ),
    frange_val = GREATEST(fred1, fred2, fred3, fred4, fred5, fred6) - LEAST(fred1, fred2, fred3, fred4, fred5, fred6)
WHERE fsum_val IS NULL;

-- 2. 补算购买记录表
UPDATE t_purchase_record
SET
    fsum_val = fred1 + fred2 + fred3 + fred4 + fred5 + fred6,
    fzone_ratio = CONCAT(
        (IF(fred1<=11,1,0) + IF(fred2<=11,1,0) + IF(fred3<=11,1,0) + IF(fred4<=11,1,0) + IF(fred5<=11,1,0) + IF(fred6<=11,1,0)),
        ':',
        (IF(fred1 BETWEEN 12 AND 22,1,0) + IF(fred2 BETWEEN 12 AND 22,1,0) + IF(fred3 BETWEEN 12 AND 22,1,0) + IF(fred4 BETWEEN 12 AND 22,1,0) + IF(fred5 BETWEEN 12 AND 22,1,0) + IF(fred6 BETWEEN 12 AND 22,1,0)),
        ':',
        (IF(fred1>=23,1,0) + IF(fred2>=23,1,0) + IF(fred3>=23,1,0) + IF(fred4>=23,1,0) + IF(fred5>=23,1,0) + IF(fred6>=23,1,0))
    ),
    fodd_even_ratio = CONCAT(
        (IF(fred1%2=1,1,0) + IF(fred2%2=1,1,0) + IF(fred3%2=1,1,0) + IF(fred4%2=1,1,0) + IF(fred5%2=1,1,0) + IF(fred6%2=1,1,0)),
        ':',
        (IF(fred1%2=0,1,0) + IF(fred2%2=0,1,0) + IF(fred3%2=0,1,0) + IF(fred4%2=0,1,0) + IF(fred5%2=0,1,0) + IF(fred6%2=0,1,0))
    ),
    frange_val = GREATEST(fred1, fred2, fred3, fred4, fred5, fred6) - LEAST(fred1, fred2, fred3, fred4, fred5, fred6)
WHERE fsum_val IS NULL;

-- 3. 补算预测号码表
UPDATE t_predict_record
SET
    fsum_val = fred1 + fred2 + fred3 + fred4 + fred5 + fred6,
    fzone_ratio = CONCAT(
        (IF(fred1<=11,1,0) + IF(fred2<=11,1,0) + IF(fred3<=11,1,0) + IF(fred4<=11,1,0) + IF(fred5<=11,1,0) + IF(fred6<=11,1,0)),
        ':',
        (IF(fred1 BETWEEN 12 AND 22,1,0) + IF(fred2 BETWEEN 12 AND 22,1,0) + IF(fred3 BETWEEN 12 AND 22,1,0) + IF(fred4 BETWEEN 12 AND 22,1,0) + IF(fred5 BETWEEN 12 AND 22,1,0) + IF(fred6 BETWEEN 12 AND 22,1,0)),
        ':',
        (IF(fred1>=23,1,0) + IF(fred2>=23,1,0) + IF(fred3>=23,1,0) + IF(fred4>=23,1,0) + IF(fred5>=23,1,0) + IF(fred6>=23,1,0))
    ),
    fodd_even_ratio = CONCAT(
        (IF(fred1%2=1,1,0) + IF(fred2%2=1,1,0) + IF(fred3%2=1,1,0) + IF(fred4%2=1,1,0) + IF(fred5%2=1,1,0) + IF(fred6%2=1,1,0)),
        ':',
        (IF(fred1%2=0,1,0) + IF(fred2%2=0,1,0) + IF(fred3%2=0,1,0) + IF(fred4%2=0,1,0) + IF(fred5%2=0,1,0) + IF(fred6%2=0,1,0))
    ),
    frange_val = GREATEST(fred1, fred2, fred3, fred4, fred5, fred6) - LEAST(fred1, fred2, fred3, fred4, fred5, fred6)
WHERE fsum_val IS NULL;

SET SQL_SAFE_UPDATES = 1;
