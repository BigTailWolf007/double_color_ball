const LotteryList = (() => {
  let state = { page: 1, size: 20, total: 0, issue: '', startDate: '', endDate: '', loading: false }
  const redOptions = Array.from({ length: 33 }, (_, i) => i + 1)
  const blueOptions = Array.from({ length: 16 }, (_, i) => i + 1)
  let formReds = [], formBlue = null

  function render() {
    document.getElementById('main-content').innerHTML = `
      <div class="card">
        <div class="card-header">
          <span>开奖号码列表</span>
          <button class="btn btn-primary" id="btn-add">手动录入</button>
        </div>
        <div class="card-body">
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
          <div class="table-wrap" id="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>期号</th><th>开奖日期</th><th>红球</th><th>蓝球</th><th>录入时间</th><th>操作</th>
                </tr>
              </thead>
              <tbody id="table-body"><tr><td colspan="6" class="text-center" style="color:#909399;">加载中...</td></tr></tbody>
            </table>
          </div>
          <div class="pagination" id="pagination"></div>
        </div>
      </div>`

    document.getElementById('btn-add').addEventListener('click', openAddDialog)
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

    fetchList()
  }

  async function fetchList() {
    const tbody = document.getElementById('table-body')
    if (!tbody) return
    tbody.innerHTML = '<tr><td colspan="6" class="text-center" style="color:#909399;">加载中...</td></tr>'
    try {
      const params = { page: state.page, size: state.size }
      if (state.issue) params.issue = state.issue
      if (state.startDate) params.startDate = state.startDate
      if (state.endDate) params.endDate = state.endDate
      const res = await api.get('/api/lottery/list', params)
      const list = res.data.list || []
      state.total = res.data.total || 0

      if (!list.length) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center" style="color:#909399;">暂无数据</td></tr>'
      } else {
        tbody.innerHTML = list.map(row => `
          <tr>
            <td>${row.issue}</td>
            <td>${row.drawDate || '-'}</td>
            <td>${renderReds(row.reds)}</td>
            <td>${renderBlue(row.blue)}</td>
            <td>${row.createdAt || '-'}</td>
            <td><button class="btn btn-link btn-danger btn-sm" data-id="${row.id}" data-issue="${row.issue}">删除</button></td>
          </tr>`).join('')

        tbody.querySelectorAll('[data-id]').forEach(btn => {
          btn.addEventListener('click', () => handleDelete(btn.dataset.id, btn.dataset.issue))
        })
      }

      renderPagination('pagination', state.page, state.size, state.total, (p, s) => {
        state.page = p; state.size = s; fetchList()
      })
    } catch (e) {
      if (tbody) tbody.innerHTML = '<tr><td colspan="6" class="text-center" style="color:#f56c6c;">加载失败</td></tr>'
    }
  }

  async function handleDelete(id, issue) {
    try {
      await confirm(`确认删除期号 ${issue} 的开奖号码？`)
      await api.delete(`/api/lottery/${id}`)
      toast('删除成功')
      fetchList()
    } catch (e) {}
  }

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
