const store = require('../../utils/store');
const theme = require('../../utils/theme');
const ui = require('../../utils/ui');

Page({
  data: {
    theme: theme.get(),
    navTitle: '主页',
    uid: null,
    profile: null,
    recipes: [],
    page: 0,
    size: 10,
    total: 0,
    loading: false,
    finished: false
  },

  onLoad(options) {
    getApp().applyTheme(this);
    this.setData({ uid: Number(options.id) });
    this.loadProfile();
    this.loadRecipes(true);
  },

  onShow() {
    getApp().applyTheme(this);
    // 从编辑资料页回来时刷新（头像/昵称/可见性可能变化）
    if (this.data.profile && this.data.profile.isSelf) {
      this.loadProfile();
    }
  },

  onPullDownRefresh() {
    Promise.all([this.loadProfile(), this.loadRecipes(true)]).then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.loading || this.data.finished || !this.data.profile) return;
    if (this.data.profile.locked && !this.data.profile.isSelf) return;
    this.loadRecipes(false);
  },

  loadProfile() {
    return store.fetchUserProfile(this.data.uid).then((profile) => {
      this.setData({ profile });
      if (profile.user && profile.user.nickname) {
        this.setData({ navTitle: profile.user.nickname + ' 的主页' });
      }
    }).catch((err) => {
      ui.fail(err, '加载主页失败');
    });
  },

  loadRecipes(reset) {
    if (this.data.loading) return Promise.resolve();
    const page = reset ? 0 : this.data.page;
    this.setData({ loading: true });
    return store.fetchUserRecipes(this.data.uid, page).then((res) => {
      const raw = res.content || [];
      const list = raw.map((it) => {
        const r = it.recipe || {};
        return Object.assign({}, r, {
          covers: Array.isArray(it.covers) && it.covers.length > 0 ? it.covers : [r.cover].filter(Boolean)
        });
      });
      if (reset) {
        this.setData({
          recipes: list,
          page: page + 1,
          total: res.total || 0,
          finished: (page + 1) * this.data.size >= (res.total || 0)
        });
      } else {
        const start = this.data.recipes.length;
        const patch = { page: page + 1, total: res.total || 0 };
        list.forEach((it, i) => { patch[`recipes[${start + i}]`] = it; });
        patch.finished = (page + 1) * this.data.size >= (res.total || 0);
        this.setData(patch);
      }
    }).catch(() => {}).finally(() => this.setData({ loading: false }));
  },

  /** 关注/取关（乐观更新 + 失败回滚） */
  onToggleFollow() {
    if (!store.getToken()) {
      return this.selectComponent('#login-popup').show();
    }
    const p = this.data.profile;
    const prev = { followed: p.followed, fansCount: p.fansCount };
    const next = !p.followed;
    this.setData({
      'profile.followed': next,
      'profile.fansCount': Math.max(0, (p.fansCount || 0) + (next ? 1 : -1))
    });
    store.toggleFollow(this.data.uid).then((res) => {
      this.setData({ 'profile.followed': res.followed, 'profile.fansCount': res.fansCount });
      wx.showToast({ title: res.followed ? '已关注' : '已取消关注', icon: 'none' });
    }).catch((err) => {
      this.setData({ 'profile.followed': prev.followed, 'profile.fansCount': prev.fansCount });
      wx.showToast({ title: err.message || '操作失败', icon: 'none' });
    });
  },

  /** 数据栏点击：进关注/粉丝列表 */
  onStatTap(e) {
    const type = e.currentTarget.dataset.type;
    const uid = this.data.profile.isSelf ? '' : `&uid=${this.data.uid}`;
    wx.navigateTo({ url: `/pages/follow-list/follow-list?type=${type}${uid}` });
  },

  onEditProfile() {
    wx.switchTab({ url: '/pages/mine/mine' });
  },

  onRecipeTap(e) {
    wx.navigateTo({ url: `/pages/recipe-detail/recipe-detail?id=${e.currentTarget.dataset.id}` });
  },

  onPreviewAvatar() {
    const url = this.data.profile.user.avatar;
    if (url) wx.previewImage({ urls: [url], current: url });
  },

  onLogined() {
    this.loadProfile();
  },

  onShareAppMessage() {
    const p = this.data.profile;
    return {
      title: p && p.user ? `来看看「${p.user.nickname || '厨友'}」的菜谱主页` : '厨友主页',
      path: `/pages/user-profile/user-profile?id=${this.data.uid}`
    };
  }
});
