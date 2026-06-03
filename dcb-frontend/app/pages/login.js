const LoginPage = (() => {
  function render() {
    document.body.innerHTML = `
      <div style="display:flex;align-items:center;justify-content:center;height:100vh;background:#f0f2f5;">
        <div class="login-card">
          <h2 style="text-align:center;margin-bottom:24px;color:#303133;">双色球分析系统</h2>
          <div class="form-group">
            <label>用户名</label>
            <input class="form-input" id="login-username" placeholder="请输入用户名" style="width:100%;" />
          </div>
          <div class="form-group" style="margin-top:16px;">
            <label>密码</label>
            <input class="form-input" id="login-password" type="password" placeholder="请输入密码" style="width:100%;" />
          </div>
          <div id="login-error" style="color:#f56c6c;font-size:13px;margin-top:8px;display:none;"></div>
          <button class="btn btn-primary" id="login-btn" style="width:100%;margin-top:20px;">登 录</button>
        </div>
      </div>`

    document.getElementById('login-btn').addEventListener('click', handleLogin)
    document.getElementById('login-password').addEventListener('keydown', e => {
      if (e.key === 'Enter') handleLogin()
    })
  }

  async function handleLogin() {
    const username = document.getElementById('login-username').value.trim()
    const password = document.getElementById('login-password').value.trim()
    const errEl = document.getElementById('login-error')
    if (!username || !password) {
      errEl.textContent = '请输入用户名和密码'
      errEl.style.display = 'block'
      return
    }

    const btn = document.getElementById('login-btn')
    btn.disabled = true
    btn.textContent = '登录中...'

    try {
      const res = await api.post('/api/auth/login', { username, password })
      if (res.code === 200) {
        localStorage.setItem('token', res.data.token)
        localStorage.setItem('user', JSON.stringify({ nickname: res.data.nickname, role: res.data.role }))
        location.reload()
      }
    } catch (e) {
      errEl.textContent = e.message || '登录失败'
      errEl.style.display = 'block'
      btn.disabled = false
      btn.textContent = '登 录'
    }
  }

  return { render }
})()
