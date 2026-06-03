// 微信登录模块
const api = require('./api')
const app = getApp()

/** 微信一键登录 */
function wxLogin() {
  return new Promise((resolve, reject) => {
    // 1. 调用 wx.login 获取临时 code
    wx.login({
      success(loginRes) {
        if (!loginRes.code) {
          reject(new Error('获取登录凭证失败'))
          return
        }

        // 2. 获取用户昵称和头像（新版小程序需用户主动填写）
        wx.getUserProfile({
          desc: '用于完善用户资料',
          success(profileRes) {
            const userInfo = profileRes.userInfo
            // 3. 将 code + 用户信息发送到后端
            api.post('/api/auth/wx-login', {
              code: loginRes.code,
              nickname: userInfo.nickName,
              avatar: userInfo.avatarUrl
            }).then(res => {
              const token = res.data.token
              const user = {
                nickname: res.data.nickname,
                role: res.data.role,
                subscribeExpireAt: res.data.subscribeExpireAt
              }
              app.saveLogin(token, user)
              resolve(user)
            }).catch(reject)
          },
          fail() {
            // 用户拒绝授权，仍可登录但无昵称头像
            api.post('/api/auth/wx-login', {
              code: loginRes.code,
              nickname: '微信用户',
              avatar: ''
            }).then(res => {
              const token = res.data.token
              const user = {
                nickname: res.data.nickname,
                role: res.data.role,
                subscribeExpireAt: res.data.subscribeExpireAt
              }
              app.saveLogin(token, user)
              resolve(user)
            }).catch(reject)
          }
        })
      },
      fail(err) {
        reject(new Error('微信登录失败：' + err.errMsg))
      }
    })
  })
}

module.exports = { wxLogin }
