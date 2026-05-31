const PurchaseList = (() => {
  let state = { page: 1, size: 20, total: 0, issue: '', prizeLevel: '' }
  let selectedIds = new Set()

  const prizeLevelOptions = [
    { label: '一等奖', value: 1 }, { label: '二等奖', value: 2 },
    { label: '三等奖', value: 3 }, { label: '四等奖', value: 4 },
    { label: '五等奖', value: 5 }, { label: '六等奖', value: 6 },
    { label: '未中奖', value: 0 }
  ]

  function render() {
    selectedIds = new Set()
    document.getElementById('main-content').innerHTML = `
      <div class="card" style="flex:1;display:flex;flex-direction:column;min-height:0;">
        <div class="card-header">
          <span>购买记录列表</span>
          <div style="display:flex;gap:8px;">
            <button class="btn btn-danger" id="btn-delete-issue">按期号删除</button>
            <button class="btn btn-danger" id="btn-batch-delete" disabled>批量删除</button>
            <button class="btn btn-warning" id="btn-recalc" disabled>重新计算盈亏</button>
          </div>
        </div>
        <div class="card-body" style="flex:1;display:flex;flex-direction:column;min-height:0;overflow:hidden;">
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
          <div class="stat-cards" id="stat-cards" style="margin-top:0;margin-bottom:16px;">
            <div class="stat-card"><div class="stat-label">总投入</div><div class="stat-value" id="stat-cost">-</div></div>
            <div class="stat-card"><div class="stat-label">总奖金</div><div class="stat-value" style="color:#67c23a;" id="stat-prize">-</div></div>
            <div class="stat-card"><div class="stat-label">盈亏</div><div class="stat-value" id="stat-profit">-</div></div>
          </div>
          <div class="table-scroll" style="flex:1;min-height:0;">
            <table>
              <thead>
                <tr>
                  <th style="width:36px;"><input type="checkbox" id="check-all" title="全选/取消" /></th>
                  <th>期号</th><th>号码</th><th class="text-center">注数</th>
                  <th class="text-center">中奖等级</th><th class="text-right">总奖金</th>
                  <th>备注</th><th>操作</th>
                </tr>
              </thead>
              <tbody id="table-body"><tr><td colspan="8" class="text-center" style="color:#909399;">加载中...</td></tr></tbody>
            </table>
          </div>
          <div class="pagination" id="pagination" style="margin-top:12px;"></div>
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
    document.getElementById('btn-recalc').addEventListener('click', handleRecalc)
    document.getElementById('btn-delete-issue').addEventListener('click', handleDeleteByIssue)
    document.getElementById('btn-batch-delete').addEventListener('click', handleBatchDelete)
    document.getElementById('check-all').addEventListener('change', () => {
      const tbody = document.getElementById('table-body')
      const checkAll = document.getElementById('check-all')
      if (!tbody || !checkAll) return
      tbody.querySelectorAll('.row-check').forEach(cb => {
        cb.checked = checkAll.checked
        const id = Number(cb.dataset.id)
        checkAll.checked ? selectedIds.add(id) : selectedIds.delete(id)
      })
      updateActionBtns()
    })

    renderIssueInput('q-issue', '/api/purchase/issue-suggest', val => { state.issue = val })

    fetchList()
    fetchSummary()
  }

  function updateActionBtns() {
    const recalcBtn = document.getElementById('btn-recalc')
    const deleteBtn = document.getElementById('btn-batch-delete')
    if (recalcBtn) recalcBtn.disabled = selectedIds.size === 0
    if (deleteBtn) deleteBtn.disabled = selectedIds.size === 0
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
          const checked = selectedIds.has(row.id) ? 'checked' : ''
          return `<tr>
            <td style="text-align:center;"><input type="checkbox" class="row-check" data-id="${row.id}" ${checked} /></td>
            <td>${row.issue}</td>
            <td>${renderReds(row.reds)}${renderBlue(row.blue)}</td>
            <td class="text-center">${row.quantity}</td>
            <td class="text-center">${renderPrizeLevel(row.prizeLevel, row.prizeLevelDesc)}</td>
            <td class="text-right" style="color:${prizeColor};">¥${row.prizeMoney ?? '-'}</td>
            <td>${row.remark || ''}</td>
            <td><button class="btn btn-link btn-primary btn-sm btn-edit" data-id="${row.id}" data-quantity="${row.quantity}" data-remark="${(row.remark || '').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;')}">编辑</button><button class="btn btn-link btn-danger btn-sm btn-delete" data-id="${row.id}">删除</button></td>
          </tr>`
        }).join('')

        tbody.querySelectorAll('.row-check').forEach(cb => {
          cb.addEventListener('change', () => {
            const id = Number(cb.dataset.id)
            cb.checked ? selectedIds.add(id) : selectedIds.delete(id)
            syncCheckAll()
            updateActionBtns()
          })
        })

        tbody.querySelectorAll('.btn-edit').forEach(btn => {
          btn.addEventListener('click', () => handleEdit(btn.dataset.id, btn.dataset.quantity, btn.dataset.remark))
        })

        tbody.querySelectorAll('.btn-delete').forEach(btn => {
          btn.addEventListener('click', () => handleDelete(btn.dataset.id))
        })

        syncCheckAll()
      }

      renderPagination('pagination', state.page, state.size, state.total, (p, s) => {
        state.page = p; state.size = s; fetchList()
      })
    } catch (e) {
      if (tbody) tbody.innerHTML = '<tr><td colspan="8" class="text-center" style="color:#f56c6c;">加载失败</td></tr>'
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

  function handleEdit(id, quantity, remark) {
    const safeRemark = (remark || '').replace(/"/g, '&quot;')
    openModal('编辑购买记录', `
      <div style="display:flex;flex-direction:column;gap:12px;">
        <div style="display:flex;align-items:center;gap:8px;">
          <label style="width:48px;text-align:right;flex-shrink:0;">注数</label>
          <input class="form-input" id="edit-quantity" type="number" min="1" max="9999" value="${quantity}" style="width:100px;" />
        </div>
        <div style="display:flex;align-items:center;gap:8px;">
          <label style="width:48px;text-align:right;flex-shrink:0;">备注</label>
          <input class="form-input" id="edit-remark" type="text" value="${safeRemark}" placeholder="选填" maxlength="500" style="flex:1;" />
        </div>
      </div>`,
      `<button class="btn btn-default" id="edit-cancel">取消</button>
       <button class="btn btn-primary" id="edit-ok">保存</button>`)

    document.getElementById('edit-cancel').addEventListener('click', closeModal)
    document.getElementById('edit-ok').addEventListener('click', async () => {
      const qty = parseInt(document.getElementById('edit-quantity').value, 10)
      if (isNaN(qty) || qty < 1 || qty > 9999) { toast('注数必须为1-9999之间的整数', 'error'); return }
      try {
        await api.put(`/api/purchase/${id}`, { quantity: qty, remark: document.getElementById('edit-remark').value.trim() })
        closeModal()
        toast('保存成功')
        fetchList()
        fetchSummary()
      } catch (e) {}
    })
  }

  async function handleDelete(id) {
    try {
      await confirm('确认删除该条购买记录？')
      await api.delete(`/api/purchase/${id}`)
      selectedIds.delete(Number(id))
      toast('删除成功')
      fetchList()
      fetchSummary()
    } catch (e) {}
  }

  async function handleDeleteByIssue() {
    try {
      const issue = await prompt('请输入要删除的期号', '如：2024001', v => !!v || '期号不能为空')
      await confirm(`确认删除期号 ${issue} 的所有购买记录？`)
      const res = await api.delete(`/api/purchase/issue/${issue}`)
      toast(`已删除期号 ${issue} 的 ${res.data} 条购买记录`)
      fetchList()
      fetchSummary()
    } catch (e) {}
  }

  async function handleBatchDelete() {
    if (!selectedIds.size) return
    try {
      await confirm(`确认删除已勾选的 ${selectedIds.size} 条购买记录？`)
      const res = await api.post('/api/purchase/batch-delete', [...selectedIds])
      toast(`已删除 ${res.data} 条购买记录`)
      selectedIds.clear()
      fetchList()
      fetchSummary()
    } catch (e) {}
  }

  async function handleRecalc() {
    if (!selectedIds.size) return
    try {
      await confirm(`确认对已勾选的 ${selectedIds.size} 条记录重新计算盈亏？`)
      const res = await api.post('/api/purchase/recalc', [...selectedIds])
      toast(`重算完成，共更新 ${res.data} 条记录`)
      selectedIds.clear()
      fetchList()
      fetchSummary()
    } catch (e) {}
  }

  return { render }
})()
