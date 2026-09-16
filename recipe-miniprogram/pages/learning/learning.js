const store = require('../../utils/store');
const theme = require('../../utils/theme');
const ui = require('../../utils/ui');

Page({
  data: {
    theme: theme.get(),
    list: [],
    loading: true
  },

  onLoad() {
    getApp().applyTheme(this);
  },

  onShow() {
    getApp().applyTheme(this);
    this.load();
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh());
  },

  load() {
    return store.fetchLearning().then((list) => {
      const items = (list || []).map((it, idx) => Object.assign({}, it, {
        no: String(idx + 1).padStart(2, '0'),
        dateText: it.createdAt ? String(it.createdAt).substring(5, 10).replace('-', '/') : ''
      }));
      this.setData({ list: items, loading: false });
    }).catch((err) => {
      this.setData({ loading: false });
      ui.fail(err, '加载失败');
    });
  },

  /** 去发布：带入菜名，发布成功自动移出清单 */
  goPublish(e) {
    const dish = e.currentTarget.dataset.dish;
    const id = e.currentTarget.dataset.id;
    getApp().globalData.publishPrefill = { dish, learningId: id };
    wx.switchTab({ url: '/pages/publish/publish' });
  },

  /** 移除（不学了/已经会了） */
  onRemove(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '移出待学习',
      content: '确认把这道菜移出待学习清单？',
      confirmText: '移出',
      success: (res) => {
        if (!res.confirm) return;
        store.deleteLearning(id).then(() => {
          wx.showToast({ title: '已移出', icon: 'none' });
          this.load();
        }).catch((err) => wx.showToast({ title: err.message || '操作失败', icon: 'none' }));
      }
    });
  }
});
