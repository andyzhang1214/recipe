const store = require('../../utils/store');
const theme = require('../../utils/theme');

Page({

  onLoad() {
    // 默认展示全库数据（空关键词 = 后端返回全部），用户搜索/切分类时再过滤
    this.query();
  },
  onShow() {
    getApp().applyTheme(this);
  },
  data: {
    theme: theme.get(),
    keyword: '',
    category: '',
    list: [],
    loading: false
  },

  onInput(e) {
    this.setData({ keyword: e.detail.value });
  },

  onSearch() {
    this.query();
  },

  onCategory(e) {
    const idx = e.currentTarget.dataset.idx;
    const categories = ['', '食品', '调料'];
    this.setData({ category: categories[idx] }, () => this.query());
  },

  query() {
    this.setData({ loading: true });
    store.fetchFoodCalories(this.data.keyword, this.data.category)
      .then((list) => {
        // 热量分级：>=400 高(红) / 200-399 中(橙) / <200 低(绿)
        const items = (list || []).map((it) => {
          const kcal = Number(it.caloriesPer100) || 0;
          let level = 'low';
          if (kcal >= 400) level = 'high';
          else if (kcal >= 200) level = 'mid';
          return Object.assign({}, it, { kcalLevel: level });
        });
        this.setData({ list: items });
      })
      .catch(() => this.setData({ list: [] }))
      .finally(() => this.setData({ loading: false }));
  }
});
