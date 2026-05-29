# 任务列表：双色球分析

## 任务概览

按以下顺序实施：
1. 搭建单体 Spring Boot 后端骨架
2. 公共模块（统一响应、异常、枚举、工具类）
3. 数据库初始化脚本
4. 开奖号码模块（导入、手动录入、列表）
5. 购买记录模块（录入、中奖计算、列表汇总）
6. 预测号码模块（保存、对比开奖、清除）
7. 规则推荐占位接口
8. 前端 Vue 3 项目（页面逐一实现）

---

## 任务步骤

### Task 1: 初始化 Spring Boot 后端项目

- **文件**：`dcb-backend/pom.xml`、`dcb-backend/src/main/resources/application.yml`
- **操作**：新建
- **内容**：
  - Spring Boot 2.7.x，Java 8
  - 依赖：`spring-boot-starter-web`、`mybatis-plus-boot-starter`、`mysql-connector-java`、`lombok`、`spring-boot-starter-validation`
  - `application.yml` 配置数据源（占位符，用户自填）、MyBatis-Plus 驼峰映射、文件上传大小限制 10MB、跨域允许所有来源（开发阶段）
  - 主启动类 `DcbApplication`

---

### Task 2: 公共模块

- **文件**：`dcb-backend/src/main/java/com/dcb/common/`
- **操作**：新建
- **内容**：
  - `Result<T>`：统一响应体，含 `code`、`message`、`data`，提供 `success()`、`fail()` 静态方法
  - `PageResult<T>`：分页响应体，含 `total`、`list`
  - `BizException`：业务异常，含 `message` 字段
  - `GlobalExceptionHandler`：`@RestControllerAdvice`，捕获 `BizException`、`MethodArgumentNotValidException`、`Exception`
  - `PrizeLevel` 枚举：`FIRST(1,"一等奖",0)`~`SIXTH(6,"六等奖",5)`、`NO_PRIZE(0,"未中奖",0)`，含固定奖金字段（一、二等奖固定奖金设为 0 表示浮动）
  - `LotteryUtils`：
    - `validateRed(List<Integer>)`：校验红球（6个、1-33、不重复）
    - `validateBlue(int)`：校验蓝球（1-16）
    - `calcPrize(购买红球, 购买蓝球, 开奖红球, 开奖蓝球)`：返回 `PrizeLevel`

---

### Task 3: 数据库初始化脚本

- **文件**：`dcb-backend/src/main/resources/sql/init.sql`
- **操作**：新建
- **内容**：

```sql
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
```

---

### Task 4: 开奖号码模块 - 实体与 Mapper

- **文件**：
  - `dcb-backend/src/main/java/com/dcb/lottery/entity/LotteryResult.java`
  - `dcb-backend/src/main/java/com/dcb/lottery/mapper/LotteryResultMapper.java`
  - `dcb-backend/src/main/resources/mapper/LotteryResultMapper.xml`
- **操作**：新建
- **内容**：
  - `LotteryResult` 实体（`@Data @Builder @NoArgsConstructor @AllArgsConstructor @TableName("t_lottery_result")`），字段使用 `@TableField("fxxx")` 映射
  - `LotteryResultMapper extends BaseMapper<LotteryResult>`
  - XML 中实现按期号模糊查询 + 日期范围查询的分页 SQL

---

### Task 5: 开奖号码模块 - Service 与 Controller

- **文件**：
  - `dcb-backend/src/main/java/com/dcb/lottery/service/LotteryService.java`
  - `dcb-backend/src/main/java/com/dcb/lottery/dto/LotteryAddDTO.java`
  - `dcb-backend/src/main/java/com/dcb/lottery/controller/LotteryController.java`
- **操作**：新建
- **内容**：
  - `POST /api/lottery/import`：上传 TXT，逐行解析，校验，跳过重复期号，返回导入结果
  - `POST /api/lottery/add`：手动录入，DTO 校验（`@NotBlank issue`、红蓝球范围）
  - `DELETE /api/lottery/{id}`：删除
  - `GET /api/lottery/list`：分页查询，参数 `issue`、`startDate`、`endDate`、`page`（默认1）、`size`（默认20）

---

### Task 6: 购买记录模块 - 实体与 Mapper

- **文件**：
  - `dcb-backend/src/main/java/com/dcb/purchase/entity/PurchaseRecord.java`
  - `dcb-backend/src/main/java/com/dcb/purchase/mapper/PurchaseRecordMapper.java`
  - `dcb-backend/src/main/resources/mapper/PurchaseRecordMapper.xml`
