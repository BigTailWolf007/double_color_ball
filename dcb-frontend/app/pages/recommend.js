const Recommend = (() => {
  let state = {
    page: 1,
    pageSize: 20,
    total: 0,
    truncated: false,
    allGroups: [],
    excludeRed: [],
    includeBlue: [],
  }

  function render() {
    document.getElementById('main-content').innerHTML = `
      <div class="card">
        <div class="card-header"><span>号码推荐</span></div>
        <div class="card-body">

          <!-- 条件输入区 -->
          <div class="filter-bar" style="flex-wrap:wrap;gap:12px;align-items:flex-start;">
            <div style="display:flex;align-items:center;gap:8px;">
              <label>和值范围</label>
              <input class="form-input" id="sum-min" type="number" placeholder="最小值" style="width:90px;" />
              <span>~</span>
              <input class="form-input" id="sum-max" type="number" placeholder="最大值" style="width:90px;" />
            </div>
            <div style="display:flex;align-items:center;gap:8px;">
              <label>区间比</label>
              <input class="form-input" id="zone-ratio" type="text" placeholder="如 2:2:2" style="width:100px;" />
            </div>
            <div style="display:flex;align-items:center;gap:8px;">
              <label>奇偶比</label>
              <input class="form-input" id="odd-even-ratio" type="text" placeholder="如 3:3" style="width:100px;" />
            </div>
          </div>

          <!-- 剔除红球 -->
          <div style="margin:12px 0 4px;"><label>剔除红球</label></div>
          <div id="exclude-red-picker" style="display:flex;flex-wrap:wrap;gap:4px;"></div>

          <!-- 选择蓝球（必选1~3个） -->
          <div style="margin:12px 0 4px;"><label>选择蓝球 <span style="color:#909399;font-size:12px;">（必选，1~3个）</span></label></div>
          <div id="include-blue-picker" style="display:flex;flex-wrap:wrap;gap:4px;"></div>

          <!-- 操作按钮 -->
          <div style="margin:16px 0 0;display:flex;gap:8px;align-items:center;">
            <button class="btn btn-primary" id="btn-generate">生成号码</button>
            <button class="btn btn-default" id="btn-reset-form">重置条件</button>
          </div>

          <!-- 结果统计区（初始隐藏） -->
          <div id="result-summary" style="display:none;margin-top:20px;padding:12px;background:#f5f7fa;border-radius:4px;">
            <span id="result-count"></span>
            <span id="result-truncated" style="color:#e6a23c;margin-left:12px;display:none;">结果超过10000组，仅展示前10000组</span>
            <div style="margin-top:10px;display:flex;align-items:center;gap:8px;">
              <label>保存为预测（期号）</label>
              <input class="form-input" id="save-issue" type="text" placeholder="如：2024001" style="width:130px;" />
              <button class="btn btn-warning" id="btn-save-predict">保存预测</button>
            </div>
          </div>

          <!-- 结果列表区 -->
          <div id="result-list" style="display:none;margin-top:16px;">
            <div class="table-wrap">
              <table>
                <thead>
                  <tr><th style="width:60px;">序号</th><th>红球</th><th>蓝球</th></tr>
                </thead>
                <tbody id="result-tbody"></tbody>
              </table>
            </div>
            <div class="pagination" id="result-pagination"></div>
          </div>

        </div>
      </div>`

    // 渲染号码选择器
    renderBallPicker('exclude-red-picker',
      Array.from({length: 33}, (_, i) => i + 1),
      state.excludeRed, 33, 'tag-danger',
      vals => { state.excludeRed = vals }
    )
    renderBallPicker('include-blue-picker',
      Array.from({length: 16}, (_, i) => i + 1),
      state.includeBlue, 3, 'tag-primary',
      vals => { state.includeBlue = vals }
    )

    document.getElementById('btn-generate').addEventListener('click', handleGenerate)
    document.getElementById('btn-reset-form').addEventListener('click', handleReset)
    document.getElementById('btn-save-predict').addEventListener('click', handleSavePredict)
  }

  async function handleGenerate() {
    const sumMin = document.getElementById('sum-min').value.trim()
    const sumMax = document.getElementById('sum-max').value.trim()
    const zoneRatio = document.getElementById('zone-ratio').value.trim()
    const oddEvenRatio = document.getElementById('odd-even-ratio').value.trim()

    const body = {}
    if (sumMin !== '') body.sumMin = parseInt(sumMin, 10)
    if (sumMax !== '') body.sumMax = parseInt(sumMax, 10)
    if (zoneRatio !== '') body.zoneRatio = zoneRatio
    if (oddEvenRatio !== '') body.oddEvenRatio = oddEvenRatio
    if (state.excludeRed.length) body.excludeRed = [...state.excludeRed]
    if (!state.includeBlue.length) { toast('请至少选择1个蓝球', 'error'); return }
    body.includeBlue = [...state.includeBlue]
    body.page = 1
    body.pageSize = state.pageSize

    const btn = document.getElementById('btn-generate')
    btn.disabled = true
    btn.textContent = '计算中...'

    try {
      const res = await api.post('/api/recommend/generate', body)
      const data = res.data
      state.total = data.total
      state.truncated = data.truncated
      state.allGroups = data.list || []
      state.page = 1

      // 缓存当前查询条件，用于翻页时重新请求
      state.lastQuery = body

      showSummary()
      renderResultPage()
    } catch (e) {
      // api.js 已弹出错误 toast
    } finally {
      btn.disabled = false
      btn.textContent = '生成号码'
    }
  }

  function showSummary() {
    const summary = document.getElementById('result-summary')
    summary.style.display = 'block'
    document.getElementById('result-count').textContent = `共找到 ${state.total.toLocaleString()} 组号码`
    const truncEl = document.getElementById('result-truncated')
    truncEl.style.display = state.truncated ? 'inline' : 'none'
    document.getElementById('result-list').style.display = 'block'
  }

  async function fetchPage(page) {
    const body = { ...state.lastQuery, page, pageSize: state.pageSize }
    try {
      const res = await api.post('/api/recommend/generate', body)
      state.allGroups = res.data.list || []
      state.page = page
      renderResultPage()
    } catch (e) {}
  }

  function renderResultPage() {
    const tbody = document.getElementById('result-tbody')
    if (!tbody) return

    if (!state.allGroups.length) {
      tbody.innerHTML = '<tr><td colspan="3" class="text-center" style="color:#909399;">暂无符合条件的号码</td></tr>'
    } else {
      const offset = (state.page - 1) * state.pageSize
      tbody.innerHTML = state.allGroups.map((g, i) => `
        <tr>
          <td>${offset + i + 1}</td>
          <td>${renderReds(g.red)}</td>
          <td>${renderBlue(g.blue)}</td>
        </tr>`).join('')
    }

    renderPagination('result-pagination', state.page, state.pageSize, Math.min(state.total, 10000), (p, s) => {
      state.pageSize = s
      fetchPage(p)
    })
  }

  async function handleSavePredict() {
    const issue = document.getElementById('save-issue').value.trim()
    if (!issue) { toast('请输入期号', 'error'); return }
    if (!state.allGroups.length) { toast('没有可保存的号码', 'error'); return }

    try {
      await confirm(`确认将当前页 ${state.allGroups.length} 组号码保存为期号 ${issue} 的预测记录？`)
    } catch (e) { return }

    const btn = document.getElementById('btn-save-predict')
    btn.disabled = true
    btn.textContent = '保存中...'

    const results = await Promise.allSettled(
      state.allGroups.map(g => api.post('/api/predict/save', {
        issue,
        red1: g.red[0], red2: g.red[1], red3: g.red[2],
        red4: g.red[3], red5: g.red[4], red6: g.red[5],
        blue: g.blue
      }))
    )
    const successCount = results.filter(r => r.status === 'fulfilled').length
    const failCount = results.filter(r => r.status === 'rejected').length

    btn.disabled = false
    btn.textContent = '保存预测'
    toast(`保存完成：成功 ${successCount} 条${failCount ? `，失败 ${failCount} 条` : ''}`)
  }

  function handleReset() {
    state.excludeRed = []
    state.includeBlue = []
    state.total = 0
    state.truncated = false
    state.allGroups = []
    state.page = 1
    render()
  }

  return { render }
})()
