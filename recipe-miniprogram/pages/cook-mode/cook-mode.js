const store = require('../../utils/store');
const theme = require('../../utils/theme');

Page({

  onShow() {
    getApp().applyTheme(this);
  },
  data: {
    theme: theme.get(),
    navTitle: '照着做',
    id: null,
    recipe: null,
    steps: [],
    current: 0,
    keepScreenOn: false
  },

  onLoad(options) {
    this.setData({ id: Number(options.id) });
    wx.setKeepScreenOn({ keepScreenOn: true });
    this.setData({ keepScreenOn: true });
    store.fetchRecipeDetail(this.data.id).then((data) => {
      this.setData({ recipe: data.recipe, steps: data.steps || [] });
      this.setData({ navTitle: '照着做 · ' + data.recipe.title });
    }).catch((err) => wx.showToast({ title: err.message, icon: 'none' }));
  },

  onUnload() {
    wx.setKeepScreenOn({ keepScreenOn: false });
  },

  next() {
    if (this.data.current < this.data.steps.length - 1) {
      this.setData({ current: this.data.current + 1 });
    } else {
      wx.showToast({ title: '完成！', icon: 'success' });
    }
  },

  prev() {
    if (this.data.current > 0) {
      this.setData({ current: this.data.current - 1 });
    }
  },

  startTimer() {
    // 独立计时器：手动添加任意时长，作为步骤内联计时外的补充
    wx.navigateTo({ url: `/pages/timer/timer` });
  }
});
