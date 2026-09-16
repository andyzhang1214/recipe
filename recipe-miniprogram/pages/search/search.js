const store = require('../../utils/store');
const theme = require('../../utils/theme');
const ui = require('../../utils/ui');
const config = require('../../utils/config');

Page({

  onShow() {
    getApp().applyTheme(this);
  },
  data: {
    theme: theme.get(),
    keyword: '',
    list: [],
    page: 0,
    size: config.PAGE_SIZE,
    finished: false,
    loading: false,
    hotKeywords: ['红烧肉', '可乐鸡翅', '番茄炒蛋', '糖醋排骨', '酸辣土豆丝', '清蒸鲈鱼', '麻婆豆腐', '冬瓜排骨汤', '蒜蓉西兰花', '咖喱鸡肉']
  },

  onInput(e) { this.setData({ keyword: e.detail.value }); },

  onHotTap(e) {
    const kw = e.currentTarget.dataset.kw;
    this.setData({ keyword: kw }, () => this.onSearch());
  },

  onSearch() {
    this.setData({ page: 0, list: [], finished: false });
    this.query();
  },

  query() {
    if (this.data.loading || this.data.finished) return;
    this.setData({ loading: true });
    store.fetchPublicRecipes(this.data.page, this.data.size, this.data.keyword)
      .then((res) => {
        // 后端返回 {recipe, covers[], authorNickname, ...}，平铺后才能绑定 title/cover（同 plaza.js）
        const list = (res.content || []).map((it) => {
          const r = it.recipe || {};
          return Object.assign({}, r, {
            covers: Array.isArray(it.covers) && it.covers.length > 0 ? it.covers : [r.cover].filter(Boolean),
            authorNickname: it.authorNickname,
            authorAvatar: it.authorAvatar,
            authorId: it.authorId
          });
        });
        const start = this.data.list.length;
        const patch = { page: this.data.page + 1 };
        list.forEach((it, i) => { patch[`list[${start + i}]`] = it; });
        patch.finished = (this.data.page + 1) * this.data.size >= (res.total || 0);
        this.setData(patch);
      })
      .catch((err) => ui.fail(err, '搜索失败'))
      .finally(() => this.setData({ loading: false }));
  },

  onReachBottom() { this.query(); },

  onRecipeTap(e) {
    wx.navigateTo({ url: `/pages/recipe-detail/recipe-detail?id=${e.currentTarget.dataset.id}` });
  }
});
