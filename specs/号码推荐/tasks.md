# 任务列表：号码推荐

## 任务概览

分三层实现：后端算法层 → 后端接口层 → 前端页面层。

后端新增 `RecommendService` 实现过滤算法，改造 `RecommendController` 暴露接口；前端改造 `recommend.js` 实现条件表单 + 结果展示 + 保存预测。

---

## 任务步骤

### Task 1: 新增请求 DTO
- **文件**：`dcb-backend/src/main/java/com/dcb/recommend/dto/RecommendQueryDTO.java`
- **操作**：新建
- **内容**：
  ```
  字段：
  - Integer sumMin          // 和值最小值（可选）
  - Integer sumMax          // 和值最大值（可选）
  - String  zoneRatio       // 区间比，格式 "低:中:高"，如 "2:2:2"（可选）
  - String  oddEvenRatio    // 奇偶比，格式 "奇:偶"，如 "3:3"（可选）
  - List<Integer> excludeRed   // 剔除红球号码列表（可选）
  - List<Integer> excludeBlue  // 剔除蓝球号码列表（可选）
  - Integer page            // 页码，默认 1
  - Integer pageSize        // 每页条数，默认 20
  ```
  遵循 DTO 规范：@Data @Builder @NoArgsConstructor @AllArgsConstructor，字段加中文注释

### Task 2: 新增响应 VO
- **文件**：`dcb-backend/src/main/java/com/dcb/recommend/vo/RecommendResultVO.java`
- **操作**：新建
- **内容**：
  ```
  字段：
  - Long   total            // 符合条件的总组合数
  - Boolean truncated       // 是否因超过上限被截断
  - List<NumberGroupVO> list  // 当前页号码列表
  
  内部类 NumberGroupVO：
  - List<Integer> red       // 6个红球
  - Integer blue            // 蓝球
  ```
  遵循 VO 规范：@Data @Builder @NoArgsConstructor @AllArgsConstructor

### Task 3: 实现推荐算法 Service
- **文件**：`dcb-backend/src/main/java/com/dcb/recommend/service/RecommendService.java`
- **操作**：新建
- **内容**：
  核心方法 `generate(RecommendQueryDTO dto)` 返回 `RecommendResultVO`
  
  算法步骤：
  1. 生成 1~33 全量红球组合 C(33,6)，使用递归或迭代方式
  2. 按条件依次过滤红球组合：
     - 和值过滤：sum(red) in [sumMin, sumMax]
     - 区间比过滤：解析 zoneRatio，统计低/中/高区数量匹配
     - 奇偶比过滤：解析 oddEvenRatio，统计奇/偶数量匹配
     - 剔除红球过滤：red 与 excludeRed 无交集
  3. 对每个通过的红球组合，遍历蓝球 1~16：
     - 剔除蓝球过滤：blue 不在 excludeBlue 中
     - 将 (red, blue) 加入结果集
  4. 记录总数 total；若 total > 10000，截断并标记 truncated=true
  5. 按 page/pageSize 分页返回

  参数校验（抛 BizException）：
  - zoneRatio 格式校验：三段数字，各 >=0，之和 =6
  - oddEvenRatio 格式校验：两段数字，各 >=0，之和 =6
  - excludeRed 元素范围 1~33
  - excludeBlue 元素范围 1~16
  - sumMin <= sumMax（若两者都填）

### Task 4: 改造 RecommendController
- **文件**：`dcb-backend/src/main/java/com/dcb/recommend/controller/RecommendController.java`
- **操作**：修改
- **内容**：
  - 注入 RecommendService
  - 将 `POST /api/recommend/generate` 接口改为接收 `@RequestBody @Valid RecommendQueryDTO`，调用 service.generate()，返回 `Result<RecommendResultVO>`
  - 保留或移除原占位接口 `/api/recommend/rules`（可移除）

### Task 5: 改造前端推荐页面
- **文件**：`dcb-frontend/app/pages/recommend.js`
- **操作**：修改（完整重写）
- **内容**：
  页面分三个区域：
  
  **条件输入区**：
  - 和值范围：两个数字输入框（最小值、最大值）
  - 区间比：一个文本输入框，placeholder="如 2:2:2"
  - 奇偶比：一个文本输入框，placeholder="如 3:3"
  - 剔除红球：33个红球复选框（1~33，红色样式）
  - 剔除蓝球：16个蓝球复选框（1~16，蓝色样式）
  - "生成号码"按钮
  
  **结果统计区**（生成后显示）：
  - 显示"共找到 X 组号码"
  - 若 truncated=true，显示"结果超过10000组，仅展示前10000组"警告
  - 输入期号 + "保存为预测"按钮
  
  **结果列表区**：
  - 分页展示号码，每页 20 条
  - 每条用彩色标签展示（红球红色圆形、蓝球蓝色圆形），复用 utils.js 中的号码渲染函数
  - 分页控件，复用 utils.js 中的分页组件

### Task 6: 更新前端路由菜单
- **文件**：`dcb-frontend/app/index.html`
- **操作**：修改
- **内容**：
  - 确认左侧菜单中"规则推荐"菜单项已存在且路由正确（指向 recommend 页面）
  - 若菜单项文字或路由有误，修正为"号码推荐"并确保路由匹配

---

## 验证步骤

1. 启动后端，访问 `POST /api/recommend/generate`，body 为 `{}`，确认返回结果（total 很大，truncated=true，list 有 20 条）
2. 传入 `{"sumMin":100,"sumMax":110}`，验证返回的所有号码红球之和在 100~110
3. 传入 `{"zoneRatio":"2:2:2"}`，验证低/中/高区各 2 个
4. 传入 `{"oddEvenRatio":"4:2"}`，验证奇数 4 个、偶数 2 个
5. 传入 `{"excludeRed":[1,2,3]}`，验证结果不含 1、2、3
6. 传入 `{"excludeBlue":[1,2]}`，验证蓝球不为 1 或 2
7. 传入非法参数（如 `"zoneRatio":"3:3:3"`），验证返回错误提示
8. 前端页面：打开推荐页，填写条件，点击生成，验证结果展示正确
9. 前端页面：输入期号，点击保存，验证跳转预测列表后有对应记录
