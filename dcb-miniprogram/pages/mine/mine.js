const app = getApp()

Page({
  data: {
    userInfo: {},
    subscribeStatus: { text: '未订阅', desc: '订阅解锁全部功能' }
  },

  onShow() {
    if (!app.checkLogin()) return
    const user = app.globalData.userInfo || {}
    this.setData({ userInfo: user })

    // 检查订阅状态
    if (user.subscribeExpireAt) {
      const expire = new Date(user.subscribeExpireAt)
      const now = new Date()
      if (expire > now) {
        const days = Math.ceil((expire - now) / (1000 * 60 * 60 * 24))
        this.setData({
          subscribeStatus: {
            text: '已订阅',
            desc: '到期时间：' + expire.toLocaleDateString() + '（剩' + days + '天）'
          }
        })
      }
    }
  },

  goSubscribe() {
    wx.navigateTo({ url: '/pages/subscribe/subscribe' })
  },

  goPage(e) {
    const page = e.currentTarget.dataset.page
    const tabPages = ['purchase', 'recommend']
    if (tabPages.includes(page)) {
      wx.switchTab({ url: '/pages/' + page + '/' + page })
    } else {
      wx.navigateTo({ url: '/pages/' + page + '/' + page })
    }
  },

  handleLogout() {
    wx.showModal({
      title: '提示',
      content: '确定要退出登录吗？',
      success(res) {
        if (res.confirm) {
          app.logout()
        }
      }
    })
  }
})
