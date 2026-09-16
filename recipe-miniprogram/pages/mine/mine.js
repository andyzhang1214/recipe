const store = require('../../utils/store');
const theme = require('../../utils/theme');
const ui = require('../../utils/ui');

Page({
  data: {
    theme: theme.get(),
    // 外观面板一级页签：cute / clean / classic（classic 泛指全部经典配色主题）
    styleTab: theme.get() === 'cute' || theme.get() === 'clean' ? theme.get() : 'classic',
    colorList: theme.GROUPS[1].items,
    skinBg: theme.getSkinBg(theme.get()),
    skinBgs: theme.SKIN_BGS[theme.get()] || [],
    skinCustomColor: theme.getCustomColor(theme.get()) || '',
    classicCustom: theme.getCustomColor('classic') || '',
    currentThemeName: '暖白',
    currentPreview: '#FAF6F1',
    showTheme: false,
    // 调色盘弹层
    showPicker: false,
    pickerScope: 'skin',   // skin：当前皮肤底色 / classic：经典自定义主题
    pickerColor: '#FFF6EF',
    pickerR: 255, pickerG: 246, pickerB: 239,
    presetColors: [
      '#FFF6EF', '#FFE4EC', '#FFD9B8', '#FFF3B8',
      '#E3F2DC', '#CDE8DC', '#D6EAF5', '#D9D2F0',
      '#F5DCF5', '#F2E6D5', '#E8E8E8', '#2B2B2B'
    ],
    user: null,
    showEditProfile: false,
    editNickname: '',
    editAvatar: '',
    savingProfile: false,
    followingCount: 0,
    fansCount: 0,
    showVisibility: false,
    visRecipe: null,
    visibilityOptions: [
      { value: 'PUBLIC', label: '公开', tip: '所有人都能查看你的主页和公开菜谱' },
      { value: 'FAMILY', label: '仅圈子可见', tip: '只有同队伍的成员能查看你的主页' },
      { value: 'PRIVATE', label: '私密', tip: '仅自己可见，他人只能看到私密账号提示' }
    ],
    editVisibility: 'PUBLIC',
    editVisibilityLabel: '公开',
    editVisibilityTip: '所有人都能查看你的主页和公开菜谱',
    tab: 'PUBLISHED',
    tabLabels: { PUBLISHED: '已发布', DRAFT: '草稿', FAVORITES: '收藏' },
    learningCount: 0,
    recipes: []
  },

  onShow() {
    getApp().applyTheme(this);
    this.syncThemeMeta();
    this.loadUser();
    this.loadRecipes();
    this.loadFollowingCount();
    this.loadLearningCount();
  },

  syncThemeMeta() {
    const cur = theme.THEMES.find((t) => t.key === this.data.theme) || theme.THEMES[0];
    this.setData({ currentThemeName: cur.name, currentPreview: cur.preview });
  },

  loadUser() {
    const token = store.getToken();
    if (!token) {
      this.setData({ user: null });
      return;
    }
    const app = getApp();
    if (app.globalData.userInfo) {
      this.setData({ user: app.globalData.userInfo });
    } else {
      store.fetchMe().then((u) => {
        app.globalData.userInfo = u;
        this.setData({ user: u });
      }).catch(() => this.setData({ user: null }));
    }
  },

  loadRecipes() {
    if (!store.getToken()) return;
    // 卡片装饰：标题首字符占位 + 封面图（covers 为 JSON 字符串需解析，取第一张）
    const decorate = (list) => (list || []).map((r) => {
      let covers = [];
      try {
        if (typeof r.covers === 'string' && r.covers) covers = JSON.parse(r.covers);
        else if (Array.isArray(r.covers)) covers = r.covers;
      } catch (e) { covers = []; }
      const cover = (covers.length && covers[0]) || r.cover || '';
      return Object.assign({}, r, { titleChar: String(r.title || '菜')[0], cover });
    });
    if (this.data.tab === 'FAVORITES') {
      store.fetchMyFavorites().then((list) => {
        this.setData({ recipes: decorate(list) });
      }).catch((err) => ui.fail(err, '加载收藏失败'));
      return;
    }
    store.fetchMyRecipes(this.data.tab).then((list) => {
      this.setData({ recipes: decorate(list) });
    }).catch((err) => ui.fail(err, '加载菜谱失败'));
  },

  onLoginTap() {
    this.selectComponent('#login-popup').show();
  },

  onLogined() {
    this.loadUser();
    this.loadRecipes();
    this.loadFollowingCount();
  },

  onLogout() {
    wx.showModal({
      title: '退出登录',
      content: '退出后需要重新登录才能发布和评论',
      success: (res) => {
        if (res.confirm) {
          store.logout();
          this.setData({ user: null, recipes: [], followingCount: 0, fansCount: 0 });
        }
      }
    });
  },

  // ---- 可见性快捷弹窗（锁标） ----
  onLockTap(e) {
    const idx = e.currentTarget.dataset.index;
    const recipe = this.data.recipes[idx];
    if (!recipe) return;
    this.setData({ showVisibility: true, visRecipe: recipe });
  },
  closeVisibility() {
    this.setData({ showVisibility: false, visRecipe: null });
  },
  onVisibilityPick(e) {
    const visibility = e.currentTarget.dataset.visibility;
    const recipe = this.data.visRecipe;
    if (!recipe || !visibility || recipe.visibility === visibility) {
      this.closeVisibility();
      return;
    }
    store.updateRecipe(recipe.id, { visibility }).then(() => {
      const recipes = this.data.recipes.map((r) =>
        r.id === recipe.id ? Object.assign({}, r, { visibility }) : r
      );
      const tips = { PUBLIC: '已设为公开', FAMILY: '已设为队伍可见', PRIVATE: '已设为私密' };
      this.setData({ recipes, showVisibility: false, visRecipe: null });
      wx.showToast({ title: tips[visibility] || '已更新', icon: 'success' });
    }).catch((err) => {
      this.setData({ showVisibility: false, visRecipe: null });
      ui.fail(err, '设置失败');
    });
  },

  onTab(e) {
    this.setData({ tab: e.currentTarget.dataset.tab }, () => this.loadRecipes());
  },

  onRecipeTap(e) {
    wx.navigateTo({ url: `/pages/recipe-detail/recipe-detail?id=${e.currentTarget.dataset.id}` });
  },

  // 编辑自己的菜谱：发布页是 tab 页无法带参，经 globalData 传递 id
  onEditRecipe(e) {
    const idx = e.currentTarget.dataset.index;
    const recipe = this.data.recipes[idx];
    if (!recipe) return;
    getApp().globalData.editRecipeId = recipe.id;
    wx.switchTab({ url: '/pages/publish/publish' });
  },

  // 删除自己的菜谱：弹窗确认后调用删除接口
  onDeleteRecipe(e) {
    const idx = e.currentTarget.dataset.index;
    const recipe = this.data.recipes[idx];
    if (!recipe) return;
    wx.showModal({
      title: '删除菜品',
      content: `确定删除「${recipe.title}」吗？删除后不可恢复。`,
      confirmText: '确认删除',
      confirmColor: '#e64340',
      cancelText: '取消',
      success: (res) => {
        if (!res.confirm) return;
        store.deleteRecipe(recipe.id).then(() => {
          wx.showToast({ title: '已删除', icon: 'success' });
          this.loadRecipes();
        }).catch((err) => ui.fail(err, '删除失败'));
      }
    });
  },

  loadFollowingCount() {
    if (!store.getToken()) {
      this.setData({ followingCount: 0, fansCount: 0 });
      return;
    }
    store.fetchMyFollows('following').then((list) => {
      this.setData({ followingCount: (list || []).length });
    }).catch(() => {});
    store.fetchMyFollows('fans').then((list) => {
      this.setData({ fansCount: (list || []).length });
    }).catch(() => {});
  },

  // 数据栏点按：关注 / 粉丝 列表
  onStatTap(e) {
    if (!store.getToken()) {
      return this.selectComponent('#login-popup').show();
    }
    const type = e.currentTarget.dataset.type === 'fans' ? 'fans' : 'following';
    wx.navigateTo({ url: `/pages/follow-list/follow-list?type=${type}` });
  },

  // 待学习菜谱：数量 + 跳转
  loadLearningCount() {
    if (!store.getToken()) {
      this.setData({ learningCount: 0 });
      return;
    }
    store.fetchLearning().then((list) => {
      this.setData({ learningCount: (list || []).length });
    }).catch(() => {});
  },
  onLearningTap() {
    wx.navigateTo({ url: '/pages/learning/learning' });
  },

  // ---- 头像昵称编辑 ----
  openEditProfile() {
    const u = this.data.user || {};
    const opt = this.data.visibilityOptions.find((v) => v.value === (u.profileVisibility || 'PUBLIC')) || this.data.visibilityOptions[0];
    this.setData({
      showEditProfile: true,
      editNickname: u.nickname || '',
      editAvatar: u.avatar || '',
      editVisibility: opt.value,
      editVisibilityLabel: opt.label,
      editVisibilityTip: opt.tip
    });
  },
  closeEditProfile() { this.setData({ showEditProfile: false }); },
  onEditAvatar(e) { this.setData({ editAvatar: e.detail.avatarUrl }); },
  onEditNickname(e) { this.setData({ editNickname: e.detail.value }); },
  onEditVisibility(e) {
    const opt = this.data.visibilityOptions[Number(e.detail.value)];
    if (!opt) return;
    this.setData({
      editVisibility: opt.value,
      editVisibilityLabel: opt.label,
      editVisibilityTip: opt.tip
    });
  },
  saveProfile() {
    const { editNickname, editAvatar, editVisibility, savingProfile } = this.data;
    if (savingProfile) return;
    if (!editNickname.trim()) {
      return wx.showToast({ title: '昵称不能为空', icon: 'none' });
    }
    const payload = { nickname: editNickname.trim(), profileVisibility: editVisibility };
    if (editAvatar) payload.avatar = editAvatar;
    this.setData({ savingProfile: true });
    store.updateProfile(payload).then((u) => {
      const app = getApp();
      const merged = Object.assign({}, app.globalData.userInfo || {}, u);
      app.globalData.userInfo = merged;
      this.setData({ user: merged, showEditProfile: false, savingProfile: false });
      wx.showToast({ title: '已保存', icon: 'success' });
    }).catch((err) => {
      this.setData({ savingProfile: false });
      ui.fail(err);
    });
  },

  // ---- 外观 / 主题 ----
  openTheme() {
    // 打开时一级页签跟随当前风格
    const t = this.data.theme;
    this.setData({
      showTheme: true,
      styleTab: t === 'cute' || t === 'clean' ? t : 'classic'
    });
  },
  closeTheme() { this.setData({ showTheme: false }); },

  /** 切换主题的公共入口：落库 + 本页生效 + 同步皮肤背景数据 */
  applyThemeKey(key) {
    theme.set(key);
    getApp().applyTheme(this);
    this.setData({
      skinBg: theme.getSkinBg(key),
      skinBgs: theme.SKIN_BGS[key] || [],
      skinCustomColor: (key === 'cute' || key === 'clean') ? (theme.getCustomColor(key) || '') : ''
    });
    this.syncThemeMeta();
  },

  /** 一级页签点击：切风格 banner 并展开对应颜色选择 */
  onStyleTap(e) {
    const style = e.currentTarget.dataset.style;
    if (style === this.data.styleTab) return;
    if (style === 'classic') {
      // 已在经典配色下只换页签；从皮肤切回则落到经典暖白
      if (this.data.theme === 'cute' || this.data.theme === 'clean') {
        this.applyThemeKey('light');
        wx.showToast({ title: '外观已切换', icon: 'none' });
      }
      this.setData({ styleTab: 'classic' });
    } else {
      this.setData({ styleTab: style });
      this.applyThemeKey(style);
      wx.showToast({ title: '外观已切换', icon: 'none' });
    }
  },

  onThemeChange(e) {
    this.applyThemeKey(e.currentTarget.dataset.key);
    wx.showToast({ title: '外观已切换', icon: 'none' });
  },

  // 背景色选项：预设三项直接切换，「自定义」打开调色盘
  onBgOptionTap(e) {
    const key = e.currentTarget.dataset.key;
    if (key === 'custom') {
      this.openColorPicker('skin');
      return;
    }
    if (!key || key === this.data.skinBg) return;
    theme.setSkinBg(this.data.theme, key);
    this.setData({ skinBg: key, bgStyle: '' });
    wx.showToast({ title: '背景已切换', icon: 'none' });
  },

  // 经典配色选项：自定义项打开调色盘，其余直接切主题
  onColorOptionTap(e) {
    const key = e.currentTarget.dataset.key;
    if (key === 'custom') {
      this.openColorPicker('classic');
      return;
    }
    this.onThemeChange(e);
  },

  // ---- 调色盘 ----
  openColorPicker(scope) {
    const cur = (scope === 'classic'
      ? theme.getCustomColor('classic')
      : theme.getCustomColor(this.data.theme)) || '#FFF6EF';
    this.setData({
      showPicker: true,
      pickerScope: scope,
      pickerColor: cur,
      pickerR: parseInt(cur.slice(1, 3), 16),
      pickerG: parseInt(cur.slice(3, 5), 16),
      pickerB: parseInt(cur.slice(5, 7), 16)
    });
  },
  closePicker() { this.setData({ showPicker: false }); },

  onPresetTap(e) {
    const c = e.currentTarget.dataset.color;
    this.setData({
      pickerColor: c,
      pickerR: parseInt(c.slice(1, 3), 16),
      pickerG: parseInt(c.slice(3, 5), 16),
      pickerB: parseInt(c.slice(5, 7), 16)
    });
  },

  onSlide(e) {
    const ch = e.currentTarget.dataset.ch;
    const v = e.detail.value;
    const patch = {};
    if (ch === 'r') patch.pickerR = v;
    else if (ch === 'g') patch.pickerG = v;
    else patch.pickerB = v;
    const r = patch.pickerR !== undefined ? patch.pickerR : this.data.pickerR;
    const g = patch.pickerG !== undefined ? patch.pickerG : this.data.pickerG;
    const b = patch.pickerB !== undefined ? patch.pickerB : this.data.pickerB;
    patch.pickerColor = '#' + [r, g, b].map((n) => ('0' + n.toString(16).toUpperCase()).slice(-2)).join('');
    this.setData(patch);
  },

  confirmPicker() {
    const c = this.data.pickerColor;
    if (this.data.pickerScope === 'classic') {
      theme.setCustomColor('classic', c);
      theme.set('custom'); // 切到经典自定义主题并广播/tabBar 联动
      getApp().applyTheme(this);
      this.syncThemeMeta();
      this.setData({ classicCustom: c, skinBg: '', skinBgs: [], skinCustomColor: '' });
    } else {
      const skin = this.data.theme;
      theme.setCustomColor(skin, c);
      theme.setSkinBg(skin, 'custom'); // 广播让 tabBar 换底 + 同步导航栏/窗口
      this.setData({
        skinBg: 'custom',
        skinCustomColor: c,
        bgStyle: theme.getPageBgStyle(skin)
      });
    }
    this.setData({ showPicker: false });
    wx.showToast({ title: '颜色已应用', icon: 'none' });
  }
});
