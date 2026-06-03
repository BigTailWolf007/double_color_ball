const api = require('../../utils/api')
const app = getApp()

Page({
  data: {
    latest: null,
    stats: {},
    recommends: [],
    refreshing: false
  },

  onShow() {
    if (!app.checkLogin()) return
    this.loadData()
  },

  async loadData() {
    try {
      // 并行请求
      const [lotteryRes, recommendRes, summaryRes] = await Promise.all([
        api.get('/api/lottery/list', { page: 1, size: 1 }),
        api.post('/api/recommend/generate', { pageSize: 5 }),
        api.get('/api/purchase/summary')
      ])

      // 最新开奖
      const lotteryList = lotteryRes.data && lotteryRes.data.list
      if (lotteryList && lotteryList.length > 0) {
        const latest = lotteryList[0]
        this.setData({
          latest: {
            issue: latest.issue,
            reds: latest.reds,
            blue: latest.blue
          }
        })
      }

      // 个人统计
      const summary = summaryRes.data || {}
      this.setData({
        stats: {
          totalQuantity: summary.totalQuantity || 0,
          winCount: summary.winCount || 0,
          profit: summary.profit || 0
        }
      })

      // 推荐号码
      const recData = recommendRes.data
      if (recData && recData.list) {
        this.setData({
          recommends: recData.list.slice(0, 5).map(item => ({
            reds: item.reds,
            blue: item.blue
          }))
        })
      }
    } catch (e) {
      console.error('首页加载失败:', e)
    }
  },

  /** 采纳推荐号码 */
  async adoptRecommend(e) {
    const { reds, blue } = e.currentTarget.dataset
    try {
      await api.post('/api/purchase/add', [{
        issue: this.getNextIssue(),
        red1: reds[0], red2: reds[1], red3: reds[2],
        red4: reds[3], red5: reds[4], red6: reds[5],
        blue: blue, quantity: 1
      }])
      wx.showToast({ title: '已添加到购买记录', icon: 'success' })
    } catch (e) {
      // 错误已在 api.js 中处理
    }
  },

  getNextIssue() {
    // 从最新期号推算下一期
    const latest = this.data.latest
    if (!latest) return ''
    const num = parseInt(latest.issue)
    return String(num + 1)
  },

  onRefresh() {
    this.setData({ refreshing: true })
    this.loadData().finally(() => {
      this.setData({ refreshing: false })
    })
  }
})
