const api = require('../../utils/api')

Page({
  data: {
    list: [],
    page: 1,
    size: 20,
    hasMore: true,
    startDate: '',
    endDate: '',
    refreshing: false
  },

  onLoad() {
    this.loadList()
  },

  async loadList(append = false) {
    const params = { page: this.data.page, size: this.data.size }
    if (this.data.startDate) params.startDate = this.data.startDate
    if (this.data.endDate) params.endDate = this.data.endDate

    try {
      const res = await api.get('/api/lottery/list', params)
      const newList = res.data.list || []
      const list = append ? [...this.data.list, ...newList] : newList
      this.setData({
        list,
        hasMore: newList.length >= this.data.size
      })
    } catch (e) {
      // 错误已在 api.js 处理
    }
  },

  loadMore() {
    if (!this.data.hasMore) return
    this.setData({ page: this.data.page + 1 }, () => {
      this.loadList(true)
    })
  },

  doFilter() {
    this.setData({ page: 1 }, () => {
      this.loadList()
    })
  },

  onStartDateChange(e) {
    this.setData({ startDate: e.detail.value })
  },

  onEndDateChange(e) {
    this.setData({ endDate: e.detail.value })
  },

  showDetail(e) {
    const item = e.currentTarget.dataset.item
    wx.showModal({
      title: '第 ' + item.issue + ' 期详情',
      content: '和值：' + (item.sumVal || '-') +
               '\n跨度：' + (item.rangeVal || '-') +
               '\n区间比：' + (item.zoneRatio || '-') +
               '\n奇偶比：' + (item.oddEvenRatio || '-'),
      showCancel: false
    })
  },

  onRefresh() {
    this.setData({ refreshing: true, page: 1 })
    this.loadList().finally(() => {
      this.setData({ refreshing: false })
    })
  }
})
