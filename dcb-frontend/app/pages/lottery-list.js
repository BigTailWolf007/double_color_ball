const LotteryList = (() => {
  let state = { page: 1, size: 20, total: 0, issue: '', startDate: '', endDate: '', loading: false }
  const redOptions = Array.from({ length: 33 }, (_, i) => i + 1)
  const blueOptions = Array.from({ length: 16 }, (_, i) => i + 1)
  let formReds = [], formBlue = null

  function render() {
    document.getElementById('main-content').innerHTML = `
      <div class="card" style="flex:1;display:flex;flex-direction:column;min-height:0;">
        <div class="card-header">
          <span>开奖号码列表</span>
          <div class="card-header-actions">
            <button class="btn btn-primary" id="btn-sync">同步开奖信息</button>
            <button class="btn btn-primary" id="btn-add">手动录入</button>
          </div>
        </div>
        <div class="card-body" style="flex:1;display:flex;flex-direction:column;min-height:0;overflow:hidden;">
          <div class="filter-bar">
            <label>期号</label>
            <input class="form-input" id="q-issue" placeholder="请输入期号" value="${state.issue}" style="width:160px;" />
            <label>开始日期</label>
            <input class="form-input" id="q-start" type="date" value="${state.startDate}" />
            <label>结束日期</label>
            <input class="form-input" id="q-end" type="date" value="${state.endDate}" />
            <button class="btn btn-primary" id="btn-search">查询</button>
            <button class="btn btn-default" id="btn-reset">重置</button>
          </div>
          <div class="table-scroll" style="flex:1;min-height:0;">
            <table>
              <thead>
                <tr>
                  <th style="width:100px;">期号</th><th style="width:110px;">开奖日期</th><th style="width:160px;">号码</th><th style="width:170px;">奖金详情</th><th style="width:110px;">销售额</th><th style="width:110px;">奖池金额</th><th style="width:110px;">录入时间</th><th style="width:80px;">操作</th>
                </tr>
              </thead>
              <tbody id="table-body"><tr><td colspan="8" class="text-center" style="color:#909399;">加载中...</td></tr></tbody>
            </table>
          </div>
          <div class="pagination" id="pagination"></div>
        </div>
      </div>`

    document.getElementById('btn-add').addEventListener('click', openAddDialog)
    document.getElementById('btn-sync').addEventListener('click', openSyncDialog)
    document.getElementById('btn-search').addEventListener('click', () => {
      state.issue = document.getElementById('q-issue').value.trim()
      state.startDate = document.getElementById('q-start').value
      state.endDate = document.getElementById('q-end').value
      state.page = 1
      fetchList()
    })
    document.getElementById('btn-reset').addEventListener('click', () => {
      state.issue = ''; state.startDate = ''; state.endDate = ''; state.page = 1
      document.getElementById('q-issue').value = ''
      document.getElementById('q-start').value = ''
      document.getElementById('q-end').value = ''
      fetchList()
    })

    renderIssueInput('q-issue', '/api/lottery/issue-suggest', val => { state.issue = val })

    fetchList()
  }

  async function fetchList() {
    const tbody = document.getElementById('table-body')
    if (!tbody) return
    tbody.innerHTML = '<tr><td colspan="8" class="text-center" style="color:#909399;">加载中...</td></tr>'
    try {
      const params = { page: state.page, size: state.size }
      if (state.issue) params.issue = state.issue
      if (state.startDate) params.startDate = state.startDate
      if (state.endDate) params.endDate = state.endDate
      const res = await api.get('/api/lottery/list', params)
      const list = res.data.list || []
      state.total = res.data.total || 0

      if (!list.length) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center" style="color:#909399;">暂无数据</td></tr>'
      } else {
        tbody.innerHTML = list.map(row => {
          const prizeHtml = renderPrizeText(row.prizeText)
          return `
          <tr>
            <td>${row.issue}</td>
            <td>${row.drawDate || '-'}</td>
            <td>${renderReds(row.reds)}${renderBlue(row.blue)}</td>
            <td style="font-size:12px;">${prizeHtml}</td>
            <td style="text-align:right;">${row.saleAmount ? '¥' + row.saleAmount : '-'}</td>
            <td style="text-align:right;">${row.poolAmount ? '¥' + row.poolAmount : '-'}</td>
            <td>${row.createdAt || '-'}</td>
            <td><button class="btn btn-link btn-danger btn-sm" data-id="${row.id}" data-issue="${row.issue}">删除</button></td>
          </tr>`
        }).join('')

        tbody.querySelectorAll('[data-id]').forEach(btn => {
          btn.addEventListener('click', () => handleDelete(btn.dataset.id, btn.dataset.issue))
        })
      }

      renderPagination('pagination', state.page, state.size, state.total, (p, s) => {
        state.page = p; state.size = s; fetchList()
      })
    } catch (e) {
      if (tbody) tbody.innerHTML = '<tr><td colspan="8" class="text-center" style="color:#f56c6c;">加载失败</td></tr>'
    }
  }

  /** 渲染奖金详情：过长时截断，点击展开 */
  function renderPrizeText(text) {
    if (!text) return '<span style="color:#909399;">-</span>'
    // 按分号换行，每条奖金独立一行
    const lines = text.split('；')
    return '<span style="font-size:12px;line-height:1.6;">' + lines.map(l => escapeHtml(l)).join('<br>') + '</span>'
  }

  function escapeHtml(str) {
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
  }

  async function handleDelete(id, issue) {
    try {
      await confirm(`确认删除期号 ${issue} 的开奖号码？`)
      await api.delete(`/api/lottery/${id}`)
      toast('删除成功')
      fetchList()
    } catch (e) {}
  }

  /** ===== 同步开奖信息弹窗 ===== */
  async function openSyncDialog() {
    // 获取最新期号作为默认值
    let defaultIssue = ''
    try {
      const res = await api.get('/api/lottery/issue-suggest', { q: '' })
      if (res.data && res.data.length > 0) {
        // 最新期号 +1
        const latest = parseInt(res.data[0])
        if (!isNaN(latest)) {
          defaultIssue = String(latest + 1)
        }
      }
    } catch (e) { /* 忽略 */ }

    const bodyHtml = `
      <div class="form-group" style="margin-bottom:12px;">
        <label>期号 <span style="color:#f56c6c;">*</span></label>
        <input class="form-input" id="sync-issue" placeholder="如：2026061" value="${defaultIssue}" style="width:100%;" />
        <div style="color:#909399;font-size:12px;margin-top:4px;">将从外部彩票接口拉取该期号的开奖信息</div>
      </div>`

    const footerHtml = `
      <button class="btn btn-default" id="sync-cancel">取消</button>
      <button class="btn btn-primary" id="sync-confirm">确认同步</button>`

    openModal('同步开奖信息', bodyHtml, footerHtml)

    // 期号下拉提示
    renderIssueInput('sync-issue', '/api/lottery/issue-suggest', () => {})

    document.getElementById('sync-cancel').addEventListener('click', closeModal)
    document.getElementById('sync-confirm').addEventListener('click', handleSync)
  }

  async function handleSync() {
    const issue = document.getElementById('sync-issue').value.trim()
    if (!issue) { toast('请输入期号', 'warning'); return }

    const confirmBtn = document.getElementById('sync-confirm')
    confirmBtn.disabled = true
    confirmBtn.textContent = '同步中...'

    try {
      const res = await api.post('/api/lottery/sync', { issue })
      const newRecord = res.data && res.data.newRecord
      const mode = res.data && res.data.calcMode === 'async' ? '（已提交后台异步计算）' : ''
      toast((newRecord ? '同步成功（新增记录）' : '同步成功（已更新）') + mode)
      closeModal()
      fetchList()
    } catch (e) {
      confirmBtn.disabled = false
      confirmBtn.textContent = '确认同步'
    }
  }

  /** ===== 手动录入弹窗 ===== */
  function openAddDialog() {
    formReds = []; formBlue = null
    const bodyHtml = `
      <div class="form-group" style="margin-bottom:12px;">
        <label>期号 <span style="color:#f56c6c;">*</span></label>
        <input class="form-input" id="add-issue" placeholder="如：2024001" style="width:100%;" />
      </div>
      <div class="form-group" style="margin-bottom:12px;">
        <label>开奖日期</label>
        <input class="form-input" id="add-date" type="date" style="width:100%;" />
      </div>
      <div class="form-group" style="margin-bottom:12px;">
        <label>红球 <span style="color:#f56c6c;">*</span></label>
        <div class="ball-picker" id="red-picker"></div>
        <div class="ball-count" id="red-count">已选 0/6 个</div>
      </div>
      <div class="form-group">
        <label>蓝球 <span style="color:#f56c6c;">*</span></label>
        <div class="ball-picker" id="blue-picker"></div>
      </div>`

    const footerHtml = `
      <button class="btn btn-default" id="add-cancel">取消</button>
      <button class="btn btn-primary" id="add-confirm">确认录入</button>`

    openModal('手动录入开奖号码', bodyHtml, footerHtml)

    renderBallPicker('red-picker', redOptions, formReds, 6, 'tag-danger', (reds) => {
      formReds = reds
      const el = document.getElementById('red-count')
      if (el) el.textContent = `已选 ${formReds.length}/6 个`
    })
    renderBallPicker('blue-picker', blueOptions, formBlue, 1, 'tag-primary', (n) => { formBlue = n })

    document.getElementById('add-cancel').addEventListener('click', closeModal)
    document.getElementById('add-confirm').addEventListener('click', handleAdd)
  }

  async function handleAdd() {
    const issue = document.getElementById('add-issue').value.trim()
    const drawDate = document.getElementById('add-date').value
    if (!issue) { toast('期号不能为空', 'warning'); return }
    if (formReds.length !== 6) { toast('请选择6个红球', 'warning'); return }
    if (!formBlue) { toast('请选择蓝球', 'warning'); return }
    const sorted = [...formReds].sort((a, b) => a - b)
    try {
      await api.post('/api/lottery/add', {
        issue, drawDate: drawDate || null,
        red1: sorted[0], red2: sorted[1], red3: sorted[2],
        red4: sorted[3], red5: sorted[4], red6: sorted[5],
        blue: formBlue
      })
      // 录入开奖号码后触发该期购买记录的盈亏计算
      api.post(`/api/purchase/calc/${issue}`).catch(() => {})
      toast('录入成功')
      closeModal()
      fetchList()
    } catch (e) {}
  }

  return { render }
})()
