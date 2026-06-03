/**
 * 会话管理模块
 * - 使用 sessionStorage 存储 token（关闭浏览器自动清除）
 * - 使用 BroadcastChannel 实现跨标签页登录同步
 */

const Session = (() => {
  const TOKEN_KEY = 'token'
  const USER_KEY = 'user'
  let channel = null

  // 初始化 BroadcastChannel（跨标签页通信）
  try {
    channel = new BroadcastChannel('dcb-session')
    channel.onmessage = (e) => {
      if (e.data.type === 'login') {
        // 其他标签页登录，同步 token 到本标签页
        sessionStorage.setItem(TOKEN_KEY, e.data.token)
        sessionStorage.setItem(USER_KEY, e.data.user)
      } else if (e.data.type === 'logout') {
        // 其他标签页退出，本标签页也清除
        sessionStorage.removeItem(TOKEN_KEY)
        sessionStorage.removeItem(USER_KEY)
      }
    }
  } catch (e) {
    // BroadcastChannel 不支持时降级，仅使用 sessionStorage
  }

  function store(token, userJson) {
    sessionStorage.setItem(TOKEN_KEY, token)
    sessionStorage.setItem(USER_KEY, userJson)
    // 广播到其他标签页
    if (channel) {
      channel.postMessage({ type: 'login', token: token, user: userJson })
    }
  }

  function clear() {
    sessionStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(USER_KEY)
    // 广播到其他标签页
    if (channel) {
      channel.postMessage({ type: 'logout' })
    }
  }

  function getToken() {
    return sessionStorage.getItem(TOKEN_KEY)
  }

  function getUser() {
    try {
      return JSON.parse(sessionStorage.getItem(USER_KEY) || '{}')
    } catch (e) {
      return {}
    }
  }

  return { store, clear, getToken, getUser }
})()
