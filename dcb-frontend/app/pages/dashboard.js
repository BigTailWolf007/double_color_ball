const Dashboard = (() => {
  function render() {
    document.getElementById('main-content').innerHTML = `
      <div class="dashboard">
        <!-- 统计卡片 -->
        <div class="stat-cards-grid">
          <div class="stat-card"><div class="stat-label">总用户数</div><div class="stat-value" id="d-total">-</div></div>
          <div class="stat-card"><div class="stat-label">今日日活</div><div class="stat-value" id="d-dau">-</div></div>
          <div class="stat-card"><div class="stat-label">本月月活</div><div class="stat-value" id="d-mau">-</div></div>
          <div class="stat-card"><div class="stat-label">付费用户</div><div class="stat-value" style="color:#67c23a;" id="d-paid">-</div></div>
          <div class="stat-card"><div class="stat-label">订单收入</div><div class="stat-value" style="color:#e6a23c;" id="d-revenue">-</div></div>
        </div>

        <!-- 冷热号分析 2x2 网格布局 -->
        <div id="d-analysis" class="analysis-grid">
          <div style="color:#909399;padding:20px;text-align:center;grid-column:1/-1;">📊 冷热号分析加载中...</div>
        </div>

        <!-- 近7天趋势 -->
        <div class="card" style="margin-bottom:20px;">
          <div class="card-header">📈 近7天日活趋势</div>
          <div class="card-body" style="overflow:auto;">
            <table>
              <thead><tr><th>日期</th><th>日活</th><th>月活</th></tr></thead>
              <tbody id="d-trend"><tr><td colspan="3" class="text-center" style="color:#909399;">加载中...</td></tr></tbody>
            </table>
          </div>
        </div>
      </div>`

    fetchStats()
    fetchAnalysis()
  }

  async function fetchStats() {
    try {
      const res = await api.get('/api/admin/activity')
      const d = res.data || {}
      document.getElementById('d-total').textContent = d.totalUsers || 0
      document.getElementById('d-dau').textContent = d.todayDau || 0
      document.getElementById('d-mau').textContent = d.thisMonthMau || 0
      document.getElementById('d-paid').textContent = d.paidUsers || 0

      const trend = d.trend || []
      document.getElementById('d-trend').innerHTML = trend.length
        ? trend.map(t => `<tr><td>${t.date}</td><td class="text-center">${t.dau}</td><td class="text-center">${t.mau}</td></tr>`).join('')
        : '<tr><td colspan="3" class="text-center" style="color:#909399;">暂无数据</td></tr>'
    } catch (e) {
      console.error('Dashboard stats error:', e)
    }

    try {
      const r2 = await api.get('/api/admin/orders/summary')
      document.getElementById('d-revenue').textContent = '¥' + ((r2.data && r2.data.totalRevenue) || 0)
    } catch (e) {}
  }

  async function fetchAnalysis() {
    const container = document.getElementById('d-analysis')
    try {
      const res = await api.get('/api/lottery/analysis')
      if (res.code !== 200) return
      var d = res.data
      var periods = [100, 50, 20, 10]
      var pad = function(n){ return String(n).padStart(2,'0') }
      var html = ''
      periods.forEach(function(p){
        var data = d['periods' + p]
        if(!data) return
        html += '<div class="analysis-card">'
        html += '<div class="card-title">📊 近' + p + '期 <span class="badge">' + data.sampleSize + '期</span></div>'
        html += '<div class="analysis-body">'
        html += '<div class="analysis-left">'
        html += '<div class="stat-row"><span class="stat-label">🔥 红球热号</span>'
        data.redHot.forEach(function(n){ html += '<span class="ball ball-red">' + pad(n) + '</span>' })
        html += '</div>'
        html += '<div class="stat-row"><span class="stat-label">❄️ 红球冷号</span>'
        data.redCold.forEach(function(n){ html += '<span class="ball ball-red" style="opacity:0.5;">' + pad(n) + '</span>' })
        html += '</div>'
        html += '<div class="stat-row"><span class="stat-label">🔥 蓝球热号</span>'
        data.blueHot.forEach(function(n){ html += '<span class="ball ball-blue">' + pad(n) + '</span>' })
        html += '</div>'
        html += '<div class="stat-row"><span class="stat-label">❄️ 蓝球冷号</span>'
        data.blueCold.forEach(function(n){ html += '<span class="ball ball-blue" style="opacity:0.5;">' + pad(n) + '</span>' })
        html += '</div>'
        html += '</div>'
        html += '<div class="analysis-right">'
        html += '<div class="stat-row"><span class="stat-label">📐 和值区间</span><span>' + (data.topSumRange || '-') + '</span></div>'
        html += '<div class="stat-row"><span class="stat-label">📏 跨度区间</span><span>' + (data.topSpanRange || '-') + '</span></div>'
        html += '<div class="stat-row"><span class="stat-label">📊 高频区间比</span><span>' + (data.topZoneRatio || '-') + '</span></div>'
        html += '<div class="stat-row"><span class="stat-label">⚖️ 高频奇偶比</span><span>' + (data.topOddEven || '-') + '</span></div>'
        html += '</div></div></div>'
      })
      container.innerHTML = html
    } catch (e) {
      container.innerHTML = '<div style="color:#f56c6c;padding:20px;text-align:center;grid-column:1/-1;">分析数据加载失败</div>'
    }
  }

  return { render }
})()
