const api = require('../../utils/api')
const app = getApp()

Page({
  data: {
    summary: {},
    list: [],
    page: 1,
    size: 20,
    hasMore: true,
    prizeLevel: '',
    refreshing: false
  },

  onShow() {
    if (!app.checkLogin()) return
    this.loadData()
  },

  async loadData(append = false) {
    const params = { page: this.data.page, size: this.data.size }
    if (this.data.prizeLevel) params.prizeLevels = this.data.prizeLevel

    try {
      const [listRes, summaryRes] = await Promise.all([
        api.get('/api/purchase/list', params),
        api.get('/api/purchase/summary', this.data.prizeLevel ? { prizeLevels: this.data.prizeLevel } : {})
      ])

      const newList = (listRes.data && listRes.data.list) || []
      const list = append ? [...this.data.list, ...newList] : newList
      this.setData({
        list,
        hasMore: newList.length >= this.data.size,
        summary: summaryRes.data || {}
      })
    } catch (e) {}
  },

  loadMore() {
    if (!this.data.hasMore) return
    this.setData({ page: this.data.page + 1 }, () => this.loadData(true))
  },

  filterByLevel(e) {
    const level = e.currentTarget.dataset.level
    this.setData({ prizeLevel: level, page: 1 }, () => this.loadData())
  },

  onRefresh() {
    this.setData({ refreshing: true, page: 1 })
    this.loadData().finally(() => this.setData({ refreshing: false }))
  }
})
