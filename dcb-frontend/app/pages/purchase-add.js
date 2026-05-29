const PurchaseAdd = (() => {
  const redOptions = Array.from({ length: 33 }, (_, i) => i + 1)
  const blueOptions = Array.from({ length: 16 }, (_, i) => i + 1)
  let rows = []

  function newRow() {
    return { issue: '', reds: [], blue: null, quantity: 1, remark: '' }
  }

  function render() {
    rows = [newRow()]
    renderPage()
  }

  function renderPage() {
    document.getElementById('main-content').innerHTML = `
      <div class="card">
        <div class="card-header">
          <span>录入购买号码</span>
          <button class="btn btn-success" id="btn-add-row">+ 添加一组</button>
        </div>
        <div class="card-body">
          <div id="rows-container"></div>
          <div style="text-align:right;margin-top:8px;">
            <button class="btn btn-default" id="btn-cancel">取消</button>
            <button class="btn btn-primary" id="btn-submit">提交录入</button>
          </div>
        </div>
      </div>`

    document.getElementById('btn-add-row').addEventListener('click', () => {
      rows.push(newRow())
      renderRows()
    })
    document.getElementById('btn-cancel').addEventListener('click', () => {
      router.navigate('purchase-list')
    })
    document.getElementById('btn-submit').addEventListener('click', handleSubmit)

    renderRows()
  }

  function renderRows() {
    const container = document.getElementById('rows-container')
    if (!container) return
    container.innerHTML = rows.map((row, idx) => `
      <div class="purchase-row" id="row-${idx}">
        <div class="purchase-row-header">
          <span>第 ${idx + 1} 组</span>
          ${rows.length > 1 ? `<button class="btn btn-link btn-danger btn-sm" data-remove="${idx}">删除本组</button>` : ''}
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>期号 <span style="color:#f56c6c;">*</span></label>
            <input class="form-input" id="row-issue-${idx}" placeholder="如：2024001" value="${row.issue}" style="width:140px;" />
          </div>
          <div class="form-group">
            <label>注数 <span style="color:#f56c6c;">*</span></label>
            <input class="form-input" id="row-qty-${idx}" type="number" min="1" max="999" value="${row.quantity}" style="width:80px;" />
          </div>
          <div class="form-group">
            <label>备注</label>
            <input class="form-input" id="row-remark-${idx}" placeholder="可选" value="${row.remark}" style="width:160px;" />
          </div>
        </div>
        <div class="form-group" style="margin-bottom:10px;">
          <label>红球 <span style="color:#f56c6c;">*</span></label>
          <div class="ball-picker" id="red-picker-${idx}"></div>
          <div class="ball-count" id="red-count-${idx}">已选 ${row.reds.length}/6 个</div>
        </div>
        <div class="form-group">
          <label>蓝球 <span style="color:#f56c6c;">*</span></label>
          <div class="ball-picker" id="blue-picker-${idx}"></div>
        </div>
      </div>`).join('')

    // 绑定删除按钮
    container.querySelectorAll('[data-remove]').forEach(btn => {
      btn.addEventListener('click', () => {
        rows.splice(parseInt(btn.dataset.remove), 1)
        renderRows()
      })
    })

    // 绑定输入框同步
    rows.forEach((row, idx) => {
      document.getElementById(`row-issue-${idx}`).addEventListener('input', e => { row.issue = e.target.value.trim() })
      document.getElementById(`row-qty-${idx}`).addEventListener('input', e => { row.quantity = parseInt(e.target.value) || 1 })
      document.getElementById(`row-remark-${idx}`).addEventListener('input', e => { row.remark = e.target.value })

      // 红球选择器
      renderBallPicker(`red-picker-${idx}`, redOptions, row.reds, 6, 'tag-danger', (reds) => {
        row.reds = reds
        const el = document.getElementById(`red-count-${idx}`)
        if (el) el.textContent = `已选 ${row.reds.length}/6 个`
      })

      // 蓝球选择器（单选）
      renderBallPicker(`blue-picker-${idx}`, blueOptions, row.blue, 1, 'tag-primary', (n) => { row.blue = n })
    })
  }

  async function handleSubmit() {
    for (let i = 0; i < rows.length; i++) {
      const row = rows[i]
      if (!row.issue) { toast(`第${i + 1}行：期号不能为空`, 'warning'); return }
      if (row.reds.length !== 6) { toast(`第${i + 1}行：请选择6个红球`, 'warning'); return }
      if (!row.blue) { toast(`第${i + 1}行：请选择蓝球`, 'warning'); return }
      if (!row.quantity || row.quantity < 1) { toast(`第${i + 1}行：注数至少1注`, 'warning'); return }
    }

    const payload = rows.map(row => {
      const sorted = [...row.reds].sort((a, b) => a - b)
      return {
        issue: row.issue,
        red1: sorted[0], red2: sorted[1], red3: sorted[2],
        red4: sorted[3], red5: sorted[4], red6: sorted[5],
        blue: row.blue,
        quantity: row.quantity,
        remark: row.remark || null
      }
    })

    try {
      await api.post('/api/purchase/add', payload)
      toast('录入成功')
      router.navigate('purchase-list')
    } catch (e) {}
  }

  return { render }
})()
