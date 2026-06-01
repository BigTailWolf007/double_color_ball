const CalcErrorLogList = (() => {
  let state = { page: 1, size: 20, total: 0, issue: '', selectedIds: new Set() }

  const STATUS_MAP = { 0: '待处理', 1: '已重试成功', 2: '已忽略' }
  const STATUS_TAG = { 0: 'warning', 1: 'success', 2: 'info' }
  const TYPE_MAP = { purchase: '购买记录', predict: '预测记录' }

  function render() {
    document.getElementById('main-content').innerHTML = `
      <div class="card" style="flex:1;display:flex;flex-direction:column;min-height:0;">
        <div class="card-header">
          <span>计算错误日志</span>
          <div class="card-header-actions">
            <button class="btn btn-primary" id="btn-retry">重新计算</button>
            <button class="btn btn-default" id="btn-ignore">忽略</button>
          </div>
        </div>
        <div class="card-body" style="flex:1;display:flex;flex-direction:column;min-height:0;overflow:hidden;">
          <div class="filter-bar">
            <label>期号</label>
            <input class="form-input" id="q-issue" placeholder="请输入期号" value="${state.issue}" style="width:160px;" />
            <button class="btn btn-primary" id="btn-search">查询</button>
            <button class="btn btn-default" id="btn-reset">重置</button>
          </div>
          <div class="table-scroll" style="flex:1;min-height:0;">
            <table>
              <thead>
                <tr>
                  <th style="width:40px;"><input type="checkbox" id="check-all" /></th>
                  <th style="width:100px;">期号</th><th style="width:90px;">类型</th><th style="width:180px;">ID区间</th><th>异常信息</th><th style="width:100px;">状态</th><th style="width:130px;">创建时间</th>
                </tr>
              </thead>
              <tbody id="table-body"><tr><td colspan="7" class="text-center" style="color:#909399;">加载中...</td></tr></tbody>
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
    document.getElementById('btn-retry').addEventListener('click', handleRetry)
    document.getElementById('btn-ignore').addEventListener('click', handleIgnore)

    renderIssueInput('q-issue', '/api/purchase/issue-suggest', val => { state.issue = val })

    fetchList()
  }

  async function fetchList() {
    const tbody = document.getElementById('table-body')
    if (!tbody) return
    tbody.innerHTML = '<tr><td colspan="7" class="text-center" style="color:#909399;">加载中...</td></tr>'
    try {
      const params = { page: state.page, size: state.size }
      if (state.issue) params.issue = state.issue
      const res = await api.get('/api/calc-error/list', params)
      const list = res.data.list || []
      state.total = res.data.total || 0

      if (!list.length) {
        tbody.innerHTML = '<tr><td colspan="7" class="text-center" style="color:#909399;">暂无数据</td></tr>'
      } else {
        tbody.innerHTML = list.map(row => {
          const checked = state.selectedIds.has(row.id) ? 'checked' : ''
          const errorShort = row.errorMsg && row.errorMsg.length > 60
            ? row.errorMsg.substring(0, 60) + '...' : (row.errorMsg || '-')
          return `
          <tr>
            <td><input type="checkbox" class="row-check" data-id="${row.id}" ${checked} /></td>
            <td>${row.issue}</td>
            <td>${TYPE_MAP[row.calcType] || row.calcType}</td>
            <td style="font-size:12px;">[${row.idStart}, ${row.idEnd})</td>
            <td style="font-size:12px;max-width:300px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" title="${escapeHtml(row.errorMsg || '')}">${escapeHtml(errorShort)}</td>
            <td><span class="tag tag-${STATUS_TAG[row.status] || 'info'}">${STATUS_MAP[row.status] || row.status}</span></td>
            <td>${row.createdAt || '-'}</td>
          </tr>`
        }).join('')

        // 全选复选框
        document.getElementById('check-all').addEventListener('change', e => {
          const checked = e.target.checked
          list.forEach(r => {
            if (checked) state.selectedIds.add(r.id)
            else state.selectedIds.delete(r.id)
          })
          fetchList()
        })

        // 行复选框
        tbody.querySelectorAll('.row-check').forEach(cb => {
          cb.addEventListener('change', e => {
            const id = parseInt(cb.dataset.id)
            if (cb.checked) state.selectedIds.add(id)
            else state.selectedIds.delete(id)
          })
        })
      }

      renderPagination('pagination', state.page, state.size, state.total, (p, s) => {
        state.page = p; state.size = s; fetchList()
      })
    } catch (e) {
      if (tbody) tbody.innerHTML = '<tr><td colspan="7" class="text-center" style="color:#f56c6c;">加载失败</td></tr>'
    }
  }

  function escapeHtml(str) {
    return String(str || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
  }

  async function handleRetry() {
    if (state.selectedIds.size === 0) { toast('请先勾选要重试的记录', 'warning'); return }
    // 只重试状态为"待处理"的记录
    const ids = [...state.selectedIds]
    try {
      await confirm(`确认重新计算选中的 ${ids.length} 条记录？`)
      await api.post('/api/calc-error/retry', ids)
      state.selectedIds.clear()
      toast('已提交 ' + ids.length + ' 个重试任务')
      fetchList()
    } catch (e) {}
  }

  async function handleIgnore() {
    if (state.selectedIds.size === 0) { toast('请先勾选要忽略的记录', 'warning'); return }
    const ids = [...state.selectedIds]
    try {
      await confirm(`确认忽略选中的 ${ids.length} 条记录？`)
      await api.post('/api/calc-error/ignore', ids)
      state.selectedIds.clear()
      toast('已忽略 ' + ids.length + ' 条记录')
      fetchList()
    } catch (e) {}
  }

  return { render }
})()
