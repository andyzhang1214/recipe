const store = require('../../utils/store');
const theme = require('../../utils/theme');
const ui = require('../../utils/ui');
const config = require('../../utils/config');

/** 自定义排序持久化 key（存队伍 id 顺序） */
const ORDER_KEY = 'family_order_v1';

Page({
  data: {
    theme: theme.get(),
    families: [],
    showCreate: false,
    createName: '',
    createMax: '',
    createAvatar: '',
    creating: false,
    showJoin: false,
    joinCode: '',
    // 拖拽排序状态
    dragging: false,
    dragIndex: -1,
    floatItem: null,
    floatX: 0,
    floatY: 0,
    cardW: 0
  },

  onShow() {
    getApp().applyTheme(this);
    this.load();
  },

  /** 记录滚动位置：拖拽中页面若滚动，需补偿槽位坐标 */
  onPageScroll(e) {
    this._curScrollTop = e.scrollTop || 0;
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh());
  },

  load() {
    return store.fetchMyFamilies().then((list) => {
      if (this.data.dragging) return; // 拖拽中不打断
      // 铺平一层：id 供 wx:key；nameChar 供无图封面占位
      const families = (list || []).map((f) => {
        const fam = f.family || {};
        return Object.assign({}, f, {
          id: fam.id,
          nameChar: Array.from(String(fam.name || '队'))[0] || '队'
        });
      });
      // 应用本地保存的自定义排序；新队伍追加在末尾
      let saved = [];
      try { saved = wx.getStorageSync(ORDER_KEY) || []; } catch (err) { saved = []; }
      const rank = {};
      saved.forEach((id, i) => { rank[id] = i; });
      families.sort((a, b) => {
        const ra = rank[a.id] == null ? saved.length : rank[a.id];
        const rb = rank[b.id] == null ? saved.length : rank[b.id];
        return ra - rb;
      });
      this.setData({ families });
    }).catch((err) => ui.fail(err, '加载队伍失败'));
  },

  // ---- 长按拖拽排序 ----
  /** 长按开始：测量所有卡片槽位坐标，生成跟随手指的悬浮卡片 */
  onCardLongPress(e) {
    if (this.data.dragging) return;
    const index = Number(e.currentTarget.dataset.index);
    if (!this.data.families[index]) return;
    const t = (e.touches && e.touches[0]) || null;
    const startX = t ? t.clientX : 0;
    const startY = t ? t.clientY : 0;
    this._dragPending = true;
    this._startScrollTop = this._curScrollTop || 0;
    this._orderChanged = false;
    this._lastMoveTs = 0;
    const query = wx.createSelectorQuery();
    query.selectAll('.f-card').boundingClientRect();
    query.select('.f-grid').boundingClientRect();
    query.exec((res) => {
      if (!this._dragPending) return; // 手指已松开，取消
      this._dragPending = false;
      const rects = res && res[0];
      const gridRect = res && res[1];
      if (!rects || !rects[index]) return;
      this._slots = rects;
      this._gridRect = gridRect;
      wx.vibrateShort({ type: 'medium', fail: () => {} });
      this.setData({
        dragging: true,
        dragIndex: index,
        floatItem: this.data.families[index],
        floatX: startX,
        floatY: startY,
        cardW: rects[index].width
      });
    });
  },

  /** 拖动中：悬浮卡片跟随手指，滑到哪个槽位就实时换位 */
  onCardTouchMove(e) {
    if (!this.data.dragging) return;
    const t = e.touches && e.touches[0];
    if (!t) return;
    const x = t.clientX;
    const y = t.clientY;
    // 页面若发生滚动，槽位整体平移 dy，检测时补偿
    const dy = (this._curScrollTop || 0) - (this._startScrollTop || 0);
    const slots = this._slots || [];
    let target = -1;
    for (let i = 0; i < slots.length; i++) {
      const r = slots[i];
      if (x >= r.left && x <= r.right && y >= r.top - dy && y <= r.bottom - dy) {
        target = i;
        break;
      }
    }
    // 落在卡片间隙：网格范围内就近吸附
    if (target < 0 && this._gridRect && slots.length) {
      const g = this._gridRect;
      const gTop = g.top - dy;
      const gBottom = g.bottom - dy;
      if (x >= g.left - 30 && x <= g.right + 30 && y >= gTop - 30 && y <= gBottom + 30) {
        let best = -1;
        let bestD = Infinity;
        for (let i = 0; i < slots.length; i++) {
          const r = slots[i];
          const cx = (r.left + r.right) / 2;
          const cy = (r.top + r.bottom) / 2 - dy;
          const dx = x - cx;
          const dyy = y - cy;
          const d = dx * dx + dyy * dyy;
          if (d < bestD) { bestD = d; best = i; }
        }
        target = best;
      }
    }
    const now = Date.now();
    if (target >= 0 && target !== this.data.dragIndex) {
      const list = this.data.families.slice();
      const moved = list.splice(this.data.dragIndex, 1)[0];
      list.splice(target, 0, moved);
      this._orderChanged = true;
      this._lastMoveTs = now;
      this.setData({ families: list, dragIndex: target, floatItem: moved, floatX: x, floatY: y });
    } else if (now - this._lastMoveTs >= 32) {
      // 节流：仅更新悬浮位置
      this._lastMoveTs = now;
      this.setData({ floatX: x, floatY: y });
    }
  },

  /** 松手：结束拖拽，顺序变化则保存到本地 */
  onCardTouchEnd() {
    this._dragPending = false;
    if (!this.data.dragging) return;
    const changed = this._orderChanged;
    if (changed) {
      const ids = this.data.families.map((f) => f.id);
      try { wx.setStorageSync(ORDER_KEY, ids); } catch (err) {}
    }
    this._slots = null;
    this._gridRect = null;
    this.setData({ dragging: false, dragIndex: -1, floatItem: null });
    if (changed) wx.showToast({ title: '排序已保存', icon: 'none' });
  },

  openCreate() { this.setData({ showCreate: true }); },
  closeCreate() { this.setData({ showCreate: false }); },
  onCreateName(e) { this.setData({ createName: e.detail.value }); },
  onCreateMax(e) { this.setData({ createMax: e.detail.value }); },

  /** 选择队伍头像：拍照/相册 → 压缩直传 COS → 回填 URL */
  onPickAvatar() {
    wx.chooseMedia({
      count: 1, mediaType: ['image'], sourceType: ['camera', 'album'],
      success: (res) => {
        const path = res.tempFiles[0].tempFilePath;
        wx.showLoading({ title: '上传中' });
        store.uploadImage(path, 600).then((url) => {
          this.setData({ createAvatar: url });
          wx.hideLoading();
        }).catch((err) => {
          wx.hideLoading();
          wx.showToast({ title: err.message || '上传失败', icon: 'none', duration: 3000 });
        });
      }
    });
  },

  confirmCreate() {
    const { createName, createMax, createAvatar, creating } = this.data;
    if (creating) return;
    if (!createName.trim()) {
      return wx.showToast({ title: '请填写圈层名称', icon: 'none' });
    }
    const maxMembers = createMax ? Number(createMax) : config.MAX_MEMBERS;
    if (maxMembers < config.MEMBER_MIN || maxMembers > config.MAX_MEMBERS) {
      return wx.showToast({ title: '成员上限需在 1-99 之间', icon: 'none' });
    }
    this.setData({ creating: true });
    store.createFamily({
      name: createName.trim(),
      maxMembers,
      avatar: createAvatar || null
    }).then((id) => {
      wx.showToast({ title: '创建成功', icon: 'success' });
      this.setData({ showCreate: false, createName: '', createMax: '', createAvatar: '', creating: false });
      this.load();
      wx.navigateTo({ url: `/pages/circle-detail/circle-detail?id=${id}` });
    }).catch((err) => {
      this.setData({ creating: false });
      wx.showToast({ title: err.message || '创建失败', icon: 'none' });
    });
  },

  openJoin() { this.setData({ showJoin: true }); },
  closeJoin() { this.setData({ showJoin: false }); },
  onJoinCode(e) { this.setData({ joinCode: e.detail.value }); },
  confirmJoin() {
    const code = this.data.joinCode.trim();
    if (!code) return;
    store.joinFamily(code).then((res) => {
      this.setData({ showJoin: false, joinCode: '' });
      this.load();
      if (res && res.alreadyMember) {
        wx.showToast({ title: '你已在队伍中', icon: 'none' });
        if (res.familyId) wx.navigateTo({ url: `/pages/circle-detail/circle-detail?id=${res.familyId}` });
      } else {
        wx.showToast({ title: '已加入队伍', icon: 'success' });
        if (res && res.familyId) {
          wx.navigateTo({ url: `/pages/circle-detail/circle-detail?id=${res.familyId}` });
        }
      }
    }).catch((err) => wx.showToast({ title: err.message || '加入失败', icon: 'none' }));
  },

  onCircleTap(e) {
    wx.navigateTo({ url: `/pages/circle-detail/circle-detail?id=${e.currentTarget.dataset.id}` });
  }
});
