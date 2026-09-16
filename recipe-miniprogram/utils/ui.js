/**
 * 统一 UI 反馈：toast 收敛、错误降级文案
 */
function fail(err, fallback) {
  const msg = (err && err.message) || fallback || '网络开小差了';
  wx.showToast({ title: msg, icon: 'none' });
}

function success(msg) {
  wx.showToast({ title: msg || '操作成功', icon: 'success' });
}

module.exports = { fail, success };
