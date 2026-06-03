const api = require('../../utils/api')
const app = getApp()

Page({
  data: {
    plans: [
      { id: 1, name: '月卡', price: 9.9, originalPrice: 19.9, desc: '30天全功能使用' },
      { id: 2, name: '季卡', price: 24.9, originalPrice: 49.9, desc: '90天全功能使用，省15元' },
      { id: 3, name: '年卡', price: 79.9, originalPrice: 169.9, desc: '365天全功能使用，省90元' }
    ],
    benefits: [
      '无限次查看历史开奖数据',
      '智能推荐号码无限制',
      '预测号码无限制',
      '购买记录无限制',
      '专属数据分析报告'
    ],
    selectedPlan: null
  },

  onShow() {
    if (!app.checkLogin()) return
  },

  selectPlan(e) {
    const id = parseInt(e.currentTarget.dataset.id)
    this.setData({ selectedPlan: id })
  },

  computed: {
    selectedPlanPrice() {
      const plan = this.data.plans.find(p => p.id === this.data.selectedPlan)
      return plan ? plan.price : 0
    }
  },

  async handlePay() {
    if (!this.data.selectedPlan) return

    wx.showLoading({ title: '下单中...' })
    try {
      const res = await api.post('/api/payment/order', {
        planId: this.data.selectedPlan
      })

      const payParams = res.data
      // 调起微信支付
      wx.requestPayment({
        timeStamp: payParams.timeStamp,
        nonceStr: payParams.nonceStr,
        package: payParams.package,
        signType: payParams.signType || 'MD5',
        paySign: payParams.paySign,
        success() {
          wx.showToast({ title: '订阅成功', icon: 'success' })
          setTimeout(() => {
            wx.switchTab({ url: '/pages/mine/mine' })
          }, 1500)
        },
        fail(err) {
          if (err.errMsg.indexOf('cancel') === -1) {
            wx.showToast({ title: '支付失败', icon: 'none' })
          }
        }
      })
    } catch (e) {
      // 错误已处理
    } finally {
      wx.hideLoading()
    }
  }
})
