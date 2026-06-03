const api = require('../../utils/api')
const app = getApp()

Page({
  data: {
    list: [],
    page: 1,
    size: 20,
    hasMore: true,
    refreshing: false
  },

  onShow() {
    if (!app.checkLogin()) return
    this.loadList()
  },

  async loadList(append = false) {
    try {
      const res = await api.get('/api/predict/list', { page: this.data.page, size: this.data.size })
      const newList = (res.data && res.data.list) || []
      const list = append ? [...this.data.list, ...newList] : newList
      this.setData({
        list,
        hasMore: newList.length >= this.data.size
      })
    } catch (e) {}
  },

  loadMore() {
    if (!this.data.hasMore) return
    this.setData({ page: this.data.page + 1 }, () => this.loadList(true))
  },

  onRefresh() {
    this.setData({ refreshing: true, page: 1 })
    this.loadList().finally(() => this.setData({ refreshing: false }))
  }
})
