const theme = require('../../utils/theme');
const STORAGE_KEY = 'cook_timers';
const PRESET_KEY = 'cook_presets';

function load() {
  return wx.getStorageSync(STORAGE_KEY) || [];
}
function save(list) {
  wx.setStorageSync(STORAGE_KEY, list);
}
function loadPresets() {
  return wx.getStorageSync(PRESET_KEY) || [];
}
function savePresets(list) {
  wx.setStorageSync(PRESET_KEY, list);
}

Page({
  data: {
    theme: theme.get(),
    timers: [],
    presets: loadPresets(),
    addName: '',
    addH: '', addM: '', addS: '',
    savePreset: false,
    // 预设编辑弹层
    showPresetForm: false,
    editingIdx: -1,
    pfName: '', pfH: '', pfM: '', pfS: ''
  },

  onShow() {
    getApp().applyTheme(this);
    // 预设必须每次进页重读：data 初始化器只在模块加载时执行一次，新页面实例会拿到旧快照
    this.setData({ presets: loadPresets() });
    this.sync();
    this._timer = setInterval(() => this.sync(), 1000);
  },
  onPullDownRefresh() {
    this.sync();
    wx.stopPullDownRefresh();
  },
  onHide() {
    clearInterval(this._timer);
  },
  onUnload() {
    clearInterval(this._timer);
  },

  /** 时间戳校准：剩余 = endAt - now；页面不可见时 setInterval 停止也不影响准确性 */
  sync() {
    const now = Date.now();
    let dirty = false;
    const timers = load().map((t) => {
      let remain = t.endAt ? t.endAt - now : 0;
      if (t.paused) remain = t.pausedRemainSec * 1000;
      if (remain < 0) remain = 0;
      const finished = t.endAt && remain === 0 && !t.doneNotified;
      if (finished) {
        t.doneNotified = true;
        dirty = true;
        this.notify(t.name);
      }
      t.remainText = format(remain);
      t.running = !t.paused && !!t.endAt && remain > 0;
      return t;
    });
    // doneNotified 必须落盘：否则下一秒 load() 又是 false，弹窗会一秒一个连弹
    if (dirty) save(timers);
    this.setData({ timers });
  },

  notify(name) {
    wx.vibrateLong();
    wx.showModal({
      title: '⏰ 时间到',
      content: `${name} 好了！`,
      showCancel: false
    });
  },

  // ---- 添加计时器（时分秒三段） ----
  onAddName(e) { this.setData({ addName: e.detail.value }); },
  onAddH(e) { this.setData({ addH: e.detail.value }); },
  onAddM(e) { this.setData({ addM: e.detail.value }); },
  onAddS(e) { this.setData({ addS: e.detail.value }); },
  toggleSavePreset() { this.setData({ savePreset: !this.data.savePreset }); },

  /** 三段输入转秒 */
  durToSec(h, m, s) {
    return (Number(h) || 0) * 3600 + (Number(m) || 0) * 60 + (Number(s) || 0);
  },

  confirmAdd() {
    const name = this.data.addName.trim() || '计时器';
    const sec = this.durToSec(this.data.addH, this.data.addM, this.data.addS);
    if (sec <= 0) {
      return wx.showToast({ title: '请输入时长', icon: 'none' });
    }
    this.addTimer(name, sec);
    // 勾选了「保存为常用预设」：连同名称一起存
    if (this.data.savePreset) {
      const list = loadPresets();
      list.push({
        id: Date.now(),
        name,
        h: Number(this.data.addH) || 0,
        m: Number(this.data.addM) || 0,
        s: Number(this.data.addS) || 0
      });
      savePresets(list);
      this.setData({ presets: list });
      wx.showToast({ title: '已加入常用预设', icon: 'none' });
    }
    this.setData({ addName: '', addH: '', addM: '', addS: '', savePreset: false });
  },
  addTimer(name, durationSec) {
    const list = load();
    list.push({
      id: Date.now(),
      name,
      durationSec,
      endAt: null,
      paused: false,
      pausedRemainSec: null,
      doneNotified: false
    });
    save(list);
    this.sync();
  },

  // ---- 常用预设（用户自定义，可增删改） ----
  addPreset(e) {
    const p = this.data.presets[e.currentTarget.dataset.idx];
    if (!p) return;
    this.addTimer(p.name, this.durToSec(p.h, p.m, p.s));
  },
  noop() {},

  editPreset(e) {
    const idx = Number(e.currentTarget.dataset.idx);
    const p = this.data.presets[idx];
    if (!p) return;
    this.setData({
      showPresetForm: true,
      editingIdx: idx,
      pfName: p.name,
      pfH: String(p.h || ''),
      pfM: String(p.m || ''),
      pfS: String(p.s || '')
    });
  },
  removePreset(e) {
    const idx = Number(e.currentTarget.dataset.idx);
    const p = this.data.presets[idx];
    if (!p) return;
    wx.showModal({
      title: '删除预设',
      content: `删除「${p.name}」？`,
      confirmColor: '#E94B6A',
      success: (r) => {
        if (!r.confirm) return;
        const list = this.data.presets.filter((_, i) => i !== idx);
        savePresets(list);
        this.setData({ presets: list });
      }
    });
  },

  onPresetName(e) { this.setData({ pfName: e.detail.value }); },
  onPresetH(e) { this.setData({ pfH: e.detail.value }); },
  onPresetM(e) { this.setData({ pfM: e.detail.value }); },
  onPresetS(e) { this.setData({ pfS: e.detail.value }); },
  closePresetForm() { this.setData({ showPresetForm: false }); },

  confirmPresetForm() {
    const name = this.data.pfName.trim();
    const sec = this.durToSec(this.data.pfH, this.data.pfM, this.data.pfS);
    if (!name) return wx.showToast({ title: '请输入预设名称', icon: 'none' });
    if (sec <= 0) return wx.showToast({ title: '请输入时长', icon: 'none' });
    const item = {
      id: this.data.editingIdx > -1 ? this.data.presets[this.data.editingIdx].id : Date.now(),
      name,
      h: Number(this.data.pfH) || 0,
      m: Number(this.data.pfM) || 0,
      s: Number(this.data.pfS) || 0
    };
    const list = this.data.presets.slice();
    if (this.data.editingIdx > -1) list[this.data.editingIdx] = item;
    else list.push(item);
    savePresets(list);
    this.setData({ presets: list, showPresetForm: false, editingIdx: -1 });
    wx.showToast({ title: '预设已保存', icon: 'none' });
  },

  start(e) {
    const id = e.currentTarget.dataset.id;
    const list = load().map((t) => {
      if (t.id !== id) return t;
      if (t.endAt) return t;
      if (t.paused) {
        // 从暂停恢复
        t.endAt = Date.now() + (t.pausedRemainSec || t.durationSec) * 1000;
        t.paused = false;
        t.pausedRemainSec = null;
      } else {
        t.endAt = Date.now() + t.durationSec * 1000;
      }
      t.doneNotified = false;
      return t;
    });
    save(list);
    this.sync();
  },

  pause(e) {
    const id = e.currentTarget.dataset.id;
    const now = Date.now();
    const list = load().map((t) => {
      if (t.id !== id || !t.endAt) return t;
      t.pausedRemainSec = Math.max(0, Math.round((t.endAt - now) / 1000));
      t.paused = true;
      t.endAt = null;
      return t;
    });
    save(list);
    this.sync();
  },

  reset(e) {
    const id = e.currentTarget.dataset.id;
    const list = load().map((t) => {
      if (t.id !== id) return t;
      t.endAt = null;
      t.paused = false;
      t.pausedRemainSec = null;
      t.doneNotified = false;
      return t;
    });
    save(list);
    this.sync();
  },

  remove(e) {
    const id = e.currentTarget.dataset.id;
    const list = load().filter((t) => t.id !== id);
    save(list);
    this.sync();
  }
});

function format(ms) {
  const total = Math.ceil(ms / 1000);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}