- **操作**：新建
- **内容**：
  - `PurchaseRecord` 实体（`@Data @Builder @NoArgsConstructor @AllArgsConstructor @TableName("t_purchase_record")`），字段使用 `@TableField("fxxx")` 映射
  - XML 中实现按期号、中奖等级筛选的分页 SQL，以及汇总查询（总注数、总奖金）

---

### Task 7: 购买记录模块 - Service 与 Controller

- **文件**：
  - `dcb-backend/src/main/java/com/dcb/purchase/service/PurchaseService.java`
  - `dcb-backend/src/main/java/com/dcb/purchase/dto/PurchaseAddDTO.java`
  - `dcb-backend/src/main/java/com/dcb/purchase/controller/PurchaseController.java`
- **操作**：新建
- **内容**：
  - `POST /api/purchase/add`：批量录入（接收 `List<PurchaseAddDTO>`），录入后自动查开奖号码计算中奖等级
  - `POST /api/purchase/calc/{issue}`：补算指定期号所有未计算记录的中奖等级
  - `DELETE /api/purchase/{id}`：删除
  - `GET /api/purchase/list`：分页查询，参数 `issue`、`prizeLevel`、`page`、`size`
  - `GET /api/purchase/summary`：汇总，返回总投入（注数×2元）、总奖金、盈亏

---

### Task 8: 预测号码模块 - 实体与 Mapper

- **文件**：
  - `dcb-backend/src/main/java/com/dcb/predict/entity/PredictRecord.java`
  - `dcb-backend/src/main/java/com/dcb/predict/mapper/PredictRecordMapper.java`
  - `dcb-backend/src/main/resources/mapper/PredictRecordMapper.xml`
- **操作**：新建
- **内容**：
  - `PredictRecord` 实体（`@Data @Builder @NoArgsConstructor @AllArgsConstructor @TableName("t_predict_record")`），字段使用 `@TableField("fxxx")` 映射
  - `PredictRecordMapper extends BaseMapper<PredictRecord>`
  - XML 中实现按期号筛选的分页 SQL，以及按期号批量更新命中结果的 SQL

---

### Task 9: 预测号码模块 - Service 与 Controller

- **文件**：
  - `dcb-backend/src/main/java/com/dcb/predict/service/PredictService.java`
  - `dcb-backend/src/main/java/com/dcb/predict/dto/PredictSaveDTO.java`
  - `dcb-backend/src/main/java/com/dcb/predict/controller/PredictController.java`
- **操作**：新建
- **内容**：
  - `POST /api/predict/save`：保存规则推荐模块生成的预测号码（接收 `List<PredictSaveDTO>`，含目标期号和号码），保存后若该期已有开奖号码则立即计算命中结果
  - `POST /api/predict/calc/{issue}`：手动触发补算指定期号所有待开奖预测记录的命中结果
  - `GET /api/predict/list`：分页查询，参数 `issue`、`page`、`size`，返回含命中情况的列表
  - `DELETE /api/predict/{id}`：按 ID 删除单条预测记录
  - `DELETE /api/predict/issue/{issue}`：按期号删除该期所有预测记录

---

### Task 10: 规则推荐占位接口

- **文件**：`dcb-backend/src/main/java/com/dcb/recommend/controller/RecommendController.java`
- **操作**：新建
- **内容**：
  - `GET /api/recommend/rules`：返回 `Result.fail("规则功能开发中")`
  - `POST /api/recommend/generate`：返回 `Result.fail("规则功能开发中")`；预留参数 `issue`（目标期号），后续实现时生成号码并调用 `PredictService.save()` 保存

---

### Task 11: 前端项目初始化

- **文件**：`dcb-frontend/`
- **操作**：新建（`npm create vite@latest dcb-frontend -- --template vue`）
- **内容**：
  - 安装依赖：`element-plus`、`axios`、`vue-router`
  - `src/api/request.js`：axios 封装，baseURL 指向 `http://localhost:8080`，统一错误提示
  - `src/router/index.js`：路由配置
  - `src/App.vue`：左侧菜单 + `<router-view>` 布局
  - 路由：
    ```
    /lottery/list     开奖号码列表
    /lottery/import   TXT 导入
    /purchase/list    购买记录列表
    /purchase/add     录入购买记录
    /predict/list     预测号码列表
    /recommend        规则推荐（占位）
    ```

