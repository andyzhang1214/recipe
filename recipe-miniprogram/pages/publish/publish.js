const store = require('../../utils/store');
const theme = require('../../utils/theme');

const MAX_COVERS = 4;

Page({
  data: {
    theme: theme.get(),
    navTitle: '发布',
    title: '',
    covers: [],
    // 食材：名称 + 克数（计入热量）
    ingredients: [{ name: '', grams: '' }],
    // 调料：名称 + 自由用量（一勺油/少许胡椒粉，不计入热量）
    seasonings: [{ name: '', amount: '' }],
    // 食材名称联想：suggestFor = 触发联想的行下标
    suggests: [],
    suggestFor: -1,
    // 几人份（选填，用于计算每份热量）
    servings: '',
    steps: [{ text: '', seasoning: '', image: '', durationSec: '' }],
    maxCovers: MAX_COVERS,
    submitting: false,
    // 可见范围：3 选 1（PRIVATE/PUBLIC/FAMILY），默认 PRIVATE
    visibility: 'PRIVATE',
    // FAMILY 时：全部队伍开关 或 指定队伍多选
    shareAll: false,
    chosenFamilyIds: [],
    selectedFamilyMap: {},
    families: [],
    // 心愿单-去完成：发布成功后自动标记该心愿完成
    mealId: null,
    // 待学习-去发布：发布成功后自动移出学习清单
    learningId: null,
    // 编辑模式：我的页「编辑」进入（tab 页无法带参，经 globalData 传递）
    editId: null,
    editTitle: ''
  },

  onLoad() {
    getApp().applyTheme(this);
    this.loadFamilies();
  },

  onUnload() {
    if (this._suggestTimer) clearTimeout(this._suggestTimer);
  },

  onShow() {
    getApp().applyTheme(this);
    // 我的页「编辑」跳转：tab 页无法带参，经 globalData 带入菜谱 id
    if (getApp().globalData.editRecipeId) {
      const id = getApp().globalData.editRecipeId;
      delete getApp().globalData.editRecipeId;
      this.loadForEdit(id);
      return;
    }
    // 队伍主页「去完成」/ 待学习「去发布」跳转：经 globalData 带入菜名（去完成默认发布到该队伍）
    const prefill = getApp().globalData.publishPrefill;
    if (prefill && prefill.dish) {
      delete getApp().globalData.publishPrefill;
      const patch = {
        title: prefill.dish,
        mealId: prefill.mealId || null,
        learningId: prefill.learningId || null
      };
      if (prefill.familyId) {
        patch.visibility = 'FAMILY';
        patch.chosenFamilyIds = [prefill.familyId];
        patch.selectedFamilyMap = { [prefill.familyId]: true };
      }
      this.setData(patch);
      wx.showToast({ title: '已带入「' + prefill.dish + '」', icon: 'none', duration: 2200 });
    }
  },

  loadFamilies() {
    store.fetchMyFamilies().then((list) => {
      this.setData({ families: list || [] });
    }).catch(() => {});
  },

  /** 编辑模式：加载菜谱详情回填表单 */
  loadForEdit(id) {
    store.fetchRecipeDetail(id).then((data) => {
      const r = data.recipe || {};
      // 食材/调料分流：新数据按 type；老数据按单位是否克/用量是否数字启发式归位
      const foods = [];
      const seasons = [];
      (data.ingredients || []).forEach((i) => {
        const amt = i.amount == null ? '' : String(i.amount);
        const unit = i.unit || '';
        const isGramUnit = !unit || unit === '克' || unit.toLowerCase() === 'g';
        if (i.type === 'SEASONING') {
          seasons.push({ name: i.name || '', amount: amt });
        } else if (i.amountGrams != null) {
          foods.push({ name: i.name || '', grams: String(i.amountGrams) });
        } else if (amt !== '' && isGramUnit && !isNaN(Number(amt))) {
          foods.push({ name: i.name || '', grams: amt });
        } else {
          // 老数据非克数用量：归入调料区，编辑保存时即完成数据清洗
          seasons.push({ name: i.name || '', amount: amt ? amt + unit : '' });
        }
      });
      this.setData({
        editId: id,
        editTitle: r.title || '',
        title: r.title || '',
        covers: data.covers || [],
        ingredients: foods.length ? foods : [{ name: '', grams: '' }],
        seasonings: seasons.length ? seasons : [{ name: '', amount: '' }],
        servings: r.servings == null ? '' : String(r.servings),
        steps: (data.steps || []).length ? data.steps.map((s) => ({
          text: s.text || '',
          seasoning: s.seasoning || '',
          image: s.image || '',
          durationSec: s.durationSec == null ? '' : String(s.durationSec)
        })) : [{ text: '', seasoning: '', image: '', durationSec: '' }],
        visibility: r.visibility || 'PRIVATE',
        shareAll: !!data.shareAll,
        chosenFamilyIds: (data.sharedFamilyIds || (r.familyId ? [r.familyId] : [])),
        selectedFamilyMap: this.buildMap(data.sharedFamilyIds || (r.familyId ? [r.familyId] : [])),
        mealId: null,
        learningId: null
      });
      this.setData({ navTitle: '编辑菜谱' });
    }).catch((err) => wx.showToast({ title: err.message || '加载菜谱失败', icon: 'none' }));
  },

  exitEdit() {
    wx.showModal({
      title: '退出编辑',
      content: '未保存的修改将丢失，确认退出编辑？',
      success: (res) => {
        if (!res.confirm) return;
        this.setData({
          editId: null,
          editTitle: '',
          title: '',
          covers: [],
          ingredients: [{ name: '', grams: '' }],
          seasonings: [{ name: '', amount: '' }],
          suggests: [],
          suggestFor: -1,
          servings: '',
          steps: [{ text: '', seasoning: '', image: '', durationSec: '' }],
          visibility: 'PRIVATE',
          shareAll: false,
          chosenFamilyIds: [],
          mealId: null,
          learningId: null
        });
        this.setData({ navTitle: '发布' });
      }
    });
  },

  onTitle(e) { this.setData({ title: e.detail.value }); },
  onServings(e) { this.setData({ servings: e.detail.value }); },

  /** 食材输入：名称输入触发联想（300ms 防抖），克数输入直接落值 */
  onIngredient(e) {
    const idx = e.currentTarget.dataset.idx;
    const key = e.currentTarget.dataset.key;
    this.setData({ [`ingredients[${idx}].${key}`]: e.detail.value });
    if (key === 'name') {
      this.querySuggest(idx, e.detail.value);
    }
  },

  /** 食材名称联想（防抖请求后端 /api/food-calories/search） */
  querySuggest(idx, name) {
    if (this._suggestTimer) clearTimeout(this._suggestTimer);
    if (!name || !name.trim()) {
      this.setData({ suggests: [], suggestFor: -1 });
      return;
    }
    this._suggestTimer = setTimeout(() => {
      store.searchFoodCalories(name.trim()).then((list) => {
        // 竞态防护：响应到达时该行名称已变（删空/改词），丢弃本次结果
        if ((this.data.ingredients[idx] || {}).name !== name) return;
        this.setData({ suggests: (list || []).slice(0, 8), suggestFor: idx });
      }).catch(() => {});
    }, 300);
  },

  /** 点选联想项：回填食材名并收起联想 */
  pickSuggest(e) {
    const idx = e.currentTarget.dataset.idx;
    const name = e.currentTarget.dataset.name;
    this.setData({ [`ingredients[${idx}].name`]: name, suggests: [], suggestFor: -1 });
  },

  onSeasoning(e) {
    const idx = e.currentTarget.dataset.idx;
    const key = e.currentTarget.dataset.key;
    this.setData({ [`seasonings[${idx}].${key}`]: e.detail.value });
  },
  onStep(e) {
    const idx = e.currentTarget.dataset.idx;
    const key = e.currentTarget.dataset.key;
    this.setData({ [`steps[${idx}].${key}`]: e.detail.value });
  },
  addIngredient() { this.setData({ ingredients: this.data.ingredients.concat({ name: '', grams: '' }) }); },
  removeIngredient(e) {
    const ingredients = this.data.ingredients.filter((_, i) => i !== e.currentTarget.dataset.idx);
    // 行删除后联想胶囊会错位到下一行，直接收起
    this.setData({ ingredients, suggests: [], suggestFor: -1 });
  },
  addSeasoning() { this.setData({ seasonings: this.data.seasonings.concat({ name: '', amount: '' }) }); },
  removeSeasoning(e) {
    const seasonings = this.data.seasonings.filter((_, i) => i !== e.currentTarget.dataset.idx);
    this.setData({ seasonings });
  },
  addStep() { this.setData({ steps: this.data.steps.concat({ text: '', seasoning: '', image: '', durationSec: '' }) }); },
  /** 删除步骤：先弹窗确认，用户确认后再删 */
  removeStep(e) {
    const idx = e.currentTarget.dataset.idx;
    wx.showModal({
      title: '删除此步骤',
      content: '确定要删除第 ' + (idx + 1) + ' 步吗？删除后不可恢复。',
      confirmText: '确认删除',
      confirmColor: '#e64340',
      cancelText: '取消',
      success: (res) => {
        if (res.confirm) {
          const steps = this.data.steps.filter((_, i) => i !== idx);
          this.setData({ steps });
        }
      }
    });
  },

  /** 展示图多选：按剩余名额选图，逐张压缩上传（最多4张） */
  onChooseCovers() {
    const remain = MAX_COVERS - (this.data.covers || []).length;
    if (remain <= 0) {
      return wx.showToast({ title: `最多上传 ${MAX_COVERS} 张展示图`, icon: 'none' });
    }
    wx.chooseMedia({
      count: remain, mediaType: ['image'], sourceType: ['album', 'camera'],
      success: (res) => {
        wx.showLoading({ title: '上传中' });
        const files = res.tempFiles.map((f) => f.tempFilePath);
        const uploads = files.map((path) => store.uploadImage(path, 1080));
        Promise.all(uploads).then((urls) => {
          const covers = (this.data.covers || []).concat(urls).slice(0, MAX_COVERS);
          this.setData({ covers });
          wx.hideLoading();
        }).catch((err) => {
          // hideLoading 必须先于 showToast：两者共用同一原生视图，后调 hideLoading 会瞬间吞掉提示
          wx.hideLoading();
          wx.showToast({ title: err.message || '上传失败', icon: 'none', duration: 3000 });
        });
      }
    });
  },

  /** 删除某张展示图 */
  onRemoveCover(e) {
    const idx = e.currentTarget.dataset.idx;
    const covers = (this.data.covers || []).filter((_, i) => i !== idx);
    this.setData({ covers });
  },

  /** 预览展示图 */
  onPreviewCover(e) {
    const idx = e.currentTarget.dataset.idx;
    wx.previewImage({ current: this.data.covers[idx], urls: this.data.covers });
  },

  /** 步骤配图（大图区点击 → 选图 → 压缩 720 → 直传 COS → 回填 URL） */
  onStepImage(e) {
    const idx = e.currentTarget.dataset.idx;
    wx.chooseMedia({
      count: 1, mediaType: ['image'], sourceType: ['album', 'camera'],
      success: (res) => {
        const path = res.tempFiles[0].tempFilePath;
        wx.showLoading({ title: '上传中' });
        store.uploadImage(path, 720).then((url) => {
          this.setData({ [`steps[${idx}].image`]: url });
          wx.hideLoading();
        }).catch((err) => {
          wx.hideLoading();
          wx.showToast({ title: err.message || '上传失败', icon: 'none', duration: 3000 });
        });
      }
    });
  },

  /** 可见范围 3 按钮选择：私密菜单 / 发布到广场 / 分享到队伍 */
  onPickVis(e) {
    const v = e.currentTarget.dataset.v;
    if (v === 'FAMILY') {
      const fams = this.data.families || [];
      if (fams.length === 0) {
        return wx.showToast({ title: '请先创建队伍', icon: 'none' });
      }
      this.setData({ visibility: 'FAMILY' });
    } else {
      this.setData({ visibility: v, shareAll: false, chosenFamilyIds: [], selectedFamilyMap: {} });
    }
  },

  /** 全部队伍开关：开 = 分享到作者加入的所有队伍 */
  toggleShareAll() {
    this.setData({ shareAll: !this.data.shareAll, chosenFamilyIds: [], selectedFamilyMap: {} });
  },

  /** 指定队伍多选胶囊（同步维护选中 map，WXML 用 map 判断选中态） */
  toggleFamily(e) {
    const id = Number(e.currentTarget.dataset.id);
    const cur = this.data.chosenFamilyIds || [];
    const next = cur.includes(id) ? cur.filter((x) => x !== id) : cur.concat(id);
    this.setData({ chosenFamilyIds: next, selectedFamilyMap: this.buildMap(next) });
  },

  buildMap(ids) {
    const map = {};
    (ids || []).forEach((id) => { map[id] = true; });
    return map;
  },

  submit(saveDraft) {
    const { title, ingredients, seasonings, steps, visibility, shareAll, chosenFamilyIds, covers, mealId, learningId, editId, servings } = this.data;
    if (!title.trim()) {
      return wx.showToast({ title: '请填写菜谱标题', icon: 'none' });
    }
    if (visibility === 'FAMILY' && !shareAll && !(chosenFamilyIds || []).length) {
      return wx.showToast({ title: '请选择分享的队伍', icon: 'none' });
    }
    // 食材必须按克计量（热量计算依赖克数）
    const noGrams = (ingredients || []).find((i) => i.name && !String(i.grams || '').trim());
    if (noGrams) {
      return wx.showToast({ title: `食材「${noGrams.name}」请填写克数`, icon: 'none' });
    }
    // 食材 + 调料合并为 ingredients：FOOD 计热量（克），SEASONING 自由用量不计
    const foodRows = (ingredients || []).filter((i) => i.name).map((i) => ({
      name: i.name,
      amount: String(i.grams),
      unit: '克',
      type: 'FOOD',
      amountGrams: Number(i.grams)
    }));
    const seasonRows = (seasonings || []).filter((s) => s.name).map((s) => ({
      name: s.name,
      amount: s.amount || null,
      type: 'SEASONING'
    }));
    const payload = {
      title,
      covers: covers || [],
      cover: (covers && covers.length) ? covers[0] : '',
      visibility,
      shareAll: visibility === 'FAMILY' ? !!shareAll : false,
      familyIds: (visibility === 'FAMILY' && !shareAll) ? (chosenFamilyIds || []) : [],
      status: saveDraft ? 'DRAFT' : 'PUBLISHED',
      servings: servings ? Number(servings) : null,
      ingredients: foodRows.concat(seasonRows),
      steps: steps.filter(s => s.text || s.image).map(s => ({
        text: s.text,
        seasoning: s.seasoning || null,
        image: s.image || null,
        durationSec: s.durationSec ? Number(s.durationSec) : null
      }))
    };
    this.setData({ submitting: true });
    const req = editId ? store.updateRecipe(editId, payload) : store.createRecipe(payload);
    req.then((newId) => {
      wx.showToast({ title: saveDraft ? '已存草稿' : (editId ? '已更新' : '发布成功'), icon: 'success' });
      if (!saveDraft) {
        getApp().globalData.plazaDirty = true;
        // 来自心愿单「去完成」：发布成功即完成心愿（带新菜谱 id，许愿人可点开查看）
        if (mealId) {
          store.doneMealRequest(mealId, newId).catch(() => {});
          this.setData({ mealId: null });
        }
        // 来自待学习「去发布」：发布成功即移出学习清单
        if (learningId) {
          store.deleteLearning(learningId).catch(() => {});
          this.setData({ learningId: null });
        }
      }
      if (editId) {
        this.setData({ editId: null, editTitle: '' });
        this.setData({ navTitle: '发布' });
      }
      setTimeout(() => wx.switchTab({ url: '/pages/plaza/plaza' }), 600);
    })
      .catch((err) => wx.showToast({ title: err.message || '发布失败', icon: 'none' }))
      .finally(() => this.setData({ submitting: false }));
  },

  onPublish() { this.submit(false); },
  onSaveDraft() { this.submit(true); }
});
