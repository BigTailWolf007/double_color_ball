const PredictList = (() => {
  let state = { page: 1, size: 20, total: 0, issue: '', userId: null }
  let selectedIds = new Set()
  let isAdmin = false
  let currentUser = null

  function getLoginUserId() {
    const user = Session.getUser()
    return (user && user.id) ? user.id : null
  }

  function render() {
    isAdmin = (Session.getUser().role || '').toUpperCase() === 'ADMIN'
    currentUser = Session.getUser()
    // 默认勾选当前用户
    if (state.userId === null && currentUser && currentUser.id) {
      state.userId = currentUser.id
    }
    selectedIds = new Set()
    const userCol = isAdmin
    document.getElementById('main-content').innerHTML = `
      <div class="card" style="flex:1;display:flex;flex-direction:column;min-height:0;">
        <div class="card-header">
          <span>预测号码列表</span>
          <div style="display:flex;gap:8px;">
            <button class="btn btn-success" id="btn-export">导出 TXT</button>
            <button class="btn btn-primary" id="btn-sync-issue">按期号同步购买</button>
            <button class="btn btn-primary" id="btn-sync-selected" disabled>同步勾选到购买</button>
            <button class="btn btn-warning" id="btn-calc">重新计算</button>
            <button class="btn btn-danger" id="btn-clear">按期号清除</button>
          </div>
        </div>
        <div class="card-body" style="flex:1;display:flex;flex-direction:column;min-height:0;overflow:hidden;">
          <div class="filter-bar">
            <label>目标期号</label>
            <input class="form-input" id="q-issue" placeholder="请输入期号" value="${state.issue}" style="width:160px;" />
            ${isAdmin ? '<label>用户</label><div style="position:relative;"><input class="form-input" id="q-user" placeholder="输入用户名搜索" value="' + (currentUser.nickname || currentUser.username || '') + '" style="width:140px;" /></div>' : ''}
            <button class="btn btn-primary" id="btn-search">查询</button>
            <button class="btn btn-default" id="btn-reset">重置</button>
          </div>
          <div class="table-scroll" style="flex:1;min-height:0;">
            <table>
              <thead>
                <tr>
                  <th style="width:36px;"><input type="checkbox" id="check-all" title="全选/取消" /></th>
                  <th>目标期号</th><th>号码</th>
                  ${isAdmin ? '<th class="text-center">用户</th>' : ''}
                  <th class="text-center">和值</th><th class="text-center">跨度</th><th class="text-center">区间比</th><th class="text-center">奇偶比</th>
                  <th class="text-center">命中红球</th><th class="text-center">命中蓝球</th>
                  <th class="text-center">命中等级</th><th>生成时间</th><th>操作</th>
                </tr>
              </thead>
              <tbody id="table-body"><tr><td colspan="12" class="text-center" style="color:#909399;">加载中...</td></tr></tbody>
            </table>
          </div>
          <div class="pagination" id="pagination" style="margin-top:12px;"></div>
        </div>
      </div>`

    document.getElementById('btn-search').addEventListener('click', () => {
      state.issue = document.getElementById('q-issue').value.trim()
      state.page = 1
      fetchList()
    })
    document.getElementById('btn-reset').addEventListener('click', () => {
      state.issue = ''; state.page = 1
      if (isAdmin) {
        const uid = currentUser && currentUser.id ? currentUser.id : null
        state.userId = uid
      }
      document.getElementById('q-issue').value = ''
      if (isAdmin) {
        const uEl = document.getElementById('q-user')
        if (uEl) uEl.value = (currentUser.nickname || currentUser.username || '')
      }
      fetchList()
    })
    document.getElementById('btn-calc').addEventListener('click', handleCalc)
    document.getElementById('btn-clear').addEventListener('click', handleDeleteByIssue)
    document.getElementById('btn-export').addEventListener('click', handleExport)
    document.getElementById('btn-sync-issue').addEventListener('click', handleSyncByIssue)
    document.getElementById('btn-sync-selected').addEventListener('click', handleSyncSelected)
    document.getElementById('check-all').addEventListener('change', () => {
      const tbody = document.getElementById('table-body')
      const checkAll = document.getElementById('check-all')
      if (!tbody || !checkAll) return
      tbody.querySelectorAll('.row-check').forEach(cb => {
        cb.checked = checkAll.checked
        const id = Number(cb.dataset.id)
        checkAll.checked ? selectedIds.add(id) : selectedIds.delete(id)
      })
      updateSyncBtn()
    })

    renderIssueInput('q-issue', '/api/predict/issue-suggest', val => { state.issue = val })
    if (isAdmin) {
      renderUserInput('q-user', '/api/predict/user-suggest', uid => { state.userId = uid })
    }

    fetchList()
  }

  function updateSyncBtn() {
    const btn = document.getElementById('btn-sync-selected')
    if (btn) btn.disabled = selectedIds.size === 0
  }

  async function fetchList() {
    const tbody = document.getElementById('table-body')
    if (!tbody) return
    tbody.innerHTML = `<tr><td colspan="${isAdmin ? 13 : 12}" class="text-center" style="color:#909399;">加载中...</td></tr>`
    try {
      const params = { page: state.page, size: state.size }
      if (state.issue) params.issue = state.issue
      if (state.userId) params.userId = state.userId
      const res = await api.get('/api/predict/list', params)
      const list = res.data.list || []
      state.total = res.data.total || 0

      if (!list.length) {
        tbody.innerHTML = `<tr><td colspan="${isAdmin ? 13 : 12}" class="text-center" style="color:#909399;">暂无数据</td></tr>`
      } else {
        tbody.innerHTML = list.map(row => {
          let hitRedHtml, hitBlueHtml
          if (row.hitRed !== null && row.hitRed !== undefined) {
            hitRedHtml = `${row.hitRed} 个`
          } else {
            hitRedHtml = '<span class="tag tag-info">待开奖</span>'
          }
          if (row.hitBlue !== null && row.hitBlue !== undefined) {
            hitBlueHtml = `<span class="tag tag-${row.hitBlue ? 'primary' : 'info'}">${row.hitBlue ? '是' : '否'}</span>`
          } else {
            hitBlueHtml = '<span class="tag tag-info">待开奖</span>'
          }
          const checked = selectedIds.has(row.id) ? 'checked' : ''
          const userCol = isAdmin ? `<td class="text-center">${row.username || '-'}</td>` : ''
          return `<tr>
            <td style="text-align:center;"><input type="checkbox" class="row-check" data-id="${row.id}" ${checked} /></td>
            <td>${row.issue}</td>
            <td>${renderBalls(row.reds, row.blue, row.drawReds, row.drawBlue)}</td>
            ${userCol}
            <td class="text-center">${row.sumVal ?? '-'}</td>
            <td class="text-center">${row.rangeVal ?? '-'}</td>
            <td class="text-center">${row.zoneRatio ?? '-'}</td>
            <td class="text-center">${row.oddEvenRatio ?? '-'}</td>
            <td class="text-center">${hitRedHtml}</td>
            <td class="text-center">${hitBlueHtml}</td>
            <td class="text-center">${renderPrizeLevel(row.prizeLevel, row.prizeLevelDesc)}</td>
            <td>${row.createdAt || '-'}</td>
            <td><button class="btn btn-link btn-danger btn-sm" data-id="${row.id}">删除</button></td>
          </tr>`
        }).join('')

        tbody.querySelectorAll('.row-check').forEach(cb => {
          cb.addEventListener('change', () => {
            const id = Number(cb.dataset.id)
            cb.checked ? selectedIds.add(id) : selectedIds.delete(id)
            syncCheckAll()
            updateSyncBtn()
          })
        })

        tbody.querySelectorAll('[data-id]:not(.row-check)').forEach(btn => {
          btn.addEventListener('click', () => handleDeleteById(btn.dataset.id))
        })

        syncCheckAll()
      }

      renderPagination('pagination', state.page, state.size, state.total, (p, s) => {
        state.page = p; state.size = s; fetchList()
      })
    } catch (e) {
      if (tbody) tbody.innerHTML = `<tr><td colspan="${isAdmin ? 13 : 12}" class="text-center" style="color:#f56c6c;">加载失败</td></tr>`
    }
  }

  function syncCheckAll() {
    const checkAll = document.getElementById('check-all')
    const tbody = document.getElementById('table-body')
    if (!checkAll || !tbody) return
    const cbs = tbody.querySelectorAll('.row-check')
    if (!cbs.length) { checkAll.checked = false; checkAll.indeterminate = false; return }
    const checkedCount = [...cbs].filter(c => c.checked).length
    checkAll.checked = checkedCount === cbs.length
    checkAll.indeterminate = checkedCount > 0 && checkedCount < cbs.length
  }

  async function handleSyncByIssue() {
    let selectedIssue = ''

    openModal('选择同步期号',
      `<div style="display:flex;flex-direction:column;gap:12px;">
        <div>
          <label>期号</label>
          <div style="position:relative;">
            <input class="form-input" id="sync-issue" placeholder="请输入期号，如 2026061" style="width:100%;margin-top:4px;" />
          </div>
        </div>
      </div>`,
      `<button class="btn btn-default" id="sync-cancel">取消</button>
       <button class="btn btn-primary" id="sync-confirm">同步到购买记录</button>`
    )

    // 期号自动补全输入框
    renderIssueInput('sync-issue', '/api/predict/issue-suggest', val => { selectedIssue = val })

    document.getElementById('sync-cancel').addEventListener('click', closeModal)
    document.getElementById('sync-confirm').addEventListener('click', async () => {
      if (!selectedIssue) { toast('请选择期号', 'warning'); return }

      const btn = document.getElementById('sync-confirm')
      btn.disabled = true
      btn.textContent = '同步中...'

      try {
        const userId = getLoginUserId()
        if (!userId) { toast('无法获取用户ID，请重新登录', 'error'); return }
        const res = await api.post('/api/predict/sync-by-issues', { issues: [selectedIssue], userId: userId })
        toast(`同步完成:共同步 ${res.data} 条到购买记录`)
        closeModal()
      } catch (e) {
      } finally {
        btn.disabled = false
        btn.textContent = '同步到购买记录'
      }
    })
  }

  async function handleSyncSelected() {
    if (!selectedIds.size) return
    try {
      await confirm(`确认将已勾选的 ${selectedIds.size} 条预测号码同步到购买记录?`)
      const userId = getLoginUserId()
      if (!userId) { toast('无法获取用户ID，请重新登录', 'error'); return }
      const res = await api.post('/api/predict/sync-by-ids', { ids: [...selectedIds], userId: userId })
      toast(`同步完成:共同步 ${res.data} 条到购买记录`)
      selectedIds.clear()
      fetchList()
    } catch (e) {}
  }

  async function handleExport() {
    let selectedIssue = ''

    openModal('选择导出期号',
      `<div>
        <label>期号</label>
        <div style="position:relative;">
          <input class="form-input" id="export-issue" placeholder="请输入期号，如 2026061" style="width:100%;margin-top:4px;" />
        </div>
      </div>`,
      `<button class="btn btn-default" id="export-cancel">取消</button>
       <button class="btn btn-primary" id="export-download">下载</button>`
    )

    renderIssueInput('export-issue', '/api/predict/issue-suggest', val => { selectedIssue = val })

    document.getElementById('export-cancel').addEventListener('click', closeModal)
    document.getElementById('export-download').addEventListener('click', async () => {
      if (!selectedIssue) { toast('请选择期号', 'warning'); return }

      const btn = document.getElementById('export-download')
      btn.disabled = true
      btn.textContent = '导出中...'

      try {
        const token = Session.getToken()
        const userId = getLoginUserId()
        const headers = { 'Content-Type': 'application/json' }
        if (token) headers['Authorization'] = 'Bearer ' + token

        const response = await fetch(`${api.baseUrl}/api/predict/export`, {
          method: 'POST',
          headers: headers,
          body: JSON.stringify({ issues: [selectedIssue], userId: userId })
        })
        if (!response.ok) {
          const err = await response.json().catch(() => null)
          toast((err && err.message) || '导出失败', 'error')
          return
        }

        const blob = await response.blob()
        const blobUrl = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = blobUrl
        a.download = `预测号码_${selectedIssue}_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '')}.txt`
        a.click()
        setTimeout(() => URL.revokeObjectURL(blobUrl), 1000)
        closeModal()
        toast('导出成功')
      } catch (e) {
        toast('导出失败：' + (e.message || '网络错误'), 'error')
      } finally {
        btn.disabled = false
        btn.textContent = '下载'
      }
    })
  }

  async function handleDeleteById(id) {
    try {
      await confirm('确认删除该条预测记录?')
      await api.delete(`/api/predict/${id}`)
      selectedIds.delete(Number(id))
      toast('删除成功')
      fetchList()
    } catch (e) {}
  }

  async function handleDeleteByIssue() {
    let selectedIssue = ''

    openModal('按期号清除预测记录',
      `<div>
        <label>期号</label>
        <div style="position:relative;">
          <input class="form-input" id="clear-issue" placeholder="请输入期号，如 2026061" style="width:100%;margin-top:4px;" />
        </div>
      </div>`,
      `<button class="btn btn-default" id="clear-cancel">取消</button>
       <button class="btn btn-danger" id="clear-ok">确认清除</button>`
    )

    renderIssueInput('clear-issue', '/api/predict/issue-suggest', val => { selectedIssue = val })

    document.getElementById('clear-cancel').addEventListener('click', closeModal)
    document.getElementById('clear-ok').addEventListener('click', async () => {
      if (!selectedIssue) { toast('请选择期号', 'warning'); return }
      try {
        await confirm(`确认清除期号 ${selectedIssue} 的所有预测记录？`)
        const userId = getLoginUserId()
        if (!userId) { toast('无法获取用户ID，请重新登录', 'error'); return }
        const res = await api.delete(`/api/predict/issue/${selectedIssue}?userId=${userId}`)
        toast(`已清除期号 ${selectedIssue} 的 ${res.data} 条预测记录`)
        closeModal()
        fetchList()
      } catch (e) {}
    })
  }

  async function handleCalc() {
    let selectedIssue = ''

    openModal('重新计算命中结果',
      `<div>
        <label>期号</label>
        <div style="position:relative;">
          <input class="form-input" id="calc-issue" placeholder="请输入期号，如 2026061" style="width:100%;margin-top:4px;" />
        </div>
      </div>`,
      `<button class="btn btn-default" id="calc-cancel">取消</button>
       <button class="btn btn-primary" id="calc-ok">开始计算</button>`
    )

    renderIssueInput('calc-issue', '/api/predict/issue-suggest', val => { selectedIssue = val })

    document.getElementById('calc-cancel').addEventListener('click', closeModal)
    document.getElementById('calc-ok').addEventListener('click', async () => {
      if (!selectedIssue) { toast('请选择期号', 'warning'); return }
      const btn = document.getElementById('calc-ok')
      btn.disabled = true
      btn.textContent = '计算中...'
      try {
        await api.post(`/api/predict/calc/${selectedIssue}`)
        toast(`期号 ${selectedIssue} 已提交后台异步重新计算，稍后刷新查看结果`)
        closeModal()
        setTimeout(() => fetchList(), 3000)
      } catch (e) {
        btn.disabled = false
        btn.textContent = '开始计算'
      }
    })
  }

  return { render }
})()
