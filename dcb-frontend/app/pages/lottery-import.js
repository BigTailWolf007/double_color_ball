const LotteryImport = (() => {
  function render() {
    document.getElementById('main-content').innerHTML = `
      <div class="card">
        <div class="card-header">
          <span>TXT 文件导入开奖号码</span>
        </div>
        <div class="card-body">
          <div class="alert alert-info">
            文件格式：每行一条记录，格式为 <code>期号 红1 红2 红3 红4 红5 红6 蓝球</code>，例如：
            <code>2024001 01 05 12 18 25 33 07</code>
          </div>

          <div class="upload-area" id="upload-area">
            <div class="upload-icon">📂</div>
            <div>拖拽 TXT 文件到此处，或 <em>点击上传</em></div>
            <input type="file" id="file-input" accept=".txt" style="display:none;" />
          </div>

          <div id="import-result"></div>
        </div>
      </div>`

    const area = document.getElementById('upload-area')
    const fileInput = document.getElementById('file-input')

    area.addEventListener('click', () => fileInput.click())
    fileInput.addEventListener('change', () => {
      if (fileInput.files[0]) handleUpload(fileInput.files[0])
    })

    area.addEventListener('dragover', e => { e.preventDefault(); area.classList.add('dragover') })
    area.addEventListener('dragleave', () => area.classList.remove('dragover'))
    area.addEventListener('drop', e => {
      e.preventDefault()
      area.classList.remove('dragover')
      const file = e.dataTransfer.files[0]
      if (file) handleUpload(file)
    })
  }

  async function handleUpload(file) {
    const area = document.getElementById('upload-area')
    area.innerHTML = '<div style="color:#409eff;">上传中...</div>'
    const formData = new FormData()
    formData.append('file', file)
    try {
      const res = await api.postForm('/api/lottery/import', formData)
      const r = res.data
      toast(`导入完成：成功 ${r.success} 条`)
      // 导入成功后，对所有成功导入的期号触发购买记录盈亏计算
      if (r.success > 0 && r.issues && r.issues.length) {
        Promise.allSettled(r.issues.map(issue => api.post(`/api/purchase/calc/${issue}`)))
      }
      renderResult(r)
    } catch (e) {
      area.innerHTML = `
        <div class="upload-icon">📂</div>
        <div>拖拽 TXT 文件到此处，或 <em>点击上传</em></div>
        <input type="file" id="file-input" accept=".txt" style="display:none;" />`
      document.getElementById('file-input').addEventListener('change', ev => {
        if (ev.target.files[0]) handleUpload(ev.target.files[0])
      })
      document.getElementById('upload-area').addEventListener('click', () => {
        document.getElementById('file-input').click()
      })
    }
  }

  function renderResult(r) {
    const container = document.getElementById('import-result')
    if (!container) return
    let html = `
      <div class="import-result">
        <div style="font-weight:bold;margin-bottom:10px;">导入结果</div>
        <div class="import-result-grid">
          <div class="import-result-item">
            <label>成功</label>
            <span class="tag tag-success">${r.success} 条</span>
          </div>
          <div class="import-result-item">
            <label>跳过（重复）</label>
            <span class="tag tag-warning">${r.skip} 条</span>
          </div>
          <div class="import-result-item">
            <label>失败</label>
            <span class="tag tag-danger">${r.fail} 条</span>
          </div>
        </div>`

    if (r.failDetails && r.failDetails.length) {
      html += `<details class="fail-details">
        <summary>失败明细（${r.failDetails.length} 条）</summary>
        ${r.failDetails.map(m => `<div class="fail-detail-item">${m}</div>`).join('')}
      </details>`
    }

    html += '</div>'
    container.innerHTML = html
  }

  return { render }
})()
