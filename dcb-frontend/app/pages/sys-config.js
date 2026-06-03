const SysConfig = (() => {
  const groupLabels = {
    'API': '🔗 接口配置',
    'SYSTEM': '⚙️ 系统参数',
    'SCHEDULE': '⏰ 定时任务',
    'UPLOAD': '📁 文件上传',
    'CACHE': '💾 缓存策略',
    'AUTH': '🔐 认证配置'
  }

  const typeHints = {
    'INT': '请输入整数',
    'CRON': '格式：秒 分 时 日 月 周（6段空格分隔）',
    'STRING': ''
  }

  let state = { data: {}, loading: false }

  function render() {
    document.getElementById('main-content').innerHTML = `
      <div class="card" style="flex:1;display:flex;flex-direction:column;min-height:0;">
        <div class="card-header">
          <span>系统配置管理</span>
          <span style="font-size:12px;color:#909399;">修改后即时生效，无需重启</span>
        </div>
        <div class="card-body" style="flex:1;overflow:auto;" id="config-container">
          <div class="text-center" style="color:#909399;padding:40px;">加载中...</div>
        </div>
      </div>`

    fetchList()
  }

  async function fetchList() {
    try {
      const res = await api.get('/api/config/list')
      state.data = res.data || {}
      renderCards()
    } catch (e) {
      document.getElementById('config-container').innerHTML =
        '<div class="text-center" style="color:#f56c6c;padding:40px;">加载失败</div>'
    }
  }

  function renderCards() {
    const container = document.getElementById('config-container')
    if (!container) return

    const groups = Object.keys(groupLabels)
    let html = ''

    groups.forEach(group => {
      const items = state.data[group]
      if (!items || !items.length) return

      html += `<div class="config-card">
        <div class="config-card-title">${groupLabels[group] || group}</div>`

      items.forEach(item => {
        const hint = typeHints[item.configType] || ''
        html += `
        <div class="config-row">
          <div class="config-info">
            <span class="config-key">${item.configKey}</span>
            <span class="config-desc">${item.configDesc || ''}</span>
          </div>
          <div class="config-input-wrap">
            <input class="form-input config-input" id="cfg-${item.configKey}"
                   value="${escapeAttr(item.configValue)}"
                   type="${item.configType === 'INT' ? 'number' : 'text'}"
                   placeholder="${hint}" />
            <button class="btn btn-primary btn-sm config-save" data-key="${item.configKey}">保存</button>
          </div>
          ${item.updatedAt ? `<span class="config-updated">${item.updatedAt}</span>` : ''}
        </div>`
      })

      html += '</div>'
    })

    container.innerHTML = html || '<div class="text-center" style="color:#909399;padding:40px;">暂无配置数据</div>'

    // 绑定保存按钮
    container.querySelectorAll('.config-save').forEach(btn => {
      btn.addEventListener('click', () => handleSave(btn.dataset.key))
    })
  }

  function escapeAttr(str) {
    return String(str).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  }

  async function handleSave(key) {
    const input = document.getElementById('cfg-' + key)
    if (!input) return
    const value = input.value.trim()
    if (!value) { toast('配置值不能为空', 'warning'); return }

    const btn = document.querySelector(`.config-save[data-key="${key}"]`)
    btn.disabled = true
    btn.textContent = '保存中...'

    try {
      await api.put('/api/config/' + key, { value })
      toast(key + ' 已更新为 ' + value)
      fetchList()
    } catch (e) {
      btn.disabled = false
      btn.textContent = '保存'
    }
  }

  return { render }
})()
