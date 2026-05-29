const PredictList = (() => {
  let state = { page: 1, size: 20, total: 0, issue: '' }

  function render() {
    document.getElementById('main-content').innerHTML = `
      <div class="card">
        <div class="card-header">
          <span>预测号码列表</span>
          <div style="display:flex;gap:8px;">
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
                  <th>目标期号</th><th>红球</th><th>蓝球</th>
                  <th class="text-center">命中红球</th><th class="text-center">命中蓝球</th>
                  <th class="text-center">命中等级</th><th>生成时间</th><th>操作</th>
                </tr>
              </thead>
              <tbody id="table-body"><tr><td colspan="8" class="text-center" style="color:#909399;">加载中...</td></tr></tbody>
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

    fetchList()
  }

  async function fetchList() {
    const tbody = document.getElementById('table-body')
    if (!tbody) return
    tbody.innerHTML = '<tr><td colspan="8" class="text-center" style="color:#909399;">加载中...</td></tr>'
    try {
      const params = { page: state.page, size: state.size }
      if (state.issue) params.issue = state.issue
      const res = await api.get('/api/predict/list', params)
      const list = res.data.list || []
      state.total = res.data.total || 0

      if (!list.length) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center" style="color:#909399;">暂无数据</td></tr>'
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
          return `<tr>
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

        tbody.querySelectorAll('[data-id]').forEach(btn => {
          btn.addEventListener('click', () => handleDeleteById(btn.dataset.id))
        })
      }

      renderPagination('pagination', state.page, state.size, state.total, (p, s) => {
        state.page = p; state.size = s; fetchList()
      })
    } catch (e) {
      if (tbody) tbody.innerHTML = '<tr><td colspan="8" class="text-center" style="color:#f56c6c;">加载失败</td></tr>'
    }
  }

  async function handleDeleteById(id) {
    try {
      await confirm('确认删除该条预测记录？')
      await api.delete(`/api/predict/${id}`)
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
