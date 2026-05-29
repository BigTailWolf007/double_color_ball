const PurchaseList = (() => {
  let state = { page: 1, size: 20, total: 0, issue: '', prizeLevel: '' }

  const prizeLevelOptions = [
    { label: '一等奖', value: 1 }, { label: '二等奖', value: 2 },
    { label: '三等奖', value: 3 }, { label: '四等奖', value: 4 },
    { label: '五等奖', value: 5 }, { label: '六等奖', value: 6 },
    { label: '未中奖', value: 0 }
  ]

  function render() {
    document.getElementById('main-content').innerHTML = `
      <div class="card">
        <div class="card-header">
          <span>购买记录列表</span>
        </div>
        <div class="card-body">
          <div class="filter-bar">
            <label>期号</label>
            <input class="form-input" id="q-issue" placeholder="请输入期号" value="${state.issue}" style="width:160px;" />
            <label>中奖等级</label>
            <select class="form-select" id="q-level" style="width:120px;">
              <option value="">全部</option>
              ${prizeLevelOptions.map(o => `<option value="${o.value}" ${state.prizeLevel === String(o.value) ? 'selected' : ''}>${o.label}</option>`).join('')}
            </select>
            <button class="btn btn-primary" id="btn-search">查询</button>
            <button class="btn btn-default" id="btn-reset">重置</button>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>期号</th><th>红球</th><th>蓝球</th><th class="text-center">注数</th>
                  <th class="text-center">中奖等级</th><th class="text-right">总奖金</th>
                  <th>备注</th><th>操作</th>
                </tr>
              </thead>
              <tbody id="table-body"><tr><td colspan="8" class="text-center" style="color:#909399;">加载中...</td></tr></tbody>
            </table>
          </div>
          <div class="pagination" id="pagination"></div>
          <div class="stat-cards" id="stat-cards">
            <div class="stat-card"><div class="stat-label">总投入</div><div class="stat-value" id="stat-cost">-</div></div>
            <div class="stat-card"><div class="stat-label">总奖金</div><div class="stat-value" style="color:#67c23a;" id="stat-prize">-</div></div>
            <div class="stat-card"><div class="stat-label">盈亏</div><div class="stat-value" id="stat-profit">-</div></div>
          </div>
        </div>
      </div>`

    document.getElementById('btn-search').addEventListener('click', () => {
      state.issue = document.getElementById('q-issue').value.trim()
      state.prizeLevel = document.getElementById('q-level').value
      state.page = 1
      fetchList()
    })
    document.getElementById('btn-reset').addEventListener('click', () => {
      state.issue = ''; state.prizeLevel = ''; state.page = 1
      document.getElementById('q-issue').value = ''
      document.getElementById('q-level').value = ''
      fetchList()
    })

    fetchList()
    fetchSummary()
  }

  async function fetchList() {
    const tbody = document.getElementById('table-body')
    if (!tbody) return
    tbody.innerHTML = '<tr><td colspan="8" class="text-center" style="color:#909399;">加载中...</td></tr>'
    try {
      const params = { page: state.page, size: state.size }
      if (state.issue) params.issue = state.issue
      if (state.prizeLevel !== '') params.prizeLevel = state.prizeLevel
      const res = await api.get('/api/purchase/list', params)
      const list = res.data.list || []
      state.total = res.data.total || 0

      if (!list.length) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center" style="color:#909399;">暂无数据</td></tr>'
      } else {
        tbody.innerHTML = list.map(row => {
          const prizeColor = row.prizeMoney > 0 ? '#67c23a' : '#909399'
          return `<tr>
            <td>${row.issue}</td>
            <td>${renderReds(row.reds)}</td>
            <td>${renderBlue(row.blue)}</td>
            <td class="text-center">${row.quantity}</td>
            <td class="text-center">${renderPrizeLevel(row.prizeLevel, row.prizeLevelDesc)}</td>
            <td class="text-right" style="color:${prizeColor};">¥${row.prizeMoney ?? '-'}</td>
            <td>${row.remark || ''}</td>
            <td><button class="btn btn-link btn-danger btn-sm" data-id="${row.id}">删除</button></td>
          </tr>`
        }).join('')

        tbody.querySelectorAll('[data-id]').forEach(btn => {
          btn.addEventListener('click', () => handleDelete(btn.dataset.id))
        })
      }

      renderPagination('pagination', state.page, state.size, state.total, (p, s) => {
        state.page = p; state.size = s; fetchList()
      })
    } catch (e) {
      if (tbody) tbody.innerHTML = '<tr><td colspan="8" class="text-center" style="color:#f56c6c;">加载失败</td></tr>'
    }
  }

  async function fetchSummary() {
    try {
      const res = await api.get('/api/purchase/summary')
      const s = res.data
      const costEl = document.getElementById('stat-cost')
      const prizeEl = document.getElementById('stat-prize')
      const profitEl = document.getElementById('stat-profit')
      if (costEl) costEl.textContent = `¥${s.totalCost}`
      if (prizeEl) prizeEl.textContent = `¥${s.totalPrizeMoney}`
      if (profitEl) {
        profitEl.textContent = `${s.profit >= 0 ? '+' : ''}¥${s.profit}`
        profitEl.style.color = s.profit >= 0 ? '#67c23a' : '#f56c6c'
      }
    } catch (e) {}
  }

  async function handleDelete(id) {
    try {
      await confirm('确认删除该条购买记录？')
      await api.delete(`/api/purchase/${id}`)
      toast('删除成功')
      fetchList()
      fetchSummary()
    } catch (e) {}
  }

  return { render }
})()
