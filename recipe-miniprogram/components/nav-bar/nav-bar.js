// 通用自定义导航栏：与广场页同款标题字体（--font-title）
// 状态栏高度 + 胶囊推算导航行高度，与 plaza.js 保持一致
Component({
  properties: {
    title: { type: String, value: '' },
    back: { type: Boolean, value: false },
    theme: { type: String, value: '' }
  },
  data: {
    statusBarHeight: 20,
    navBarHeight: 44
  },
  lifetimes: {
    attached() {
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
    }
  },
  methods: {
    onBack() {
      // 分享直达等无栈场景回退到广场 tab
      wx.navigateBack({
        fail: () => wx.switchTab({ url: '/pages/plaza/plaza' })
      });
    }
  }
});
