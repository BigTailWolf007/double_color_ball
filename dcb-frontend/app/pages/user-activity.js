const UserActivity = (() => {
  let state = {}

  function render() {
    document.getElementById('main-content').innerHTML = `
      <div class="card" style="flex:1;display:flex;flex-direction:column;min-height:0;">
        <div class="card-header"><span>用户活跃度</span></div>
        <div class="card-body" style="flex:1;overflow:auto;">
          <div class="stat-cards" id="stat-cards" style="margin-bottom:16px;">
            <div class="stat-card"><div class="stat-label">今日日活</div><div class="stat-value" id="s-today">-</div></div>
            <div class="stat-card"><div class="stat-label">昨日日活</div><div class="stat-value" id="s-yesterday">-</div></div>
            <div class="stat-card"><div class="stat-label">本月月活</div><div class="stat-value" id="s-month">-</div></div>
            <div class="stat-card"><div class="stat-label">上月月活</div><div class="stat-value" id="s-last-month">-</div></div>
            <div class="stat-card"><div class="stat-label">总用户数</div><div class="stat-value" id="s-total">-</div></div>
            <div class="stat-card"><div class="stat-label">付费用户</div><div class="stat-value" style="color:#67c23a;" id="s-paid">-</div></div>
          </div>
          <h4 style="margin:16px 0 8px;font-size:14px;">近7天日活趋势</h4>
          <div class="table-scroll" style="max-height:200px;">
            <table>
              <thead><tr><th>日期</th><th>日活</th><th>月活</th></tr></thead>
              <tbody id="trend-body"><tr><td colspan="3" class="text-center" style="color:#909399;">加载中...</td></tr></tbody>
            </table>
          </div>
          <h4 style="margin:16px 0 8px;font-size:14px;">近7天活跃明细</h4>
          <div class="table-scroll" style="max-height:300px;">
            <table>
              <thead><tr><th>用户</th><th>登录方式</th><th>IP</th><th>时间</th></tr></thead>
              <tbody id="detail-body"><tr><td colspan="4" class="text-center" style="color:#909399;">加载中...</td></tr></tbody>
            </table>
          </div>
        </div>
      </div>`
    fetchData()
  }

  async function fetchData() {
    try {
      const res = await api.get('/api/admin/activity')
      const d = res.data
      document.getElementById('s-today').textContent = d.todayDau
      document.getElementById('s-yesterday').textContent = d.yesterdayDau
      document.getElementById('s-month').textContent = d.thisMonthMau
      document.getElementById('s-last-month').textContent = d.lastMonthMau
      document.getElementById('s-total').textContent = d.totalUsers
      document.getElementById('s-paid').textContent = d.paidUsers

      const trend = d.trend || []
      document.getElementById('trend-body').innerHTML = trend.length
        ? trend.map(t => `<tr><td>${t.date}</td><td class="text-center">${t.dau}</td><td class="text-center">${t.mau}</td></tr>`).join('')
        : '<tr><td colspan="3" class="text-center" style="color:#909399;">暂无数据</td></tr>'

      const detail = d.recentActives || []
      document.getElementById('detail-body').innerHTML = detail.length
        ? detail.map(a => `<tr><td>${a.nickname}</td><td class="text-center">${a.loginType}</td><td>${a.ip||'-'}</td><td>${a.time||'-'}</td></tr>`).join('')
        : '<tr><td colspan="4" class="text-center" style="color:#909399;">暂无数据</td></tr>'
    } catch (e) {}
  }

  return { render }
})()
