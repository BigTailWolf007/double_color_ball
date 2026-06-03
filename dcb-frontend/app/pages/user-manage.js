const UserManage = (() => {
  let state = { page: 1, size: 20, total: 0 }

  function render() {
    document.getElementById('main-content').innerHTML = `
      <div class="card" style="flex:1;display:flex;flex-direction:column;min-height:0;">
        <div class="card-header"><span>用户管理</span></div>
        <div class="card-body" style="flex:1;display:flex;flex-direction:column;min-height:0;overflow:hidden;">
          <div class="table-scroll" style="flex:1;min-height:0;">
            <table>
              <thead>
                <tr>
                  <th>昵称</th><th>用户名</th><th>角色</th><th>状态</th>
                  <th>订阅到期</th><th>注册时间</th><th>最后登录</th><th>操作</th>
                </tr>
              </thead>
              <tbody id="table-body"><tr><td colspan="8" class="text-center" style="color:#909399;">加载中...</td></tr></tbody>
            </table>
          </div>
          <div class="pagination" id="pagination"></div>
        </div>
      </div>`
    fetchList()
  }

  async function fetchList() {
    const tbody = document.getElementById('table-body')
    if (!tbody) return
    tbody.innerHTML = '<tr><td colspan="8" class="text-center" style="color:#909399;">加载中...</td></tr>'
    try {
      const res = await api.get('/api/admin/users', { page: state.page, size: state.size })
      const list = res.data.list || []
      state.total = res.data.total || 0

      if (!list.length) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center" style="color:#909399;">暂无数据</td></tr>'
      } else {
        tbody.innerHTML = list.map(u => `
          <tr>
            <td>${u.nickname || '-'}</td>
            <td>${u.username || '-'}</td>
            <td><span class="tag ${u.role==='ADMIN'?'tag-danger':'tag-info'}">${u.role}</span></td>
            <td><span style="color:${u.status===1?'#67c23a':'#f56c6c'}">${u.status===1?'正常':'禁用'}</span></td>
            <td>${u.subscribeExpireAt || '-'}</td>
            <td>${u.createdAt || '-'}</td>
            <td>${u.lastLoginAt || '-'}</td>
            <td>
              <button class="btn btn-link btn-sm ${u.status===1?'btn-danger':'btn-primary'}" data-id="${u.id}" data-status="${u.status}">
                ${u.status===1?'禁用':'启用'}
              </button>
            </td>
          </tr>`).join('')

        tbody.querySelectorAll('button[data-id]').forEach(btn => {
          btn.addEventListener('click', () => toggleStatus(btn.dataset.id, parseInt(btn.dataset.status)))
        })
      }

      renderPagination('pagination', state.page, state.size, state.total, (p, s) => {
        state.page = p; state.size = s; fetchList()
      })
    } catch (e) {
      tbody.innerHTML = '<tr><td colspan="8" class="text-center" style="color:#f56c6c;">加载失败</td></tr>'
    }
  }

  async function toggleStatus(id, currentStatus) {
    const newStatus = currentStatus === 1 ? 0 : 1
    try {
      await api.put('/api/admin/users/' + id + '/status', { status: newStatus })
      toast(newStatus === 1 ? '已启用' : '已禁用')
      fetchList()
    } catch (e) {}
  }

  return { render }
})()
