const api = require('../../utils/api')

Page({
  data: {
    pageSize: 10,
    recommends: []
  },

  onPageSizeInput(e) {
    this.setData({ pageSize: parseInt(e.detail.value) || 10 })
  },

  async generate() {
    wx.showLoading({ title: '生成中...' })
    try {
      const res = await api.post('/api/recommend/generate', {
        pageSize: this.data.pageSize
      })
      const data = res.data
      if (data && data.list) {
        this.setData({ recommends: data.list })
      }
    } catch (e) {
      // 错误已处理
    } finally {
      wx.hideLoading()
    }
  },

  async adopt(e) {
    const { reds, blue } = e.currentTarget.dataset
    if (!reds || reds.length < 6) return
    try {
      await api.post('/api/purchase/add', [{
        issue: '',
        red1: reds[0], red2: reds[1], red3: reds[2],
        red4: reds[3], red5: reds[4], red6: reds[5],
        blue: blue,
        quantity: 1
      }])
      wx.showToast({ title: '已采纳', icon: 'success' })
    } catch (e) {
      // 错误已处理
    }
  }
})
