/**
 * 自定义 tabBar
 * 布局数值（安全区/悬浮间距/两侧缺口）在 JS 里算好、以内联 style 注入：
 * 组件 wxss 中 env(safe-area-inset-bottom) 与 calc() 混用会整条声明失效。
 * 换肤换底：监听 theme:change 事件（payload: { theme, skinBg }），背景色选择即时联动。
 */
const theme = require('../utils/theme');

Component({
  data: {
    selected: 0,
    theme: 'light',
    skinBg: '',        // 皮肤背景选择（glass/pink/custom），经典配色恒为空
    barBg: '',         // 经典自定义主题的 bar 底色（内联注入）
    padX: 0,          // 容器左右留白（px）：皮肤=胶囊两侧缺口，经典=0
    padBottom: 0,     // 容器底部留白（px）：皮肤=安全区+悬浮间距（上移的关键）
    barPadBottom: 0,  // bar 内部底部留白（px）：经典=安全区防 home indicator 压字
    list: [
      { path: '/pages/plaza/plaza', text: '广场', icon: 'grid' },
      { path: '/pages/publish/publish', text: '发布', icon: 'plus' },
      { path: '/pages/family/family', text: '队伍', icon: 'users' },
      { path: '/pages/tools/tools', text: '工具', icon: 'tool' },
      { path: '/pages/mine/mine', text: '我的', icon: 'user' }
    ]
  },

  lifetimes: {
    attached() {
      // 底部安全区高度：新 API 取 safeAreaInsets，老 API 用屏幕高 - safeArea.bottom
      this._safeBottom = 0;
      try {
        const win = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
        this._safeBottom = (win.safeAreaInsets && win.safeAreaInsets.bottom) || 0;
        if (!this._safeBottom && win.safeArea && win.screenHeight) {
          this._safeBottom = win.screenHeight - win.safeArea.bottom;
        }
      } catch (e) {}

      const app = getApp();
      this.applyTheme((app && app.globalData.theme) || 'light');
      this._onTheme = (payload) => {
        // 兼容旧调用（直接传 key 字符串）
        const obj = (typeof payload === 'string') ? { theme: payload } : (payload || {});
        const key = obj.theme || this.data.theme;
        const bg = (obj.skinBg !== undefined && key === this.data.theme)
          ? obj.skinBg
          : theme.getSkinBg(key);
        this.applyTheme(key, bg);
      };
      if (app && app.bus) app.bus.on('theme:change', this._onTheme);
    },
    detached() {
      const app = getApp();
      if (app && app.bus && this._onTheme) app.bus.off('theme:change', this._onTheme);
    }
  },

  methods: {
    /** 换主题/背景同时重算悬浮布局（皮肤才悬浮，经典平铺） */
    applyTheme(key, skinBg) {
      const skin = key === 'cute' || key === 'clean';
      const gap = key === 'cute' ? 5 : 4;
      const bg = skin ? (skinBg !== undefined ? skinBg : theme.getSkinBg(key)) : '';
      // 经典自定义主题：bar 底色用调色盘色，深色时文字反白
      let barBg = '';
      let barDark = false;
      if (key === 'custom') {
        const c = theme.getCustomColor('classic');
        if (c) {
          barBg = c;
          const r = parseInt(c.slice(1, 3), 16);
          const g = parseInt(c.slice(3, 5), 16);
          const b = parseInt(c.slice(5, 7), 16);
          barDark = (0.299 * r + 0.587 * g + 0.114 * b) < 128;
        }
      }
      this.setData({
        theme: key,
        skinBg: bg,
        barBg,
        barDark,
        padX: skin ? 10 : 0,
        padBottom: skin ? this._safeBottom + gap : 0,
        barPadBottom: skin ? 8 : this._safeBottom + 3
      });
    },

    switchTab(e) {
      const path = e.currentTarget.dataset.path;
      if (!path) return;
      wx.switchTab({ url: path });
    }
  }
});
