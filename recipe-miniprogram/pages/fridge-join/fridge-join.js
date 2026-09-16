const store = require('../../utils/store');
const theme = require('../../utils/theme');

Page({
  data: {
    theme: theme.get(),
    invite: '',
    loading: false,
    joined: false,
    ownerName: '',
    failed: false,
    message: '',
    wx_token: false
  },

  onLoad(options) {
    this.setData({ wx_token: !!store.getToken() });
    if (options && options.invite) {
      this.setData({ invite: options.invite });
      this.tryJoin();
    }
  },

  onShow() {
    getApp().applyTheme(this);
  },

  /** 打开分享链接即自动加入共享冰箱 */
  tryJoin() {
    if (!this.data.wx_token || this.data.loading || this.data.joined) return;
    if (!this.data.invite) return;
    this.setData({ loading: true, failed: false, message: '正在加入共享冰箱…' });
    store.joinFridgeByInvite(this.data.invite).then((res) => {
      this.setData({
        loading: false,
        joined: true,
        ownerName: res.ownerName || '好友',
        message: res.alreadyMember ? '你已经在该共享冰箱中了' : '已加入共享冰箱'
      });
    }).catch((err) => {
      this.setData({ loading: false, failed: true, message: err.message || '加入失败' });
    });
  },

  onLoginTap() {
    this.selectComponent('#login-popup').show();
  },

  onLogined() {
    this.setData({ wx_token: true });
    this.tryJoin();
  },

  enterFridge() {
    wx.redirectTo({ url: `/pages/fridge/fridge` });
  },

  backHome() {
    wx.switchTab({ url: '/pages/plaza/plaza' });
  }
});
