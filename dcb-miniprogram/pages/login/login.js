const auth = require('../../utils/auth')
const app = getApp()

Page({
  data: {
    loading: false
  },

  onLoad() {
    // 已登录直接跳转首页
    if (app.globalData.token) {
      wx.switchTab({ url: '/pages/index/index' })
    }
  },

  async handleLogin() {
    if (this.data.loading) return
    this.setData({ loading: true })

    try {
      await auth.wxLogin()
      wx.switchTab({ url: '/pages/index/index' })
    } catch (e) {
      wx.showToast({ title: e.message || '登录失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  }
})
