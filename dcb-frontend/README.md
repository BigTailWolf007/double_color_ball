# dcb-frontend 双色球分析系统前端

纯 HTML/JS/CSS 单页应用，无任何框架依赖，直接用浏览器打开即可运行。

## 快速启动

项目无构建步骤，通过静态服务器启动即可。

```bash
# 推荐：npx serve（Node.js 环境，端口随机分配）
cd dcb-frontend/app
npx serve -p 8081

# 或指定端口
npx serve -p 3000
```

启动后终端会输出访问地址，例如：

```
INFO  Accepting connections at http://localhost:8081
```

> Windows 环境下 `python -m http.server` 可能因权限问题无法运行，推荐使用 `npx serve`。

后端默认地址为 `http://localhost:8080`，如需修改请编辑 `app/api.js` 第一行：

```js
const BASE_URL = 'http://localhost:8080'
```

## 项目结构

```
dcb-frontend/
└── app/
    ├── index.html              # 单页入口：布局骨架、导航菜单、弹窗/确认框/输入框模板
    ├── style.css               # 全局样式：布局、卡片、表格、按钮、标签、分页、弹窗等
    ├── api.js                  # HTTP 封装：基于 fetch，统一处理 code≠200 的错误提示
    ├── utils.js                # 公共工具：toast、confirm、prompt、分页渲染、号码选择器
    ├── router.js               # Hash 路由：根据 location.hash 切换页面模块
    └── pages/
        ├── lottery-list.js     # 开奖号码列表：查询/手动录入/删除
        ├── lottery-import.js   # TXT 文件导入：拖拽上传，显示导入结果
        ├── purchase-list.js    # 购买记录列表：查询/删除/汇总统计（投入/奖金/盈亏）
        ├── purchase-add.js     # 录入购买号码：多组批量录入
        ├── predict-list.js     # 预测号码列表：查询/删除/按期号清除/手动补算
        └── recommend.js        # 规则推荐：占位页（开发中）
```

## 架构说明

### 路由

使用 URL Hash 实现单页路由，页面 key 与菜单 `data-page` 属性对应：

| Hash | 页面 |
|------|------|
| `#lottery-list` | 开奖号码列表 |
| `#lottery-import` | TXT 导入 |
| `#purchase-list` | 购买记录列表 |
| `#purchase-add` | 录入购买号码 |
| `#predict-list` | 预测号码列表 |
| `#recommend` | 规则推荐 |

### 页面模块

每个页面是一个 IIFE 模块，暴露 `render()` 方法，由路由调用后将 HTML 注入 `#main-content`，再绑定事件。

```js
const LotteryList = (() => {
  function render() { /* 渲染 HTML + 绑定事件 */ }
  return { render }
})()
```

### API 封装

`api.js` 封装了四个方法，响应 `code !== 200` 时自动弹出错误提示并抛出异常：

```js
api.get('/api/lottery/list', { page: 1, size: 20 })
api.post('/api/lottery/add', { issue: '2024001', ... })
api.postForm('/api/lottery/import', formData)
api.delete('/api/lottery/123')
```

### 公共工具（utils.js）

| 函数 | 说明 |
|------|------|
| `toast(msg, type)` | 右上角消息提示，type: success/error/warning/info |
| `confirm(msg)` | Promise 确认框，用户取消则 reject |
| `prompt(title, placeholder, validator)` | Promise 输入框 |
| `openModal(title, bodyHtml, footerHtml)` | 打开通用弹窗 |
| `closeModal()` | 关闭弹窗 |
| `renderPagination(id, page, size, total, onChange)` | 渲染分页组件 |
| `renderBallPicker(id, options, selected, max, tagClass, onChange)` | 号码选择器（支持单选/多选） |
| `renderReds(reds)` | 渲染红球标签 HTML |
| `renderBlue(blue)` | 渲染蓝球标签 HTML |
| `renderPrizeLevel(level, desc)` | 渲染中奖等级标签 HTML |

## 对接后端

后端 API 列表（baseURL: `http://localhost:8080`）：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/lottery/list` | 开奖号码列表，参数：page/size/issue/startDate/endDate |
| POST | `/api/lottery/add` | 新增开奖号码 |
| POST | `/api/lottery/import` | 上传 TXT 文件批量导入，返回 success/skip/fail/issues |
| DELETE | `/api/lottery/{id}` | 删除开奖号码 |
| GET | `/api/purchase/list` | 购买记录列表，参数：page/size/issue/prizeLevel |
| GET | `/api/purchase/summary` | 购买汇总（totalCost/totalPrizeMoney/profit） |
| POST | `/api/purchase/add` | 批量新增购买记录（数组） |
| POST | `/api/purchase/calc/{issue}` | 补算指定期号未计算记录的中奖等级 |
| POST | `/api/purchase/recalc` | 按 ID 列表强制重算中奖等级（数组） |
| DELETE | `/api/purchase/{id}` | 删除购买记录 |
| GET | `/api/predict/list` | 预测号码列表，参数：page/size/issue |
| POST | `/api/predict/save` | 批量保存预测号码（数组） |
| POST | `/api/predict/calc/{issue}` | 手动补算指定期号预测命中结果 |
| DELETE | `/api/predict/{id}` | 删除单条预测记录 |
| DELETE | `/api/predict/issue/{issue}` | 按期号批量删除预测记录 |
| POST | `/api/recommend/generate` | 按条件生成推荐号码，参数：sumMin/sumMax/zoneRatio/oddEvenRatio/excludeRed/includeBlue/page/pageSize |

所有接口响应格式：

```json
{ "code": 200, "data": { ... }, "message": "success" }
```
