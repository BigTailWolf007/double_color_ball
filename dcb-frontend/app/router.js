const PAGES = {
  'lottery-list':   () => LotteryList.render(),
  'lottery-import': () => LotteryImport.render(),
  'purchase-list':  () => PurchaseList.render(),
  'purchase-add':   () => PurchaseAdd.render(),
  'predict-list':   () => PredictList.render(),
  'recommend':      () => Recommend.render(),
}

const router = {
  current: null,

  navigate(page) {
    if (!PAGES[page]) page = 'lottery-list'
    this.current = page

    // 更新菜单高亮
    document.querySelectorAll('.menu-item').forEach(el => {
      el.classList.toggle('active', el.dataset.page === page)
    })

    // 渲染页面
    PAGES[page]()

    // 更新 hash
    location.hash = page
  }
}

// 菜单点击
document.querySelectorAll('.menu-item').forEach(el => {
  el.addEventListener('click', e => {
    e.preventDefault()
    router.navigate(el.dataset.page)
  })
})

// 初始路由：读取 hash 或默认首页
const initPage = location.hash.replace('#', '') || 'lottery-list'
router.navigate(initPage)
