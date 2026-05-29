# dcb-backend — 双色球号码分析系统后端

基于 Spring Boot + MyBatis-Plus 的双色球号码分析系统后端服务，提供开奖号码管理、购买记录管理、预测号码管理等功能。

---

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 1.8 | 运行环境 |
| Spring Boot | 2.7.18 | 核心框架 |
| MyBatis-Plus | 3.5.5 | ORM 框架 |
| MySQL | 8.0+ | 数据库 |
| Lombok | — | 代码生成 |

---

## 快速启动

### 前置条件

- JDK 1.8+
- MySQL 8.0+
- Maven 3.6+

### 步骤

**1. 初始化数据库**

```sql
CREATE DATABASE double_color_ball DEFAULT CHARACTER SET utf8mb4;
```

执行初始化脚本：

```bash
mysql -u root -p double_color_ball < src/main/resources/sql/init.sql
```

**2. 修改数据库配置**

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/double_color_ball?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 你的密码
```

**3. 启动服务**

```bash
mvn spring-boot:run
```

服务默认运行在 `http://localhost:8080`。

---

## 项目结构

```
dcb-backend/
├── src/main/java/com/dcb/
│   ├── DcbApplication.java          # 启动类
│   ├── common/
│   │   ├── config/                  # 跨域、MyBatis-Plus 配置
│   │   ├── exception/               # 全局异常处理
│   │   ├── result/                  # 统一响应体 Result<T>、PageResult<T>
│   │   ├── enums/                   # PrizeLevel 中奖等级枚举
│   │   └── util/                    # LotteryUtils 号码校验与中奖计算
│   ├── lottery/                     # 开奖号码模块
│   │   ├── controller/
│   │   ├── service/
│   │   ├── mapper/
│   │   ├── entity/
│   │   ├── dto/
│   │   └── vo/
│   ├── predict/                     # 预测号码模块
│   │   └── ...（同上）
│   ├── purchase/                    # 购买记录模块
│   │   └── ...（同上）
│   └── recommend/                   # 规则推荐模块（开发中）
│       └── controller/
├── src/main/resources/
│   ├── application.yml              # 应用配置
│   ├── mapper/                      # MyBatis XML
│   └── sql/init.sql                 # 数据库初始化脚本
└── pom.xml
```

---

## 数据库设计

### t_lottery_result — 开奖号码表

| 字段 | 类型 | 说明 |
|------|------|------|
| fid | BIGINT | 主键，自增 |
| fissue | VARCHAR(20) | 期号（唯一） |
| fdraw_date | DATE | 开奖日期 |
| fred1 ~ fred6 | TINYINT | 红球 1-6 |
| fblue | TINYINT | 蓝球 |
| fcreated_at | DATETIME | 创建时间 |

### t_purchase_record — 购买记录表

| 字段 | 类型 | 说明 |
|------|------|------|
| fid | BIGINT | 主键，自增 |
| fissue | VARCHAR(20) | 期号 |
| fred1 ~ fred6 | TINYINT | 红球 1-6 |
| fblue | TINYINT | 蓝球 |
| fquantity | INT | 注数 |
| fprize_level | TINYINT | 中奖等级（NULL=待计算，0=未中奖，1-6=等级） |
| fprize_money | DECIMAL(12,2) | 总奖金 |
| fremark | VARCHAR(200) | 备注 |
| fcreated_at | DATETIME | 创建时间 |

### t_predict_record — 预测号码表

| 字段 | 类型 | 说明 |
|------|------|------|
| fid | BIGINT | 主键，自增 |
| fissue | VARCHAR(20) | 目标期号 |
| fred1 ~ fred6 | TINYINT | 红球 1-6 |
| fblue | TINYINT | 蓝球 |
| fhit_red | TINYINT | 命中红球数（NULL=待开奖） |
| fhit_blue | TINYINT(1) | 是否命中蓝球（NULL=待开奖） |
| fprize_level | TINYINT | 命中等级（NULL=待开奖，0=未中，1-6=等级） |
| fcreated_at | DATETIME | 创建时间 |

---

## API 接口

### 开奖号码 `/api/lottery`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/import` | TXT 文件批量导入 |
| POST | `/add` | 手动录入单条开奖号码 |
| DELETE | `/{id}` | 删除开奖号码 |
| GET | `/list` | 分页查询（支持期号、日期范围筛选） |

**TXT 导入格式**（每行一条，空格分隔）：

```
2024001 01 05 12 18 25 33 07
2024002 03 08 15 22 28 31 12
```

### 购买记录 `/api/purchase`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/add` | 批量录入购买记录 |
| POST | `/calc/{issue}` | 补算指定期号的中奖等级 |
| DELETE | `/{id}` | 删除购买记录 |
| GET | `/list` | 分页查询（支持期号、中奖等级筛选） |
| GET | `/summary` | 汇总统计（总投入、总奖金、盈亏） |

### 预测号码 `/api/predict`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/save` | 保存预测号码 |
| POST | `/calc/{issue}` | 补算指定期号的命中结果 |
| GET | `/list` | 分页查询 |
| DELETE | `/{id}` | 按 ID 删除 |
| DELETE | `/issue/{issue}` | 按期号删除该期所有预测 |

### 规则推荐 `/api/recommend`（开发中）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/rules` | 查询规则列表 |
| POST | `/generate` | 生成推荐号码 |

---

## 中奖等级规则

| 等级 | 命中条件 | 奖金 |
|------|----------|------|
| 一等奖 | 6 红 + 1 蓝 | 浮动 |
| 二等奖 | 6 红 + 0 蓝 | 浮动 |
| 三等奖 | 5 红 + 1 蓝 | 3,000 元 |
| 四等奖 | 5 红 + 0 蓝 或 4 红 + 1 蓝 | 200 元 |
| 五等奖 | 4 红 + 0 蓝 或 3 红 + 1 蓝 | 10 元 |
| 六等奖 | 任意红 + 1 蓝 | 5 元 |
| 未中奖 | 其他 | 0 元 |

---

## 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

分页响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 100,
    "list": [ ... ]
  }
}
```

错误响应：

```json
{
  "code": 500,
  "message": "期号不能为空",
  "data": null
}
```
