const store = require('../../utils/store');
const theme = require('../../utils/theme');
const ui = require('../../utils/ui');
const config = require('../../utils/config');

const CATEGORIES = ['蔬菜', '肉类', '乳品', '调料', '饮品', '其他'];
// 分类 emoji：无照片的食材卡片用作封面占位
const CAT_EMOJI = { '蔬菜': '🥬', '肉类': '🥩', '乳品': '🥛', '调料': '🧂', '饮品': '🧃', '其他': '🍱' };

function today() {
  const d = new Date();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

function statusOf(item, todayStr) {
  if (!item.expiryDate) return { level: 3, label: '无到期日' };
  const remain = Math.ceil((new Date(item.expiryDate) - new Date(todayStr)) / 86400000);
  if (remain < 0) return { level: 0, label: '已过期' };
  if (remain <= item.reminderDays) return { level: 1, label: `快过期(${remain}天)` };
  return { level: 2, label: '新鲜' };
}

/** 卡片装饰：分类 emoji 封面占位 + 短日期（2026-08-31 → 08-31） */
function decorate(it) {
  return Object.assign({}, it, statusOf(it, today()), {
    catEmoji: CAT_EMOJI[it.category] || '📦',
    expiryShort: it.expiryDate ? String(it.expiryDate).substring(5) : '无到期日'
  });
}

/** 空白表单：每次打开录入弹窗都用全新数据，不带出上一次的内容 */
function emptyForm() {
  return {
    name: '', category: '', quantity: '', unit: '',
    addedAt: today(), mode: 'date', expiryDate: '', validDays: '',
    reminderDays: config.DEFAULT_REMINDER_DAYS, note: '', photoUrl: ''
  };
}

Page({
  data: {
    theme: theme.get(),
    navTitle: '冰箱',
    familyId: null,
    ownerId: null,
    ownerName: '',
    all: [],
    list: [],
    activeCategory: '',
    expiredCount: 0,
    urgentCount: 0,
    showForm: false,
    form: emptyForm(),
    categories: CATEGORIES,
    categoryIndex: -1,
    // 邀请弹层（个人冰箱：邀请他人共用）
    showInvite: false,
    showCodeGen: false,
    inviteValidDays: 7,
    inviteCreating: false,
    inviteToken: '',
    inviteExpires: '',
    // 操作记录弹层
    showLogs: false,
    logs: [],
    // 共享给我的冰箱列表
    showShared: false,
    sharedFridges: []
  },

  /** familyId → 队伍冰箱；ownerId → 他人共享冰箱；缺省 → 我的个人冰箱 */
  onLoad(options) {
    if (options && options.familyId) {
      const familyId = Number(options.familyId);
      this.setData({ familyId });
      this.setData({ navTitle: '队伍冰箱' });
    } else if (options && options.ownerId) {
      const ownerId = Number(options.ownerId);
      // 跳转方 encodeURIComponent 过昵称（可能含 emoji），onLoad 需解码后才能显示
      let name = options.ownerName || '好友';
      try { name = decodeURIComponent(name); } catch (e) {}
      this.setData({ ownerId, ownerName: name });
      this.setData({ navTitle: name + '的冰箱' });
    }
  },

  onShow() {
    getApp().applyTheme(this);
    this.load();
  },

  load() {
    store.fetchFridgeItems('status', '', this.data.familyId, this.data.ownerId).then((list) => {
      const items = (list || []).map(decorate);
      // 聚合临期/过期数量，供顶部横幅展示（按全量算，不受筛选影响）
      const expiredCount = items.filter((i) => i.level === 0).length;
      const urgentCount = items.filter((i) => i.level === 1).length;
      this.setData({ all: items, expiredCount, urgentCount });
      this.applyFilter();
    }).catch((err) => {
      if (err.code === 'AUTH') {
        this.selectComponent('#login-popup').show();
      } else {
        ui.fail(err);
      }
    });
  },

  /** 分类筛选：本地过滤，切换即时生效；URGENT 为临期标签（已过期+快过期） */
  applyFilter() {
    const cat = this.data.activeCategory;
    let list;
    if (cat === 'URGENT') {
      list = this.data.all.filter((i) => i.level === 0 || i.level === 1);
    } else {
      list = cat ? this.data.all.filter((i) => i.category === cat) : this.data.all;
    }
    this.setData({ list });
  },

  onFilter(e) {
    const cat = e.currentTarget.dataset.cat || '';
    if (cat === this.data.activeCategory) return;
    this.setData({ activeCategory: cat });
    this.applyFilter();
  },

  /** 打开录入弹窗：重置表单，避免残留上一次的数据 */
  openForm() {
    this.setData({ showForm: true, form: emptyForm(), categoryIndex: -1 });
  },
  closeForm() { this.setData({ showForm: false }); },
  onField(e) {
    const key = e.currentTarget.dataset.key;
    this.setData({ [`form.${key}`]: e.detail.value });
  },
  onCategory(e) {
    const idx = e.currentTarget.dataset.idx;
    this.setData({ categoryIndex: idx, 'form.category': CATEGORIES[idx] });
  },
  onMode(e) { this.setData({ 'form.mode': e.detail.value }); },

  /** 拍照备注：拍照/相册 → 压缩直传 COS → 回填 photoUrl（不做图片识别） */
  onTakePhoto() {
    wx.chooseMedia({
      count: 1, mediaType: ['image'], sourceType: ['camera', 'album'],
      success: (res) => {
        const path = res.tempFiles[0].tempFilePath;
        wx.showLoading({ title: '上传中' });
        store.uploadImage(path, 1080).then((url) => {
          this.setData({ 'form.photoUrl': url });
          wx.hideLoading();
        }).catch((err) => {
          wx.hideLoading();
          wx.showToast({ title: err.message || '上传失败', icon: 'none', duration: 3000 });
        });
      }
    });
  },

  submit() {
    const f = this.data.form;
    if (!f.name.trim()) return wx.showToast({ title: '请填写名称', icon: 'none' });
    const payload = {
      name: f.name.trim(),
      category: f.category || null,
      quantity: f.quantity ? Number(f.quantity) : null,
      unit: f.unit || null,
      addedAt: f.addedAt || today(),
      expiryDate: null,
      reminderDays: f.reminderDays ? Number(f.reminderDays) : config.DEFAULT_REMINDER_DAYS,
      note: f.note || null,
      photoUrl: f.photoUrl || null,
      familyId: this.data.familyId || null,
      ownerId: this.data.ownerId || null
    };
    if (f.mode === 'date') {
      if (!f.expiryDate) return wx.showToast({ title: '请选择到期日', icon: 'none' });
      payload.expiryDate = f.expiryDate;
    } else {
      if (!f.validDays) return wx.showToast({ title: '请填写保质期天数', icon: 'none' });
      const exp = new Date(payload.addedAt);
      exp.setDate(exp.getDate() + Number(f.validDays));
      const m = String(exp.getMonth() + 1).padStart(2, '0');
      const d = String(exp.getDate()).padStart(2, '0');
      payload.expiryDate = `${exp.getFullYear()}-${m}-${d}`;
    }
    store.createFridgeItem(payload).then(() => {
      wx.showToast({ title: '已放入冰箱', icon: 'success' });
      this.setData({ showForm: false });
      this.load();
    }).catch((err) => wx.showToast({ title: err.message, icon: 'none' }));
  },

  onDelete(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '删除',
      content: '确认从冰箱移除该物品？',
      success: (res) => {
        if (res.confirm) {
          store.deleteFridgeItem(id).then(() => this.load()).catch((err) => {
            if (err.code === 'AUTH') {
              this.selectComponent('#login-popup').show();
            } else {
              ui.fail(err, '删除失败');
            }
          });
        }
      }
    });
  },

  onLogined() { this.load(); },

  // ---- 邀请共用（仅个人冰箱） ----
  openInvite() { this.setData({ showInvite: true, showCodeGen: false, inviteToken: '' }); },
  closeInvite() { this.setData({ showInvite: false }); },
  toggleCodeMode() { this.setData({ showCodeGen: !this.data.showCodeGen }); },
  onSegDays(e) { this.setData({ inviteValidDays: Number(e.currentTarget.dataset.days) }); },
  genInvite() {
    if (this.data.inviteCreating) return;
    this.setData({ inviteCreating: true });
    store.createFridgeInvite(this.data.inviteValidDays).then((inv) => {
      this.setData({
        inviteCreating: false,
        inviteToken: inv.token,
        inviteExpires: inv.expiresAt
      });
    }).catch((err) => {
      this.setData({ inviteCreating: false });
      wx.showToast({ title: err.message || '生成失败', icon: 'none' });
    });
  },
  copyInvite() {
    if (!this.data.inviteToken) return;
    wx.setClipboardData({
      data: this.data.inviteToken,
      success: () => wx.showToast({ title: '已复制', icon: 'success' })
    });
  },
  onShareAppMessage() {
    const token = this.data.inviteToken;
    return {
      title: '一起共用我的冰箱吧，食材互相看得见',
      path: token ? `/pages/fridge-join/fridge-join?invite=${token}` : '/pages/fridge/fridge'
    };
  },

  // ---- 临期物品（alert-bar 点击 → 切到临期标签） ----
  openUrgent() {
    if (this.data.activeCategory === 'URGENT') return;
    this.setData({ activeCategory: 'URGENT' });
    this.applyFilter();
  },

  // ---- 操作记录 ----
  openLogs() {
    this.setData({ showLogs: true });
    store.fetchFridgeLogs(this.data.familyId, this.data.ownerId).then((logs) => {
      this.setData({ logs: this.decorateLogs(logs || []) });
    }).catch((err) => wx.showToast({ title: err.message || '加载失败', icon: 'none' }));
  },
  closeLogs() { this.setData({ showLogs: false }); },
  decorateLogs(logs) {
    const ACTION_TEXT = { ADD: '添加了', UPDATE: '修改了', DELETE: '删除了' };
    return logs.map((l) => {
      const name = l.operatorName || '用户' + l.operatorId;
      return Object.assign({}, l, {
        actionText: ACTION_TEXT[l.action] || l.action,
        operatorName: name,
        operatorChar: String(name)[0],
        timeShort: l.createdAt ? String(l.createdAt).substring(5, 16) : ''
      });
    });
  },

  // ---- 共享给我的冰箱 ----
  openShared() {
    store.fetchSharedFridges().then((list) => {
      const sharedFridges = (list || []).map((s) => {
        const name = s.ownerName || '好友';
        // Array.from 按码位取首字：emoji 开头的昵称不会取到半个代理对
        return Object.assign({}, s, { ownerName: name, ownerChar: Array.from(name)[0] || '好' });
      });
      this.setData({ showShared: true, sharedFridges });
    }).catch((err) => wx.showToast({ title: err.message || '加载失败', icon: 'none' }));
  },
  closeShared() { this.setData({ showShared: false }); },
  onSharedTap(e) {
    const { id, name } = e.currentTarget.dataset;
    wx.redirectTo({
      url: `/pages/fridge/fridge?ownerId=${id}&ownerName=${encodeURIComponent(name || '好友')}`
    });
  }
});
