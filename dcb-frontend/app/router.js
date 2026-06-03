// ===== 页面模块注册 =====
const PAGES = {
  'dashboard':      () => Dashboard.render(),
  'calc-error-log': () => CalcErrorLogList.render(),
  'lottery-list':   () => LotteryList.render(),
  'lottery-import': () => LotteryImport.render(),
  'purchase-list':  () => PurchaseList.render(),
  'purchase-add':   () => PurchaseAdd.render(),
  'predict-list':   () => PredictList.render(),
  'recommend':      () => Recommend.render(),
  'sys-config':     () => SysConfig.render(),
  'user-manage':    () => UserManage.render(),
  'user-activity':  () => UserActivity.render(),
  'order-manage':   () => OrderManage.render(),
}
