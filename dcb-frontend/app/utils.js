// ===== Toast 消息提示 =====
function toast(msg, type = 'success') {
  const el = document.createElement('div')
  el.className = `toast toast-${type}`
  el.textContent = msg
  document.getElementById('toast-container').appendChild(el)
  setTimeout(() => el.remove(), 3000)
}

// ===== 确认框 =====
function confirm(msg, title = '提示') {
  return new Promise((resolve, reject) => {
    document.getElementById('confirm-body').textContent = msg
    document.getElementById('confirm-title').textContent = title
    const overlay = document.getElementById('confirm-overlay')
    overlay.style.display = 'flex'

    const ok = document.getElementById('confirm-ok')
    const cancel = document.getElementById('confirm-cancel')

    function cleanup() {
      overlay.style.display = 'none'
      ok.replaceWith(ok.cloneNode(true))
      cancel.replaceWith(cancel.cloneNode(true))
    }

    document.getElementById('confirm-ok').addEventListener('click', () => {
      cleanup(); resolve()
    }, { once: true })

    document.getElementById('confirm-cancel').addEventListener('click', () => {
      cleanup(); reject()
    }, { once: true })
  })
}

// ===== 输入框弹窗 =====
function prompt(title, placeholder = '', validator) {
  return new Promise((resolve, reject) => {
    document.getElementById('prompt-title').textContent = title
    const input = document.getElementById('prompt-input')
    const error = document.getElementById('prompt-error')
    input.value = ''
    input.placeholder = placeholder || ''
    error.style.display = 'none'
    const overlay = document.getElementById('prompt-overlay')
    overlay.style.display = 'flex'
    input.focus()

    const ok = document.getElementById('prompt-ok')
    const cancel = document.getElementById('prompt-cancel')

    function cleanup() {
      overlay.style.display = 'none'
      ok.replaceWith(ok.cloneNode(true))
      cancel.replaceWith(cancel.cloneNode(true))
    }

    document.getElementById('prompt-ok').addEventListener('click', () => {
      const val = input.value.trim()
      if (validator) {
        const err = validator(val)
        if (err !== true && err) {
          error.textContent = err
          error.style.display = 'block'
          return
        }
      }
      if (!val) {
        error.textContent = '不能为空'
        error.style.display = 'block'
        return
      }
      cleanup(); resolve(val)
    }, { once: true })

    document.getElementById('prompt-cancel').addEventListener('click', () => {
      cleanup(); reject()
    }, { once: true })
  })
}

// ===== 弹窗 =====
function openModal(title, bodyHtml, footerHtml) {
  document.getElementById('modal-title').textContent = title
  document.getElementById('modal-body').innerHTML = bodyHtml
  document.getElementById('modal-footer').innerHTML = footerHtml
  document.getElementById('modal-overlay').style.display = 'flex'
}

function closeModal() {
  document.getElementById('modal-overlay').style.display = 'none'
}

document.getElementById('modal-close').addEventListener('click', closeModal)

// ===== 号码格式化 =====
function pad(n) { return String(n).padStart(2, '0') }

// ===== 渲染红球标签 =====
function renderReds(reds) {
  return (reds || []).map(n => `<span class="tag tag-danger">${pad(n)}</span>`).join('')
}

// ===== 渲染蓝球标签 =====
function renderBlue(blue) {
  return blue != null ? `<span class="tag tag-primary">${pad(blue)}</span>` : '-'
}

// ===== 中奖等级标签 =====
const PRIZE_TAG = { 1: 'warning', 2: 'warning', 3: 'danger', 4: 'danger', 5: 'success', 6: 'success', 0: 'info' }

function renderPrizeLevel(level, desc) {
  if (level === null || level === undefined) return '<span class="tag tag-info">待开奖</span>'
  return `<span class="tag tag-${PRIZE_TAG[level] || 'info'}">${desc || level}</span>`
}

// ===== 渲染分页 =====
function renderPagination(containerId, page, size, total, onChange) {
  const totalPages = Math.ceil(total / size) || 1
  const container = document.getElementById(containerId)
  if (!container) return

  let html = `<span>共 ${total} 条</span>`
  html += `<select class="page-size-select" id="${containerId}-size">`
  ;[20, 50, 100].forEach(s => {
    html += `<option value="${s}" ${s === size ? 'selected' : ''}>${s}条/页</option>`
  })
  html += `</select>`
  html += `<button class="page-btn" id="${containerId}-prev" ${page <= 1 ? 'disabled' : ''}>&lt;</button>`

  const start = Math.max(1, page - 2)
  const end = Math.min(totalPages, page + 2)
  for (let i = start; i <= end; i++) {
    html += `<button class="page-btn ${i === page ? 'active' : ''}" data-p="${i}">${i}</button>`
  }

  html += `<button class="page-btn" id="${containerId}-next" ${page >= totalPages ? 'disabled' : ''}>&gt;</button>`
  container.innerHTML = html

  container.querySelector(`#${containerId}-size`).addEventListener('change', e => {
    onChange(1, parseInt(e.target.value))
  })
  container.querySelector(`#${containerId}-prev`).addEventListener('click', () => {
    if (page > 1) onChange(page - 1, size)
  })
  container.querySelector(`#${containerId}-next`).addEventListener('click', () => {
    if (page < totalPages) onChange(page + 1, size)
  })
  container.querySelectorAll('[data-p]').forEach(btn => {
    btn.addEventListener('click', () => onChange(parseInt(btn.dataset.p), size))
  })
}

// ===== 号码选择器 =====
function renderBallPicker(containerId, options, selected, max, tagClass, onChange) {
  const container = document.getElementById(containerId)
  if (!container) return
  container.innerHTML = options.map(n => {
    const isSelected = Array.isArray(selected) ? selected.includes(n) : selected === n
    return `<span class="tag ${tagClass} clickable ${isSelected ? 'selected' : ''}" data-n="${n}">${pad(n)}</span>`
  }).join('')

  container.querySelectorAll('[data-n]').forEach(el => {
    el.addEventListener('click', () => {
      const n = parseInt(el.dataset.n)
      if (Array.isArray(selected)) {
        const idx = selected.indexOf(n)
        if (idx >= 0) selected.splice(idx, 1)
        else if (selected.length < max) selected.push(n)
        onChange(selected)
        renderBallPicker(containerId, options, selected, max, tagClass, onChange)
      } else {
        // 单选：通知外部更新值，然后用新值重渲染
        onChange(n)
        renderBallPicker(containerId, options, n, max, tagClass, onChange)
      }
    })
  })
}
