const theme = require('../../utils/theme');
const store = require('../../utils/store');

Page({
  data: {
    theme: theme.get(),
    tools: [
      { key: 'calorie', name: '热量查询', desc: '食品/调料每100g热量', icon: '🔥', needLogin: false },
      { key: 'fridge', name: '冰箱', desc: '临期食品提醒·拍照备注', icon: '🧊', needLogin: true },
      { key: 'timer', name: '烹饪倒计时', desc: '鸡汤50分钟·排骨30分钟', icon: '⏱', needLogin: false }
    ]
  },

  onShow() {
    getApp().applyTheme(this);
  },

  onToolTap(e) {
    const tool = this.data.tools[e.currentTarget.dataset.idx];
    const urlMap = {
      calorie: '/pages/calorie/calorie',
      fridge: '/pages/fridge/fridge',
      timer: '/pages/timer/timer'
    };
    if (tool.needLogin && !store.getToken()) {
      this.selectComponent('#login-popup').show();
      return;
    }
    wx.navigateTo({ url: urlMap[tool.key] });
  },

  onLogined() {}
});