---

### Task 12: 前端 - 开奖号码页面

- **文件**：`dcb-frontend/src/views/lottery/LotteryList.vue`、`LotteryImport.vue`
- **操作**：新建
- **内容**：
  - `LotteryList.vue`：
    - 顶部筛选栏（期号输入、日期范围选择器、查询/重置按钮）
    - 表格展示（期号、开奖日期、红球×6、蓝球、操作-删除）
    - 红球用红色标签展示，蓝球用蓝色标签展示
    - 分页组件
    - 右上角"手动录入"按钮，弹出 Dialog 表单（期号、日期、红球多选、蓝球单选）
  - `LotteryImport.vue`：
    - `el-upload` 拖拽上传 TXT
    - 上传后展示结果：成功 N 条、跳过 N 条、失败 N 条
    - 失败明细用折叠面板展示（行号 + 原因）

---

### Task 13: 前端 - 购买记录页面

- **文件**：`dcb-frontend/src/views/purchase/PurchaseList.vue`、`PurchaseAdd.vue`
- **操作**：新建
- **内容**：
  - `PurchaseList.vue`：
    - 筛选栏（期号、中奖等级下拉）
    - 表格（期号、红球、蓝球、注数、中奖等级、总奖金、操作-删除）
    - 中奖等级用不同颜色 Tag 展示（一等奖金色、未中奖灰色等）
    - 底部汇总卡片：总投入 / 总奖金 / 盈亏（盈亏正数绿色、负数红色）
  - `PurchaseAdd.vue`：
    - 动态表单，默认一行，可"添加一组"增加行
    - 每行：期号输入、红球选择（1-33 多选恰好6个）、蓝球选择（1-16 单选）、注数输入、备注
    - 提交后跳转到列表页

---

### Task 14: 前端 - 预测号码页面

- **文件**：`dcb-frontend/src/views/predict/PredictList.vue`
- **操作**：新建
- **内容**：
  - 顶部筛选栏（目标期号输入、查询/重置按钮）
  - 表格展示（目标期号、红球×6、蓝球、命中红球数、是否命中蓝球、命中等级、生成时间、操作-删除）
  - 命中等级用颜色 Tag 展示，待开奖显示"待开奖"灰色 Tag
  - 顶部工具栏：
    - "按期号清除"按钮：弹出输入框填写期号，确认后调用 `DELETE /api/predict/issue/{issue}`
    - "手动补算"按钮：弹出输入框填写期号，确认后调用 `POST /api/predict/calc/{issue}`

---

### Task 15: 前端 - 规则推荐占位页

- **文件**：`dcb-frontend/src/views/recommend/Recommend.vue`
- **操作**：新建
- **内容**：
  - 页面中央展示"规则功能开发中"图标 + 文字
  - 预留"配置规则"和"生成推荐号码"两个按钮（`disabled` 状态）

---

## 验证步骤

1. 后端启动，`GET /api/lottery/list` 返回空分页 `{ total: 0, list: [] }`
2. 准备测试 TXT（含正常行、重复期号行、格式错误行），调用导入接口验证三类结果计数正确
3. 手动录入开奖号码，测试红球重复、超范围等校验报错
4. 录入购买记录，覆盖以下中奖场景验证计算正确：
   - 6红+1蓝 → 一等奖
   - 6红+0蓝 → 二等奖
   - 5红+1蓝 → 三等奖
   - 4红+1蓝 → 五等奖
   - 0红+1蓝 → 六等奖
   - 0红+0蓝 → 未中奖
5. 汇总接口验证总投入 = 总注数 × 2，盈亏 = 总奖金 - 总投入
6. 调用 `POST /api/predict/save` 保存预测号码，验证：
   - 对应期号已有开奖号码时，命中结果自动计算
   - 对应期号无开奖号码时，`fprize_level` 为 NULL，显示"待开奖"
7. 录入开奖号码后调用 `POST /api/predict/calc/{issue}`，验证该期预测记录命中结果更新正确
8. 按期号清除：`DELETE /api/predict/issue/{issue}` 后该期所有预测记录消失
9. 按 ID 删除：`DELETE /api/predict/{id}` 只删除单条
10. 前端全流程：导入开奖 → 录入购买 → 查看中奖 → 查看预测命中 → 清除预测
11. 规则推荐页正常显示占位内容
