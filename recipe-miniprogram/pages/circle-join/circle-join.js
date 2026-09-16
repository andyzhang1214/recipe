const store = require('../../utils/store');
const theme = require('../../utils/theme');

Page({

  onShow() {
    getApp().applyTheme(this);
  },
  data: {
    theme: theme.get(),
    invite: '',
    familyId: null,
    familyName: '',
    loading: false,
    joined: false,
    already: false,
    failed: false,
    message: '',
    wx_token: false
  },

  onLoad(options) {
    this.setData({ wx_token: !!store.getToken() });
    // 两种落地：?invite=INV-xxx（生成邀请的分享卡片/二维码）或 ?id=xx（直接转发队伍）
    if (options.invite) {
      this.setData({ invite: options.invite });
    } else if (options.id) {
      this.setData({ familyId: Number(options.id) });
    }
    // 分享即邀请：打开即自动加入，无需点击
    this.tryJoin();
  },

  tryJoin() {
    if (!this.data.wx_token || this.data.loading || this.data.joined) return;
    let task = null;
    if (this.data.invite) {
      task = store.joinByInvite(this.data.invite);
    } else if (this.data.familyId) {
      task = store.joinFamilyById(this.data.familyId);
    }
    if (!task) return;
    this.setData({ loading: true, failed: false, message: '正在加入队伍…' });
    task.then((res) => {
      this.setData({
        loading: false,
        joined: true,
        already: !!(res && res.alreadyMember),
        familyId: (res && res.familyId) || this.data.familyId,
        message: res && res.alreadyMember ? '你已经是队伍成员了' : '已加入队伍，快去看看吧'
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

  enterCircle() {
    wx.redirectTo({ url: `/pages/circle-detail/circle-detail?id=${this.data.familyId}` });
  },

  backHome() {
    wx.switchTab({ url: '/pages/plaza/plaza' });
  }
});
