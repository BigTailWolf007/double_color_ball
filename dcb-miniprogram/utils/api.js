// API 请求封装
const app = getApp()

/** 基础请求方法 */
function request(method, path, data = {}) {
  return new Promise((resolve, reject) => {
    const token = app.globalData.token
    const header = {
      'Content-Type': 'application/json'
    }
    if (token) {
      header['Authorization'] = 'Bearer ' + token
    }

    wx.request({
      url: app.globalData.baseUrl + path,
      method: method,
      data: method === 'GET' ? undefined : data,
      header: header,
      success(res) {
        // 401 未授权
        if (res.statusCode === 401) {
          app.logout()
          reject(new Error('登录已过期，请重新登录'))
          return
        }

        const body = res.data
        if (body.code !== 200) {
          wx.showToast({
            title: body.message || '请求失败',
            icon: 'none'
          })
          reject(new Error(body.message || '请求失败'))
          return
        }
        resolve(body)
      },
      fail(err) {
        wx.showToast({
          title: '网络异常，请稍后重试',
          icon: 'none'
        })
        reject(err)
      }
    })
  })
}

module.exports = {
  get(path, params = {}) {
    // 将 params 拼接到 URL
    const qs = Object.keys(params)
      .filter(k => params[k] !== null && params[k] !== undefined && params[k] !== '')
      .map(k => k + '=' + encodeURIComponent(params[k]))
      .join('&')
    const url = qs ? path + '?' + qs : path
    return request('GET', url)
  },
  post(path, data = {}) {
    return request('POST', path, data)
  },
  put(path, data = {}) {
    return request('PUT', path, data)
  },
  del(path, data = {}) {
    return request('DELETE', path, data)
  }
}
