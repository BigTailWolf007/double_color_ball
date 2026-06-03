// 双色球分析小程序 — 全局入口
App({
  globalData: {
    token: null,
    userInfo: null,
    baseUrl: 'http://localhost:8080' // 开发环境，上线改为 HTTPS 域名
  },

  onLaunch() {
    // 检查本地缓存的 token
    const token = wx.getStorageSync('token')
    if (token) {
      this.globalData.token = token
      this.globalData.userInfo = wx.getStorageSync('userInfo') || null
    }
  },

  /** 检查登录状态，未登录则跳转登录页 */
  checkLogin() {
    if (!this.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return false
    }
    return true
  },

  /** 保存登录信息 */
  saveLogin(token, userInfo) {
    this.globalData.token = token
    this.globalData.userInfo = userInfo
    wx.setStorageSync('token', token)
    wx.setStorageSync('userInfo', userInfo)
  },

  /** 退出登录 */
  logout() {
    this.globalData.token = null
    this.globalData.userInfo = null
    wx.removeStorageSync('token')
    wx.removeStorageSync('userInfo')
    wx.redirectTo({ url: '/pages/login/login' })
  }
})
