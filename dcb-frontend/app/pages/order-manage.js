const OrderManage = (() => {
  let state = { page: 1, size: 20, total: 0, summary: {} }

  function render() {
    document.getElementById('main-content').innerHTML = `
      <div class="card" style="flex:1;display:flex;flex-direction:column;min-height:0;">
        <div class="card-header"><span>订单管理</span></div>
        <div class="card-body" style="flex:1;display:flex;flex-direction:column;min-height:0;overflow:hidden;">
          <div class="stat-cards" style="margin-bottom:16px;display:flex;gap:16px;">
            <div class="stat-card" style="flex:1;"><div class="stat-label">总订单数</div><div class="stat-value" id="o-count">-</div></div>
            <div class="stat-card" style="flex:1;"><div class="stat-label">总收入</div><div class="stat-value" style="color:#67c23a;" id="o-revenue">-</div></div>
          </div>
          <div class="table-scroll" style="flex:1;min-height:0;">
            <table>
              <thead>
                <tr>
                  <th>订单号</th><th>用户ID</th><th>金额</th><th>开始时间</th>
                  <th>到期时间</th><th>状态</th><th>支付时间</th><th>微信交易号</th>
                </tr>
              </thead>
              <tbody id="table-body"><tr><td colspan="8" class="text-center" style="color:#909399;">加载中...</td></tr></tbody>
            </table>
          </div>
          <div class="pagination" id="pagination"></div>
        </div>
      </div>`
    fetchList()
    fetchSummary()
  }

  async function fetchSummary() {
    try {
      const res = await api.get('/api/admin/orders/summary')
      state.summary = res.data
      document.getElementById('o-count').textContent = state.summary.totalOrders || 0
      document.getElementById('o-revenue').textContent = '¥' + (state.summary.totalRevenue || 0)
    } catch (e) {}
  }

  async function fetchList() {
    const tbody = document.getElementById('table-body')
    if (!tbody) return
    tbody.innerHTML = '<tr><td colspan="8" class="text-center" style="color:#909399;">加载中...</td></tr>'
    try {
      const res = await api.get('/api/admin/orders', { page: state.page, size: state.size })
      const list = res.data.list || []
      state.total = res.data.total || 0

      if (!list.length) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center" style="color:#909399;">暂无数据</td></tr>'
      } else {
        tbody.innerHTML = list.map(o => {
          const statusTags = {0:'<span class="tag tag-info">待支付</span>',1:'<span class="tag tag-success">已支付</span>',2:'<span class="tag tag-danger">已取消</span>'}
          return `<tr>
            <td>${o.orderNo||'-'}</td><td class="text-center">${o.userId}</td>
            <td class="text-right">¥${o.amount||'-'}</td><td>${o.startAt||'-'}</td>
            <td>${o.endAt||'-'}</td><td class="text-center">${statusTags[o.status]||o.status}</td>
            <td>${o.paidAt||'-'}</td><td>${o.wxTransactionId||'-'}</td>
          </tr>`
        }).join('')
      }

      renderPagination('pagination', state.page, state.size, state.total, (p, s) => {
        state.page = p; state.size = s; fetchList()
      })
    } catch (e) {
      tbody.innerHTML = '<tr><td colspan="8" class="text-center" style="color:#f56c6c;">加载失败</td></tr>'
    }
  }

  return { render }
})()
