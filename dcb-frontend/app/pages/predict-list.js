const PredictList = (() => {
  let state = { page: 1, size: 20, total: 0, issue: '' }
  let selectedIds = new Set()

  function render() {
    selectedIds = new Set()
    document.getElementById('main-content').innerHTML = `
      <div class="card">
        <div class="card-header">
          <span>预测号码列表</span>
          <div style="display:flex;gap:8px;">
            <button class="btn btn-success" id="btn-export">导出 TXT</button>
            <button class="btn btn-primary" id="btn-sync-issue">按期号同步购买</button>
            <button class="btn btn-primary" id="btn-sync-selected" disabled>同步勾选到购买</button>
            <button class="btn btn-warning" id="btn-calc">手动补算</button>
            <button class="btn btn-danger" id="btn-clear">按期号清除</button>
          </div>
        </div>
        <div class="card-body">
          <div class="filter-bar">
            <label>目标期号</label>
            <input class="form-input" id="q-issue" placeholder="请输入期号" value="${state.issue}" style="width:160px;" />
            <button class="btn btn-primary" id="btn-search">查询</button>
            <button class="btn btn-default" id="btn-reset">重置</button>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th style="width:36px;"><input type="checkbox" id="check-all" title="全选/取消" /></th>
                  <th>目标期号</th><th>红球</th><th>蓝球</th>
                  <th class="text-center">命中红球</th><th class="text-center">命中蓝球</th>
                  <th class="text-center">命中等级</th><th>生成时间</th><th>操作</th>
                </tr>
              </thead>
              <tbody id="table-body"><tr><td colspan="9" class="text-center" style="color:#909399;">加载中...</td></tr></tbody>
            </table>
          </div>
          <div class="pagination" id="pagination"></div>
        </div>
      </div>`

    document.getElementById('btn-search').addEventListener('click', () => {
      state.issue = document.getElementById('q-issue').value.trim()
      state.page = 1
      fetchList()
    })
    document.getElementById('btn-reset').addEventListener('click', () => {
      state.issue = ''; state.page = 1
      document.getElementById('q-issue').value = ''
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

    fetchList()
  }

  function updateSyncBtn() {
    const btn = document.getElementById('btn-sync-selected')
    if (btn) btn.disabled = selectedIds.size === 0
  }

  async function fetchList() {
    const tbody = document.getElementById('table-body')
    if (!tbody) return
    tbody.innerHTML = '<tr><td colspan="9" class="text-center" style="color:#909399;">加载中...</td></tr>'
    try {
      const params = { page: state.page, size: state.size }
      if (state.issue) params.issue = state.issue
      const res = await api.get('/api/predict/list', params)
      const list = res.data.list || []
      state.total = res.data.total || 0

      if (!list.length) {
        tbody.innerHTML = '<tr><td colspan="9" class="text-center" style="color:#909399;">暂无数据</td></tr>'
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
          return `<tr>
            <td style="text-align:center;"><input type="checkbox" class="row-check" data-id="${row.id}" ${checked} /></td>
            <td>${row.issue}</td>
            <td>${renderReds(row.reds)}</td>
            <td>${renderBlue(row.blue)}</td>
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
      if (tbody) tbody.innerHTML = '<tr><td colspan="9" class="text-center" style="color:#f56c6c;">加载失败</td></tr>'
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
    let issues
    try {
      const res = await api.get('/api/predict/issues')
      issues = res.data || []
    } catch (e) { return }

    if (!issues.length) { toast('暂无预测记录', 'warning'); return }

    const checked = new Set()

    openModal('选择同步期号',
      `<div style="margin-bottom:8px;">
        <label style="cursor:pointer;">
          <input type="checkbox" id="sync-check-all" /> 全选
        </label>
      </div>
      <div style="max-height:320px;overflow-y:auto;display:flex;flex-wrap:wrap;gap:8px;">
        ${issues.map(issue => `
          <label style="cursor:pointer;display:flex;align-items:center;gap:4px;min-width:100px;">
            <input type="checkbox" class="sync-issue-cb" value="${issue}" />
            ${issue}
          </label>`).join('')}
      </div>`,
      `<button class="btn btn-default" id="sync-cancel">取消</button>
       <button class="btn btn-primary" id="sync-confirm">同步到购买记录</button>`
    )

    document.getElementById('sync-check-all').addEventListener('change', e => {
      document.querySelectorAll('.sync-issue-cb').forEach(cb => {
        cb.checked = e.target.checked
        e.target.checked ? checked.add(cb.value) : checked.delete(cb.value)
      })
    })
    document.querySelectorAll('.sync-issue-cb').forEach(cb => {
      cb.addEventListener('change', e => {
        e.target.checked ? checked.add(cb.value) : checked.delete(cb.value)
        const all = document.querySelectorAll('.sync-issue-cb')
        document.getElementById('sync-check-all').checked = [...all].every(c => c.checked)
      })
    })

    document.getElementById('sync-cancel').addEventListener('click', closeModal)
    document.getElementById('sync-confirm').addEventListener('click', async () => {
      const selected = [...checked]
      if (!selected.length) { toast('请至少选择一个期号', 'warning'); return }

      const btn = document.getElementById('sync-confirm')
      btn.disabled = true
      btn.textContent = '同步中...'

      try {
        const res = await api.post('/api/predict/sync-by-issues', selected)
        toast(`同步完成：共同步 ${res.data} 条到购买记录`)
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
      await confirm(`确认将已勾选的 ${selectedIds.size} 条预测号码同步到购买记录？`)
      const res = await api.post('/api/predict/sync-by-ids', [...selectedIds])
      toast(`同步完成：共同步 ${res.data} 条到购买记录`)
      selectedIds.clear()
      fetchList()
    } catch (e) {}
  }

  async function handleExport() {
    let issues
    try {
      const res = await api.get('/api/predict/issues')
      issues = res.data || []
    } catch (e) { return }

    if (!issues.length) { toast('暂无预测记录可导出', 'warning'); return }

    const checked = new Set(issues)

    openModal('选择导出期号',
      `<div style="margin-bottom:8px;">
        <label style="cursor:pointer;">
          <input type="checkbox" id="export-check-all" checked /> 全选
        </label>
      </div>
      <div style="max-height:320px;overflow-y:auto;display:flex;flex-wrap:wrap;gap:8px;" id="export-issue-list">
        ${issues.map(issue => `
          <label style="cursor:pointer;display:flex;align-items:center;gap:4px;min-width:100px;">
            <input type="checkbox" class="export-issue-cb" value="${issue}" checked />
            ${issue}
          </label>`).join('')}
      </div>`,
      `<button class="btn btn-default" id="export-cancel">取消</button>
       <button class="btn btn-primary" id="export-download">下载</button>`
    )

    document.getElementById('export-check-all').addEventListener('change', e => {
      document.querySelectorAll('.export-issue-cb').forEach(cb => {
        cb.checked = e.target.checked
        e.target.checked ? checked.add(cb.value) : checked.delete(cb.value)
      })
    })
    document.querySelectorAll('.export-issue-cb').forEach(cb => {
      cb.addEventListener('change', e => {
        e.target.checked ? checked.add(cb.value) : checked.delete(cb.value)
        const all = document.querySelectorAll('.export-issue-cb')
        document.getElementById('export-check-all').checked = [...all].every(c => c.checked)
      })
    })

    document.getElementById('export-cancel').addEventListener('click', closeModal)
    document.getElementById('export-download').addEventListener('click', async () => {
      const selected = [...checked]
      if (!selected.length) { toast('请至少选择一个期号', 'warning'); return }

      const btn = document.getElementById('export-download')
      btn.disabled = true
      btn.textContent = '导出中...'

      try {
        const response = await fetch(`${api.baseUrl}/api/predict/export`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(selected)
        })
        if (!response.ok) {
          const err = await response.json().catch(() => null)
          toast((err && err.message) || '导出失败', 'error')
          return
        }

        const blob = await response.blob()
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `预测号码_${selected.length}期_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '')}.txt`
        a.click()
        setTimeout(() => URL.revokeObjectURL(url), 1000)
        closeModal()
        toast('导出成功')
      } catch (e) {
      } finally {
        btn.disabled = false
        btn.textContent = '下载'
      }
    })
  }

  async function handleDeleteById(id) {
    try {
      await confirm('确认删除该条预测记录？')
      await api.delete(`/api/predict/${id}`)
      selectedIds.delete(Number(id))
      toast('删除成功')
      fetchList()
    } catch (e) {}
  }

  async function handleDeleteByIssue() {
    try {
      const issue = await prompt('请输入要清除的期号', '如：2024001', v => !!v || '期号不能为空')
      const res = await api.delete(`/api/predict/issue/${issue}`)
      toast(`已清除期号 ${issue} 的 ${res.data} 条预测记录`)
      fetchList()
    } catch (e) {}
  }

  async function handleCalc() {
    try {
      const issue = await prompt('请输入要补算的期号', '如：2024001', v => !!v || '期号不能为空')
      const res = await api.post(`/api/predict/calc/${issue}`)
      toast(`期号 ${issue} 补算完成，共更新 ${res.data} 条记录`)
      fetchList()
    } catch (e) {}
  }

  return { render }
})()
