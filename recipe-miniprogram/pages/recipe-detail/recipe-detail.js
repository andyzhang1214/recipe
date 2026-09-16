const store = require('../../utils/store');
const theme = require('../../utils/theme');

Page({
  data: {
    theme: theme.get(),
    navTitle: '菜谱详情',
    id: null,
    recipe: null,
    author: null,
    covers: [],
    foodIngredients: [],
    seasoningList: [],
    steps: [],
    myStars: 0,
    comments: [],
    commentText: '',
    showShare: false,
    visibilityList: [
      { value: 'PRIVATE', label: '私密（仅自己）' },
      { value: 'FAMILY', label: '指定圈子' },
      { value: 'PUBLIC', label: '全部（公开到广场）' }
    ],
    families: [],
    chosenVisibility: 'PRIVATE',
    chosenVisibilityLabel: '私密（仅自己）',
    chosenFamilyId: null,
    favorited: false,
    isMine: false
  },

  onLoad(options) {
    getApp().applyTheme(this);
    this.setData({ id: Number(options.id) });
    this.loadDetail();
    this.loadFavoriteStatus();
  },

  onShow() {
    getApp().applyTheme(this);
  },

  loadFavoriteStatus() {
    if (!store.getToken()) return;
    store.fetchFavoriteStatus(this.data.id).then((res) => {
      this.setData({ favorited: !!res.favorited });
    }).catch(() => {});
  },

  /** 收藏/取消收藏 */
  onToggleFavorite() {
    if (!store.getToken()) {
      return this.selectComponent('#login-popup').show();
    }
    store.toggleFavorite(this.data.id).then((res) => {
      this.setData({ favorited: !!res.favorited });
      wx.showToast({ title: res.favorited ? '已收藏' : '已取消收藏', icon: 'none' });
    }).catch((err) => wx.showToast({ title: err.message, icon: 'none' }));
  },

  loadDetail() {
    store.fetchRecipeDetail(this.data.id)
      .then((data) => {
        const covers = data.covers && data.covers.length
          ? data.covers
          : (data.recipe.cover ? [data.recipe.cover] : []);
        this.setData({
          recipe: data.recipe,
          author: data.author,
          covers,
          foodIngredients: this.pickIngs(data.ingredients, 'FOOD'),
          seasoningList: this.pickIngs(data.ingredients, 'SEASONING'),
          steps: data.steps || []
        });
        this.setData({ navTitle: data.recipe.title });
        // 作者本人：可见范围标签可点击修改
        if (store.getToken()) {
          store.fetchMe().then((me) => {
            this.setData({ isMine: !!(me && data.recipe.authorId === me.id) });
          }).catch(() => {});
        }
      })
      .catch((err) => {
        wx.showToast({ title: err.message || '加载失败', icon: 'none' });
      });
    this.loadComments();
    this.loadMyRating();
  },

  /** 只刷新菜谱本体（评星/改范围后局部更新，不重拉评论） */
  refreshRecipeOnly() {
    store.fetchRecipeDetail(this.data.id).then((data) => {
      const covers = data.covers && data.covers.length
        ? data.covers
        : (data.recipe.cover ? [data.recipe.cover] : []);
      this.setData({
        recipe: data.recipe,
        author: data.author,
        covers,
        foodIngredients: this.pickIngs(data.ingredients, 'FOOD'),
        seasoningList: this.pickIngs(data.ingredients, 'SEASONING'),
        steps: data.steps || []
      });
    }).catch(() => {});
  },

  /** 按类型筛出食材/调料（老数据无 type 统一归食材） */
  pickIngs(list, type) {
    return (list || []).filter((i) => (type === 'SEASONING') === (i.type === 'SEASONING'));
  },

  loadComments() {
    store.fetchComments(this.data.id).then((list) => {
      // WXML 不支持字符串下标，头像首字符在 JS 侧预处理
      const comments = (list || []).map((c) => {
        const uid = String(c.userId || '');
        return Object.assign({}, c, {
          avatarChar: /^\d+$/.test(uid) ? '厨' : (uid.charAt(0) || '厨')
        });
      });
      this.setData({ comments });
    }).catch(() => {});
  },

  loadMyRating() {
    // 评星聚合在详情接口未返回个人评分时，可从评论流简化：MVP 直接展示可点星标
    this.setData({ myStars: 0 });
  },

  onStar(e) {
    const stars = e.currentTarget.dataset.stars;
    if (!store.getToken()) {
      return this.selectComponent('#login-popup').show();
    }
    store.rateRecipe(this.data.id, stars).then(() => {
      this.setData({ myStars: stars });
      wx.showToast({ title: '已评 ' + stars + ' 星', icon: 'success' });
      this.refreshRecipeOnly();
    }).catch((err) => wx.showToast({ title: err.message, icon: 'none' }));
  },

  onComment(e) { this.setData({ commentText: e.detail.value }); },

  submitComment() {
    const content = this.data.commentText.trim();
    if (!content) return;
    if (!store.getToken()) {
      return this.selectComponent('#login-popup').show();
    }
    store.addComment(this.data.id, content).then(() => {
      this.setData({ commentText: '' });
      this.loadComments();
    }).catch((err) => wx.showToast({ title: err.message, icon: 'none' }));
  },

  openShare() {
    if (!store.getToken()) {
      return this.selectComponent('#login-popup').show();
    }
    if (!this.data.isMine) {
      return wx.showToast({ title: '只有作者可以修改可见范围', icon: 'none' });
    }
    // 预选当前可见范围
    const cur = this.data.recipe.visibility;
    const found = this.data.visibilityList.filter((v) => v.value === cur)[0];
    store.fetchMyFamilies().then((list) => {
      this.setData({
        families: list || [],
        showShare: true,
        chosenVisibility: cur,
        chosenVisibilityLabel: found ? found.label : this.data.chosenVisibilityLabel,
        chosenFamilyId: null
      });
    }).catch(() => this.setData({ showShare: true }));
  },
  closeShare() { this.setData({ showShare: false }); },
  onVisibility(e) {
    const idx = e.detail.value;
    this.setData({
      chosenVisibility: this.data.visibilityList[idx].value,
      chosenVisibilityLabel: this.data.visibilityList[idx].label
    });
  },
  onFamily(e) {
    this.setData({ chosenFamilyId: this.data.families[e.detail.value].family.id });
  },
  confirmShare() {
    const { chosenVisibility, chosenFamilyId } = this.data;
    const visibility = chosenVisibility;
    if ((visibility === 'FAMILY' || visibility === 'TEAM') && !chosenFamilyId) {
      return wx.showToast({ title: '请选择可见圈层', icon: 'none' });
    }
    store.updateRecipe(this.data.id, { visibility, familyId: chosenFamilyId }).then(() => {
      wx.showToast({ title: '已更新可见范围', icon: 'success' });
      this.setData({ showShare: false, 'recipe.visibility': visibility });
    }).catch((err) => wx.showToast({ title: err.message, icon: 'none' }));
  },

  onCookMode() {
    wx.navigateTo({ url: `/pages/cook-mode/cook-mode?id=${this.data.id}` });
  },

  /** 点作者行进用户主页 */
  onAuthorTap(e) {
    const uid = e.currentTarget.dataset.uid;
    if (uid) wx.navigateTo({ url: `/pages/user-profile/user-profile?id=${uid}` });
  },

  /** 展示图预览 */
  onPreviewCover(e) {
    wx.previewImage({ current: this.data.covers[e.currentTarget.dataset.idx], urls: this.data.covers });
  },

  /** 步骤图预览 */
  onPreviewStep(e) {
    wx.previewImage({ current: e.currentTarget.dataset.url, urls: [e.currentTarget.dataset.url] });
  },

  onLogined() { this.loadDetail(); },

  onShareAppMessage() {
    const r = this.data.recipe;
    return {
      title: r ? r.title : '菜谱分享',
      path: `/pages/recipe-detail/recipe-detail?id=${this.data.id}`,
      imageUrl: r && r.cover ? r.cover : undefined
    };
  },

  /** 分享到朋友圈 */
  onShareTimeline() {
    const r = this.data.recipe;
    return {
      title: r ? r.title : '菜谱分享',
      query: `id=${this.data.id}`,
      imageUrl: r && r.cover ? r.cover : undefined
    };
  }
});
