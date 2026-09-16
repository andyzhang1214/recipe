/**
 * 步骤内联计时组件：
 * - 展示步骤建议时长（来自 step.durationSec）
 * - 开始 → 倒计时（mm:ss + 线性进度）→ 暂停/继续 → 完成震动提醒
 * - 组件卸载自动清理定时器
 */
Component({
  properties: {
    durationSec: { type: Number, value: 0 },
    stepIndex: { type: Number, value: 0 }
  },

  data: {
    started: false,
    running: false,
    finished: false,
    remaining: 0,
    customSec: 0,      // 本次计时时长：默认=建议时长
    totalSec: 0,       // 进度条分母：以实际开始时长为准
    hintText: ''
  },

  // 无建议时长时默认 5 分钟
  observers: {
    durationSec(val) {
      const d = Number(val) || 0;
      this.setData({
        hintText: this.fmtHint(d),
        customSec: this.data.started ? this.data.customSec : (d > 0 ? d : 300)
      });
    }
  },

  lifetimes: {
    attached() {
      const d = Number(this.data.durationSec) || 0;
      this.setData({
        hintText: this.fmtHint(d),
        customSec: d > 0 ? d : 300
      });
    },
    detached() {
      this.clearTimer();
    }
  },

  methods: {
    // 建议时长提示文案（如 "3 分钟" / "45 秒"）
    fmtHint(sec) {
      const d = Number(sec) || 0;
      if (d <= 0) return '';
      if (d < 60) return d + ' 秒';
      const min = Math.round(d / 60);
      const s = d % 60;
      return s === 0 ? min + ' 分钟' : min + ' 分 ' + s + ' 秒';
    },

    clearTimer() {
      if (this._timer) {
        clearInterval(this._timer);
        this._timer = null;
      }
    },

    start() {
      if (this.data.started) return;
      this.setData({
        started: true,
        running: true,
        remaining: this.data.customSec,
        totalSec: this.data.customSec
      });
      this.tick();
    },

    tick() {
      this.clearTimer();
      this._timer = setInterval(() => {
        const next = this.data.remaining - 1;
        if (next <= 0) {
          this.clearTimer();
          this.setData({ remaining: 0, running: false, finished: true });
          wx.vibrateLong({});
          wx.showToast({ title: '时间到！', icon: 'success' });
        } else {
          this.setData({ remaining: next });
        }
      }, 1000);
    },

    pause() {
      this.clearTimer();
      this.setData({ running: false });
    },

    resume() {
      if (this.data.finished) return;
      this.setData({ running: true });
      this.tick();
    },

    /** 点击时间加时：弹选加多久，剩余与总时长同步增加（进度条不倒退） */
    onTimeTap() {
      if (this.data.finished || !this.data.started) return;
      wx.showActionSheet({
        itemList: ['+ 1 分钟', '+ 3 分钟', '+ 5 分钟', '+ 10 分钟'],
        success: (res) => {
          const minutes = [1, 3, 5, 10][res.tapIndex];
          if (!minutes) return;
          this.setData({
            remaining: this.data.remaining + minutes * 60,
            totalSec: this.data.totalSec + minutes * 60
          });
          wx.showToast({ title: `已加 ${minutes} 分钟`, icon: 'none' });
        }
      });
    },

    reset() {
      this.clearTimer();
      this.setData({ started: false, running: false, finished: false, remaining: 0 });
    }
  }
});
