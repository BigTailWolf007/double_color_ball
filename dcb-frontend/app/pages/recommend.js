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
              <input class="form-input" id="sum-min" type="number" min="21" max="183" placeholder="21~183" style="width:90px;" />
              <span>~</span>
              <input class="form-input" id="sum-max" type="number" min="21" max="183" placeholder="21~183" style="width:90px;" />
            </div>
            <div style="display:flex;align-items:center;gap:8px;">
              <label>区间比 <span style="color:#909399;font-size:12px;">低:中:高</span></label>
              <input class="form-input zone-input" id="zone-low"  type="number" min="0" max="6" placeholder="低" style="width:56px;" />
              <span>:</span>
              <input class="form-input zone-input" id="zone-mid"  type="number" min="0" max="6" placeholder="中" style="width:56px;" />
              <span>:</span>
              <input class="form-input zone-input" id="zone-high" type="number" min="0" max="6" placeholder="高" style="width:56px;" />
            </div>
            <div style="display:flex;align-items:center;gap:8px;">
              <label>奇偶比 <span style="color:#909399;font-size:12px;">奇:偶</span></label>
              <input class="form-input" id="odd-count"  type="number" min="0" max="6" placeholder="奇" style="width:56px;" />
              <span>:</span>
              <input class="form-input" id="even-count" type="number" min="0" max="6" placeholder="偶" style="width:56px;" />
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

  // 读取并校验输入，返回 { ok, body, error }
  function readAndValidate() {
    const sumMinVal = document.getElementById('sum-min').value.trim()
    const sumMaxVal = document.getElementById('sum-max').value.trim()
    const zoneLow  = document.getElementById('zone-low').value.trim()
    const zoneMid  = document.getElementById('zone-mid').value.trim()
    const zoneHigh = document.getElementById('zone-high').value.trim()
    const oddVal   = document.getElementById('odd-count').value.trim()
    const evenVal  = document.getElementById('even-count').value.trim()

    const body = {}

    // 和值校验：21~183，最小值不能大于最大值
    if (sumMinVal !== '') {
      const v = parseInt(sumMinVal, 10)
      if (v < 21 || v > 183) return { ok: false, error: '和值最小值范围为 21~183' }
      body.sumMin = v
    }
    if (sumMaxVal !== '') {
      const v = parseInt(sumMaxVal, 10)
      if (v < 21 || v > 183) return { ok: false, error: '和值最大值范围为 21~183' }
      body.sumMax = v
    }
    if (body.sumMin !== undefined && body.sumMax !== undefined && body.sumMin > body.sumMax) {
      return { ok: false, error: '和值最小值不能大于最大值' }
    }

    // 区间比校验：三个框要么全填要么全空，每项 0~6，三项之和必须为 6
    const zoneEmpty = zoneLow === '' && zoneMid === '' && zoneHigh === ''
    const zoneFull  = zoneLow !== '' && zoneMid !== '' && zoneHigh !== ''
    if (!zoneEmpty && !zoneFull) return { ok: false, error: '区间比请填写全部三项（低:中:高）' }
    if (zoneFull) {
      const zl = parseInt(zoneLow, 10), zm = parseInt(zoneMid, 10), zh = parseInt(zoneHigh, 10)
      if ([zl, zm, zh].some(v => isNaN(v) || v < 0 || v > 6)) return { ok: false, error: '区间比每项范围为 0~6' }
      if (zl + zm + zh !== 6) return { ok: false, error: '区间比三项之和必须等于 6' }
      body.zoneRatio = `${zl}:${zm}:${zh}`
    }

    // 奇偶比校验：两个框要么全填要么全空，每项 0~6，两项之和必须为 6
    const oeEmpty = oddVal === '' && evenVal === ''
    const oeFull  = oddVal !== '' && evenVal !== ''
    if (!oeEmpty && !oeFull) return { ok: false, error: '奇偶比请填写全部两项（奇:偶）' }
    if (oeFull) {
      const ov = parseInt(oddVal, 10), ev = parseInt(evenVal, 10)
      if ([ov, ev].some(v => isNaN(v) || v < 0 || v > 6)) return { ok: false, error: '奇偶比每项范围为 0~6' }
      if (ov + ev !== 6) return { ok: false, error: '奇偶比两项之和必须等于 6' }
      body.oddEvenRatio = `${ov}:${ev}`
    }

    if (state.excludeRed.length) body.excludeRed = [...state.excludeRed]
    if (!state.includeBlue.length) return { ok: false, error: '请至少选择1个蓝球' }
    body.includeBlue = [...state.includeBlue]

    return { ok: true, body }
  }

  async function handleGenerate() {
    const { ok, body, error } = readAndValidate()
    if (!ok) { toast(error, 'warning'); return }

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
      state.lastQuery = body
      showSummary()
      renderResultPage()
    } catch (e) {
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

    const seen = new Set()
    const payload = []
    for (const g of state.allGroups) {
      const key = [...g.red].sort((a, b) => a - b).join(',') + '|' + g.blue
      if (seen.has(key)) continue
      seen.add(key)
      payload.push({
        issue,
        red1: g.red[0], red2: g.red[1], red3: g.red[2],
        red4: g.red[3], red5: g.red[4], red6: g.red[5],
        blue: g.blue
      })
    }

    try {
      await api.post('/api/predict/save', payload)
      toast(`保存完成：共保存 ${payload.length} 条${state.allGroups.length - payload.length > 0 ? `，去重 ${state.allGroups.length - payload.length} 条` : ''}`)
    } catch (e) {}

    btn.disabled = false
    btn.textContent = '保存预测'
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
