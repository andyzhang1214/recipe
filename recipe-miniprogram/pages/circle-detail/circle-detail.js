const store = require('../../utils/store');
const theme = require('../../utils/theme');

Page({

  data: {
    theme: theme.get(),
    navTitle: '队伍详情',
    id: null,
    family: null,
    myRole: '',
    memPct: 4,
    members: [],
    approvedCount: 0,
    pendingCount: 0,
    invites: [],
    showSettings: false,
    editName: '',
    editMax: '',
    editAvatar: '',
    savingSettings: false,
    showInvite: false,
    showCodeGen: false,
    inviteValidDays: 7,
    inviteCreating: false,
    inviteToken: '',
    inviteExpires: '',
    // 心愿单（仅 PENDING，完成后自动移出）
    meals: [],
    showMealForm: false,
    mealDish: '',
    mealNote: '',
    mealSubmitting: false,
    // 圈员菜品
    recipes: []
  },

  onLoad(options) {
    this.setData({ id: Number(options.id) });
    this.load();
    this.loadMeals();
    this.loadRecipes();
  },

  onShow() {
    getApp().applyTheme(this);
    // 从菜谱详情等页面返回时刷新心愿与菜品
    if (this.data.id) {
      this.loadMeals();
      this.loadRecipes();
    }
  },

  load() {
    store.fetchFamilyDetail(this.data.id).then((data) => {
      const fam = data.family;
      const max = fam.maxMembers || 1;
      const cnt = fam.memberCount || 0;
      this.setData({
        family: fam,
        myRole: data.myRole,
        editName: fam.name,
        editMax: String(fam.maxMembers),
        editAvatar: fam.avatar || '',
        memPct: Math.min(100, Math.max(4, Math.round(cnt / max * 100)))
      });
      this.setData({ navTitle: fam.name });
    }).catch((err) => wx.showToast({ title: err.message, icon: 'none' }));

    store.fetchFamilyMembers(this.data.id).then((members) => {
      // WXML 不支持字符串下标，头像首字符在 JS 侧预处理
      const list = (members || []).map((m) => {
        const nick = String(m.nickname || '');
        return Object.assign({}, m, {
          avatarChar: /^\d+$/.test(nick) ? '友' : (nick.charAt(0) || '友')
        });
      });
      this.setData({
        members: list,
        approvedCount: list.filter((m) => m.status === 'APPROVED').length,
        pendingCount: list.filter((m) => m.status === 'PENDING').length
      });
    }).catch(() => {});

    store.fetchInvites(this.data.id).then((invites) => {
      this.setData({ invites: invites || [] });
    }).catch(() => {});
  },

  // ---- 心愿单 ----
  loadMeals() {
    store.fetchMealOverview().then((circles) => {
      const circle = (circles || []).find((c) => c.familyId === this.data.id);
      const meals = ((circle && circle.list) || []).filter((m) => m.status === 'PENDING');
      this.setData({ meals });
    }).catch(() => {});
  },

  loadRecipes() {
    store.fetchFamilyRecipes(this.data.id).then((recipes) => {
      this.setData({ recipes: recipes || [] });
    }).catch(() => {});
  },

  openMealForm() { this.setData({ showMealForm: true, mealDish: '', mealNote: '' }); },
  closeMealForm() { this.setData({ showMealForm: false }); },
  onMealDish(e) { this.setData({ mealDish: e.detail.value }); },
  onMealNote(e) { this.setData({ mealNote: e.detail.value }); },

  confirmMeal() {
    const { mealDish, mealNote, mealSubmitting } = this.data;
    if (mealSubmitting) return;
    if (!mealDish.trim()) {
      return wx.showToast({ title: '想吃什么不能为空', icon: 'none' });
    }
    this.setData({ mealSubmitting: true });
    store.submitMealRequest({
      familyId: this.data.id,
      dishName: mealDish.trim(),
      note: mealNote.trim() || null
    }).then(() => {
      wx.showToast({ title: '已许愿 ' + mealDish.trim(), icon: 'none' });
      this.setData({ showMealForm: false, mealSubmitting: false });
      this.loadMeals();
    }).catch((err) => {
      this.setData({ mealSubmitting: false });
      wx.showToast({ title: err.message || '提交失败', icon: 'none' });
    });
  },

  /** 去完成：跳发布页并带入菜名，发布成功后自动移出心愿单 */
  goComplete(e) {
    const dish = e.currentTarget.dataset.dish;
    const mealId = e.currentTarget.dataset.id;
    getApp().globalData.publishPrefill = { dish, mealId };
    wx.switchTab({ url: '/pages/publish/publish' });
  },

  /** 我也不会：移出心愿单，进入我的待学习清单 */
  toLearning(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '加入待学习',
      content: '把这道菜加入你的待学习清单？加入后将移出心愿单',
      confirmText: '加入',
      success: (res) => {
        if (!res.confirm) return;
        store.learningFromMeal(id).then(() => {
          wx.showToast({ title: '已加入待学习', icon: 'success' });
          this.loadMeals();
        }).catch((err) => wx.showToast({ title: err.message || '操作失败', icon: 'none' }));
      }
    });
  },

  /** 已发布心愿：点卡片或「查看菜谱」跳菜谱详情 */
  onWishInfoTap(e) {
    const rid = e.currentTarget.dataset.rid;
    const status = e.currentTarget.dataset.status;
    if (status !== 'DONE' || !rid) return;
    wx.navigateTo({ url: `/pages/recipe-detail/recipe-detail?id=${rid}` });
  },
  onDoneRecipeTap(e) {
    const rid = e.currentTarget.dataset.rid;
    if (!rid) return;
    wx.navigateTo({ url: `/pages/recipe-detail/recipe-detail?id=${rid}` });
  },

  onRecipeTap(e) {
    wx.navigateTo({ url: `/pages/recipe-detail/recipe-detail?id=${e.currentTarget.dataset.id}` });
  },

  // ---- 审批（存量待审数据；新加入均已直通） ----
  onApprove(e) {
    const memberId = e.currentTarget.dataset.memberId;
    store.approveMember(this.data.id, memberId, true).then(() => {
      wx.showToast({ title: '已通过', icon: 'success' });
      this.load();
    }).catch((err) => wx.showToast({ title: err.message || '操作失败', icon: 'none' }));
  },
  onReject(e) {
    const memberId = e.currentTarget.dataset.memberId;
    store.approveMember(this.data.id, memberId, false).then(() => {
      wx.showToast({ title: '已拒绝', icon: 'none' });
      this.load();
    }).catch((err) => wx.showToast({ title: err.message || '操作失败', icon: 'none' }));
  },
  onRemove(e) {
    const memberId = e.currentTarget.dataset.memberId;
    wx.showModal({
      title: '移出成员',
      content: '确认将该成员移出队伍？',
      success: (res) => {
        if (res.confirm) {
          store.removeMember(this.data.id, memberId).then(() => this.load());
        }
      }
    });
  },

  // ---- 设置（改名/改上限） ----
  openSettings() { this.setData({ showSettings: true }); },
  closeSettings() { this.setData({ showSettings: false }); },
  onEditName(e) { this.setData({ editName: e.detail.value }); },
  onEditMax(e) { this.setData({ editMax: e.detail.value }); },
  saveSettings() {
    if (this.data.savingSettings) return;
    const max = this.data.editMax ? Number(this.data.editMax) : 99;
    if (max < 1 || max > 99) return wx.showToast({ title: '上限需 1-99', icon: 'none' });
    this.setData({ savingSettings: true });
    store.updateFamily(this.data.id, {
      name: this.data.editName.trim(),
      maxMembers: max,
      avatar: this.data.editAvatar || null
    }).then(() => {
      wx.showToast({ title: '已保存', icon: 'success' });
      this.setData({ showSettings: false, savingSettings: false });
      this.load();
    }).catch((err) => {
      this.setData({ savingSettings: false });
      wx.showToast({ title: err.message || '保存失败', icon: 'none' });
    });
  },

  /** 更换队伍头像：拍照/相册 → 压缩直传 COS → 保存时随设置提交 */
  onPickFamilyAvatar() {
    wx.chooseMedia({
      count: 1, mediaType: ['image'], sourceType: ['camera', 'album'],
      success: (res) => {
        const path = res.tempFiles[0].tempFilePath;
        wx.showLoading({ title: '上传中' });
        store.uploadImage(path, 600).then((url) => {
          this.setData({ editAvatar: url });
          wx.hideLoading();
        }).catch((err) => {
          wx.hideLoading();
          wx.showToast({ title: err.message || '上传失败', icon: 'none', duration: 3000 });
        });
      }
    });
  },

  /** 解散队伍：二次确认后删除，返回队伍列表 */
  onDissolve() {
    const name = this.data.family ? this.data.family.name : '';
    wx.showModal({
      title: '解散队伍',
      content: `确定解散「${name}」吗？成员、心愿单、队伍冰箱将被删除，圈内菜谱转为私密，操作不可恢复。`,
      confirmText: '解散',
      confirmColor: '#D1493C',
      success: (res) => {
        if (!res.confirm) return;
        store.dissolveFamily(this.data.id).then(() => {
          wx.showToast({ title: '已解散', icon: 'none' });
          setTimeout(() => wx.navigateBack(), 600);
        }).catch((err) => wx.showToast({ title: err.message || '解散失败', icon: 'none' }));
      }
    });
  },

  /** 食材管理：进入队伍冰箱（复用冰箱页，familyId 标识队伍范围） */
  goFridge() {
    wx.navigateTo({ url: `/pages/fridge/fridge?familyId=${this.data.id}` });
  },

  // ---- 邀请（带有效期） ----
  openInvite() {
    this.setData({ showInvite: true, showCodeGen: false, inviteValidDays: 7, inviteToken: '' });
  },
  closeInvite() { this.setData({ showInvite: false }); },
  toggleCodeMode() { this.setData({ showCodeGen: !this.data.showCodeGen }); },
  onSegDays(e) { this.setData({ inviteValidDays: Number(e.currentTarget.dataset.days) }); },
  genInvite() {
    if (this.data.inviteCreating) return;
    this.setData({ inviteCreating: true });
    store.createInvite(this.data.id, 'QR_CODE', this.data.inviteValidDays).then((invite) => {
      this.setData({
        inviteCreating: false,
        inviteToken: invite.token,
        inviteExpires: invite.expiresAt
      });
      this.load();
    }).catch((err) => {
      this.setData({ inviteCreating: false });
      wx.showToast({ title: err.message || '生成失败', icon: 'none' });
    });
  },
  copyInviteToken() {
    if (!this.data.inviteToken) return;
    wx.setClipboardData({
      data: this.data.inviteToken,
      success: () => wx.showToast({ title: '已复制', icon: 'success' })
    });
  },
  onRevoke(e) {
    store.revokeInvite(e.currentTarget.dataset.inviteId).then(() => this.load());
  },
  onShareAppMessage() {
    const t = this.data.inviteToken;
    const fam = this.data.family || {};
    return {
      title: `加入「${fam.name || '队伍'}」，一起做饭吧`,
      path: t ? `/pages/circle-join/circle-join?invite=${t}` : `/pages/circle-join/circle-join?id=${this.data.id}`,
      imageUrl: fam.avatar || undefined
    };
  },

  onShareTimeline() {
    const t = this.data.inviteToken;
    return {
      title: `加入「${this.data.family ? this.data.family.name : '队伍'}」`,
      query: t ? `invite=${t}` : `id=${this.data.id}`
    };
  }
});
