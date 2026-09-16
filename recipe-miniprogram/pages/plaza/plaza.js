const store = require('../../utils/store');
const theme = require('../../utils/theme');
const ui = require('../../utils/ui');

Page({
  data: {
    theme: theme.get(),
    banners: [],
    recipes: [],
    page: 0,
    size: 10,
    total: 0,
    keyword: '',
    loading: false,
    finished: false,
    statusBarHeight: 20,  // 自定义导航栏：状态栏高度（px）
    navBarHeight: 44,     // 导航栏内容高度（px，按胶囊位置推算）
    followedMap: {},    // {uid: true/false} 关注状态
    myId: null          // 当前登录用户 id：自己的卡片不显示关注按钮
  },

  onLoad() {
    // 自定义导航栏：状态栏高度 + 按胶囊位置推算导航行高度
    const win = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
    const statusBarHeight = win.statusBarHeight || 20;
    let navBarHeight = 44;
    try {
      const menu = wx.getMenuButtonBoundingClientRect();
      if (menu && menu.height) {
        navBarHeight = (menu.top - statusBarHeight) * 2 + menu.height;
      }
    } catch (e) {}
    this.setData({ statusBarHeight, navBarHeight });

    this.loadBanners();
    this.loadRecipes(true);
    // 冷启动时 app 的 fetchMe 晚于页面渲染，就绪后回填 myId（自己的卡片隐藏关注按钮）
    this._onUserReady = (u) => {
      this.setData({ myId: (u && u.id) || null });
      this.syncFollowStatus(this.data.recipes);
    };
    getApp().bus.on('user:ready', this._onUserReady);
  },

  onUnload() {
    if (this._onUserReady) getApp().bus.off('user:ready', this._onUserReady);
  },

  onShow() {
    getApp().applyTheme(this);
    this.setData({ myId: (getApp().globalData.userInfo && getApp().globalData.userInfo.id) || null });
    if (getApp().globalData.plazaDirty) {
      getApp().globalData.plazaDirty = false;
      this.loadRecipes(true);
    } else if (this.data.recipes.length) {
      // 从用户主页返回时刷新关注状态（可能在那里关注/取关过）
      this.syncFollowStatus(this.data.recipes);
    }
  },

  onLogined(e) {
    // 登录弹窗成功：事件直接携带 user（globalData 已同步，双保险）
    const u = (e.detail && e.detail.user) || getApp().globalData.userInfo;
    this.setData({ myId: (u && u.id) || null });
    this.syncFollowStatus(this.data.recipes);
  },

  onShareTimeline() {
    return { title: '菜谱广场 - 今天吃什么', query: '' };
  },

  onPullDownRefresh() {
    Promise.all([this.loadBanners(), this.loadRecipes(true)]).then(() => {
      wx.stopPullDownRefresh();
    });
  },

  onReachBottom() {
    if (this.data.loading || this.data.finished) return;
    this.loadRecipes(false);
  },

  loadBanners() {
    return store.fetchBanners().then((list) => {
      this.setData({ banners: list || [] });
    }).catch(() => this.setData({ banners: [] }));
  },

  loadRecipes(reset) {
    if (this.data.loading) return Promise.resolve();
    const page = reset ? 0 : this.data.page;
    this.setData({ loading: true });
    return store.fetchPublicRecipes(page, this.data.size, this.data.keyword)
      .then((res) => {
        // 后端返回 {recipe, covers[], authorNickname, authorAvatar, authorId}，平铺并保留 covers
        const raw = res.content || [];
        const list = raw.map((it) => {
          const r = it.recipe || {};
          return Object.assign({}, r, {
            covers: Array.isArray(it.covers) && it.covers.length > 0 ? it.covers : [r.cover].filter(Boolean),
            authorNickname: it.authorNickname,
            authorAvatar: it.authorAvatar,
            authorId: it.authorId
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
        this.syncFollowStatus(list);
      })
      .catch((err) => ui.fail(err, '加载失败'))
      .finally(() => this.setData({ loading: false }));
  },

  // 切换收藏：需要登录，乐观更新本地 favoritedMap
  onToggleFavorite(e) {
    const id = e.currentTarget.dataset.id;
    const idx = e.currentTarget.dataset.idx;
    if (!store.getToken()) {
      return wx.showToast({ title: '请先登录', icon: 'none' });
    }
    const current = this.data.recipes[idx].favorited;
    this.setData({ [`recipes[${idx}].favorited`]: !current });
    store.toggleFavorite(id).catch(() => {
      this.setData({ [`recipes[${idx}].favorited`]: current });
      wx.showToast({ title: '操作失败', icon: 'none' });
    });
  },

  // 点击封面图片：全屏预览
  onPreviewCover(e) {
    const img = e.currentTarget.dataset.img;
    if (!img) return;
    wx.previewImage({ urls: [img], current: img });
  },

  /** 加载当前页作者的批量关注状态 */
  syncFollowStatus(list) {
    if (!store.getToken() || !list.length) return;
    const uids = list.map((r) => r.authorId).filter((id) => id != null);
    if (!uids.length) return;
    store.fetchFollowStatus(uids).then((map) => {
      if (!map) return;
      const patch = {};
      Object.keys(map).forEach((uid) => { patch[`followedMap.${uid}`] = !!map[uid]; });
      this.setData(patch);
    }).catch(() => {});
  },

  // 关注/取关：乐观更新 + 失败回滚
  onToggleFollow(e) {
    if (!store.getToken()) {
      return this.selectComponent('#login-popup').show();
    }
    const uid = e.currentTarget.dataset.uid;
    const prev = !!this.data.followedMap[uid];
    this.setData({ [`followedMap.${uid}`]: !prev });
    store.toggleFollow(uid).then(() => {
      wx.showToast({ title: prev ? '已取消关注' : '已关注', icon: 'none' });
    }).catch((err) => {
      this.setData({ [`followedMap.${uid}`]: prev });
      wx.showToast({ title: err.message || '操作失败', icon: 'none' });
    });
  },

  /** 点作者行（头像/昵称）进用户主页 */
  onAuthorTap(e) {
    const uid = e.currentTarget.dataset.uid;
    if (uid) wx.navigateTo({ url: `/pages/user-profile/user-profile?id=${uid}` });
  },

  onSearchTap() {
    wx.navigateTo({ url: '/pages/search/search' });
  },

  onRecipeTap(e) {
    wx.navigateTo({ url: `/pages/recipe-detail/recipe-detail?id=${e.currentTarget.dataset.id}` });
  },

  onBannerTap(e) {
    const b = e.currentTarget.dataset.banner;
    if (b.linkType === 'RECIPE' && b.linkValue) {
      wx.navigateTo({ url: `/pages/recipe-detail/recipe-detail?id=${b.linkValue}` });
    }
  }
});
