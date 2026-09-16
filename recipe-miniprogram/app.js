const store = require('./utils/store');
const theme = require('./utils/theme');

// tab 页路由（与 custom-tab-bar/index.js 的 list 顺序一致）
const TAB_ROUTES = [
  'pages/plaza/plaza',
  'pages/publish/publish',
  'pages/family/family',
  'pages/tools/tools',
  'pages/mine/mine'
];

App({
  globalData: {
    userInfo: null,
    token: wx.getStorageSync('recipe_token') || '',
    theme: theme.get(),
    plazaDirty: false
  },

  // 全局事件总线：跨页通信（替代 globalData 隐式契约）
  bus: {
    _handlers: {},
    on(event, fn) {
      (this._handlers[event] = this._handlers[event] || []).push(fn);
    },
    off(event, fn) {
      const list = this._handlers[event] || [];
      const i = list.indexOf(fn);
      if (i >= 0) list.splice(i, 1);
    },
    emit(event, payload) {
      (this._handlers[event] || []).forEach((fn) => fn(payload));
    }
  },

  onLaunch() {
    if (store.API.USE_CLOUD) {
      wx.cloud.init({ env: store.API.CLOUD_ENV, traceUser: false });
    }
    this.loadCuteFont();
    if (this.globalData.token) {
      store.fetchMe().then(u => {
        this.globalData.userInfo = u;
        // 通知已打开的页面：用户信息就绪（如广场页据此隐藏自己的关注按钮）
        this.bus.emit('user:ready', u);
      }).catch(() => {
        // token 失效：清理登录态，避免陷入"假登录"
        store.logout();
      });
    }
  },

  /** 可爱风标题手写体：网络加载站酷快乐体（免费商用），失败静默降级苹方
   *  注意：正式环境需在小程序后台把 cdn.jsdelivr.net 加入 downloadFile 合法域名 */
  loadCuteFont() {
    wx.loadFontFace({
      global: true,
      family: 'ZCOOL KuaiLe',
      source: 'url("https://cdn.jsdelivr.net/gh/googlefonts/zcool-kuaile@main/fonts/ttf/ZCOOLKuaiLe-Regular.ttf")',
      desc: { weight: 'normal', style: 'normal' },
      fail: () => {}
    });
  },

  /** 页面 onShow 统一调用：同步当前主题 + 皮肤背景 + 调色盘内联底色到页面 data */
  applyTheme(page) {
    if (!page) return;
    const key = this.globalData.theme;
    page.setData({ theme: key, skinBg: theme.getSkinBg(key), bgStyle: theme.getPageBgStyle(key) });
    theme.applyBackgroundColor(key);
    // tab 页：同步自定义 tabBar 的选中项（theme 由组件监听 theme:change 自行更新）
    if (typeof page.getTabBar === 'function') {
      const tb = page.getTabBar();
      if (tb) {
        const idx = TAB_ROUTES.indexOf(page.route || page.__route__ || '');
        if (idx >= 0) tb.setData({ selected: idx });
      }
    }
  }
});
