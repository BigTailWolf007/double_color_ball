const BASE_URL = 'http://localhost:8080'

async function request(method, path, options = {}) {
  const url = BASE_URL + path
  const config = {
    method,
    headers: {},
    ...options
  }

  // 自动附加 JWT Token
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['Authorization'] = 'Bearer ' + token
  }

  if (options.params) {
    const qs = new URLSearchParams()
    Object.entries(options.params).forEach(([k, v]) => {
      if (v !== null && v !== undefined && v !== '') qs.append(k, v)
    })
    const q = qs.toString()
    if (q) config.url = url + '?' + q
  }

  if (options.body && !(options.body instanceof FormData)) {
    config.headers['Content-Type'] = 'application/json'
    config.body = JSON.stringify(options.body)
  }

  const res = await fetch(config.url || url, config)

  // 401 自动跳转登录页
  if (res.status === 401) {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    if (document.getElementById('login-username')) return // 已在登录页
    location.reload()
    return
  }

  const data = await res.json()

  if (data.code !== 200) {
    const msg = data.message || data.msg || '请求失败'
    toast(msg, 'error')
    throw new Error(msg)
  }

  return data
}

const api = {
  baseUrl: BASE_URL,
  get: (path, params) => request('GET', path, { params }),
  post: (path, body) => request('POST', path, { body }),
  put: (path, body) => request('PUT', path, { body }),
  postForm: (path, formData) => request('POST', path, { body: formData }),
  delete: (path) => request('DELETE', path),
}
