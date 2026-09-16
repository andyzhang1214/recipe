const store = require('../../utils/store');
const theme = require('../../utils/theme');
const ui = require('../../utils/ui');

Page({
  data: {
    theme: theme.get(),
    navTitle: '关注',
    type: 'following',
    uid: null,
    list: [],
    loading: false
  },

  onLoad(options) {
    getApp().applyTheme(this);
    this.setData({
      type: options.type === 'fans' ? 'fans' : 'following',
      uid: options.uid ? Number(options.uid) : null
    });
    this.setData({ navTitle: this.data.uid ? (this.data.type === 'fans' ? 'TA 的粉丝' : 'TA 的关注') : (this.data.type === 'fans' ? '我的粉丝' : '我的关注') });
    this.load();
  },

  onShow() {
    getApp().applyTheme(this);
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh());
  },

  load() {
    if (this.data.loading) return Promise.resolve();
    this.setData({ loading: true });
    const task = this.data.uid
      ? store.fetchUserFollows(this.data.uid, this.data.type)
      : store.fetchMyFollows(this.data.type);
    return task.then((list) => {
      this.setData({ list: list || [] });
    }).catch((err) => ui.fail(err, '加载失败'))
      .finally(() => this.setData({ loading: false }));
  },

  onTypeTap(e) {
    const type = e.currentTarget.dataset.type;
    if (type === this.data.type) return;
    this.setData({ type }, () => {
      this.setData({ navTitle: this.data.uid ? (this.data.type === 'fans' ? 'TA 的粉丝' : 'TA 的关注') : (this.data.type === 'fans' ? '我的粉丝' : '我的关注') });
      this.load();
    });
  },

  /** 行内关注/取关（乐观更新 + 失败回滚） */
  onToggleFollow(e) {
    if (!store.getToken()) {
      return this.selectComponent('#login-popup').show();
    }
    const uid = Number(e.currentTarget.dataset.id);
    const idx = e.currentTarget.dataset.idx;
    const prev = this.data.list[idx].followed;
    this.setData({ [`list[${idx}].followed`]: !prev });
    store.toggleFollow(uid).catch(() => {
      this.setData({ [`list[${idx}].followed`]: prev });
      wx.showToast({ title: '操作失败', icon: 'none' });
    });
  },

  onUserTap(e) {
    wx.navigateTo({ url: `/pages/user-profile/user-profile?id=${e.currentTarget.dataset.id}` });
  },

  onLogined() {
    this.load();
  }
});
