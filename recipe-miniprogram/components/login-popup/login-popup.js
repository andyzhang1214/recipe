const store = require('../../utils/store');
const theme = require('../../utils/theme');

Component({
  data: {
    theme: theme.get(),
    visible: false,
    checking: false, // 静默登录检测中（区分新老用户）
    nickname: '',
    avatar: '',
    loading: false
  },
  lifetimes: {
    attached() {
      // 组件内同步主题：监听全局，切换主题时弹层内也生效
      this.setData({ theme: theme.get() });
    },
    pageLifetimes: {
      show() {
        this.setData({ theme: theme.get() });
      }
    }
  },
  methods: {
    show() {
      this.setData({ visible: true, checking: true, theme: theme.get(), nickname: '', avatar: '', loading: false });
      this.silentLogin();
    },
    hide() {
      this.setData({ visible: false });
    },
    /**
     * 先静默登录（wx.login 换 code → 后端识别 openid）：
     * - 已注册用户（昵称已完善）：直接登录成功，不弹头像昵称
     * - 新用户（isNew=true）：进入头像昵称收集表单，提交走 updateProfile 完成注册
     */
    silentLogin() {
      store.login().then((data) => {
        const app = getApp();
        app.globalData.userInfo = data;
        if (data && data.isNew) {
          // 新用户：进入资料完善步骤
          this.setData({ checking: false });
          return;
        }
        // 已注册用户：直接登录完成
        this.setData({ visible: false });
        wx.showToast({ title: '登录成功', icon: 'success' });
        this.triggerEvent('logined', { user: data });
      }).catch((err) => {
        // 静默登录失败：关闭弹窗并提示（网络/微信侧异常，重试由用户再次触发）
        this.setData({ visible: false, checking: false });
        wx.showToast({ title: err.message || '登录失败', icon: 'none' });
      });
    },
    onChooseAvatar(e) {
      this.setData({ avatar: e.detail.avatarUrl });
    },
    onNickname(e) {
      this.setData({ nickname: e.detail.value });
    },
    // 协议入口：跳转小程序内置的协议/隐私政策页
    onAgreement(e) {
      const { type } = e.currentTarget.dataset || {};
      const url = type === 'privacy' ? '/pages/privacy/privacy' : '/pages/agreement/agreement';
      wx.navigateTo({ url });
    },
    // 新用户提交资料：登录态已在静默登录时建立，这里只完善昵称头像
    onConfirm() {
      const { nickname, avatar, loading } = this.data;
      if (loading) return;
      const nick = (nickname || '').trim();
      if (!nick) {
        wx.showToast({ title: '请输入昵称', icon: 'none' });
        return;
      }
      this.setData({ loading: true });
      store.updateProfile({ nickname: nick, avatar: avatar || '' })
        .then((user) => {
          // 同步全局登录态：任何页面读 globalData.userInfo 都能立即拿到
          const app = getApp();
          app.globalData.userInfo = Object.assign({}, app.globalData.userInfo, user);
          this.setData({ visible: false, loading: false });
          wx.showToast({ title: '登录成功', icon: 'success' });
          this.triggerEvent('logined', { user: app.globalData.userInfo });
        })
        .catch((err) => {
          this.setData({ loading: false });
          wx.showToast({ title: err.message || '登录失败', icon: 'none' });
        });
    }
  }
});
