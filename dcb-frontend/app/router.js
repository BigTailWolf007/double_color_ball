// ===== 页面模块注册 =====
const PAGES = {
  'calc-error-log': () => CalcErrorLogList.render(),
  'lottery-list':   () => LotteryList.render(),
  'lottery-import': () => LotteryImport.render(),
  'purchase-list':  () => PurchaseList.render(),
  'purchase-add':   () => PurchaseAdd.render(),
  'predict-list':   () => PredictList.render(),
  'recommend':      () => Recommend.render(),
}

// ===== Tab 配置：主Tab → 子Tab列表 =====
const TAB_CONFIG = {
  lottery: {
    label: '开奖号码',
    subs: [
      { key: 'lottery-list',   label: '号码列表' },
      { key: 'lottery-import', label: 'TXT导入' },
      { key: 'calc-error-log', label: '错误日志' },
    ]
  },
  purchase: {
    label: '购买记录',
    subs: [
      { key: 'purchase-list', label: '记录列表' },
      { key: 'purchase-add',  label: '录入号码' },
    ]
  },
  predict: {
    label: '预测号码',
    subs: [
      { key: 'predict-list', label: '预测列表' },
    ]
  },
  recommend: {
    label: '规则推荐',
    subs: [
      { key: 'recommend', label: '规则推荐' },
    ]
  }
}

// ===== 反向映射：页面 key → 主Tab key =====
const PAGE_TO_MAIN = {}
for (const [main, cfg] of Object.entries(TAB_CONFIG)) {
  for (const sub of cfg.subs) {
    PAGE_TO_MAIN[sub.key] = main
  }
}

// ===== 路由 =====
const router = {
  currentMain: null,
  currentSub: null,

  /** 切换到指定页面（同时激活对应主Tab和子Tab） */
  navigate(page) {
    if (!PAGES[page]) page = 'lottery-list'
    this.currentSub = page
    this.currentMain = PAGE_TO_MAIN[page] || 'lottery'

    // 更新主Tab高亮
    document.querySelectorAll('.main-tab').forEach(el => {
      el.classList.toggle('active', el.dataset.main === this.currentMain)
    })

    // 渲染子Tab
    this.renderSubTabs()

    // 渲染页面
    PAGES[page]()

    // 更新 hash
    location.hash = page
  },

  /** 切换到指定主Tab（激活第一个子Tab） */
  switchMain(main) {
    const cfg = TAB_CONFIG[main]
    if (!cfg) return
    this.navigate(cfg.subs[0].key)
  },

  /** 渲染子Tab按钮 */
  renderSubTabs() {
    const cfg = TAB_CONFIG[this.currentMain]
    if (!cfg) return

    const subTabsEl = document.getElementById('sub-tabs')
    let html = ''
    for (const sub of cfg.subs) {
      const active = sub.key === this.currentSub ? ' active' : ''
      html += `<button class="sub-tab${active}" data-sub="${sub.key}">${sub.label}</button>`
    }
    subTabsEl.innerHTML = html

    // 绑定点击
    subTabsEl.querySelectorAll('.sub-tab').forEach(btn => {
      btn.addEventListener('click', () => this.navigate(btn.dataset.sub))
    })
  }
}

// ===== 事件绑定 =====

// 主Tab点击
document.querySelectorAll('.main-tab').forEach(el => {
  el.addEventListener('click', () => router.switchMain(el.dataset.main))
})

// ===== 初始路由 =====
const initPage = location.hash.replace('#', '') || 'lottery-list'
router.navigate(initPage)
