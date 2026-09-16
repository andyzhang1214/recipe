/**
 * 主题管理：皮肤切换 + 背景色主题 + 调色盘自定义色
 * 主题名与 app.wxss 中 .page.theme-* 一一对应
 * cute / clean 为完整皮肤（换形 + 换色），其余为经典配色（仅换色）
 * custom 为经典配色下的调色盘主题：token 复用暖白，底色由用户自选色内联覆盖
 */

const STORAGE_KEY = 'recipe_theme';
const DEFAULT = 'light';
const COLOR_RE = /^#[0-9a-fA-F]{6}$/;

const THEMES = [
  { key: 'cute', name: '可爱风', desc: '软软食堂 · 贴纸手账', preview: '#FF6E8E', skin: true },
  { key: 'clean', name: '简洁风', desc: '厨房手记 · 干练极简', preview: '#1B1A18', skin: true },
  { key: 'light', name: '暖白', desc: '温馨明亮', preview: '#FAF6F1' },
  { key: 'cream', name: '奶油', desc: '奶油质感', preview: '#F8F2E8' },
  { key: 'latte', name: '奶咖', desc: '温暖醇厚', preview: '#F3EBDF' },
  { key: 'sage', name: '浅绿', desc: '自然清新', preview: '#F1F4EC' },
  { key: 'mist', name: '雾蓝', desc: '冷静柔和', preview: '#EEF2F6' },
  { key: 'custom', name: '自定义', desc: '调色盘 · 专属底色', preview: '#FFD9B8' },
  { key: 'dark', name: '深色', desc: '夜间护眼', preview: '#16120E' }
];

// 外观面板分组：皮肤在前，经典配色在后
const GROUPS = [
  { title: '风格皮肤', items: THEMES.filter((t) => t.skin) },
  { title: '经典配色', items: THEMES.filter((t) => !t.skin) }
];

// 皮肤自选背景色：每个皮肤独立记忆
// default 沿用皮肤原底色；glass 磨砂玻璃；pink 淡粉；custom 调色盘自选（颜色存 CUSTOM_COLOR）
const SKIN_BGS = {
  cute: [
    { key: 'default', name: '奶油', color: '#FFF6EF' },
    { key: 'glass', name: '玻璃', color: '#FCF1F4' },
    { key: 'pink', name: '淡粉', color: '#FFE4EC' },
    { key: 'custom', name: '自定义', color: '' }
  ],
  clean: [
    { key: 'default', name: '暖灰', color: '#F5F4F2' },
    { key: 'glass', name: '玻璃', color: '#EFF1F4' },
    { key: 'pink', name: '淡粉', color: '#FAEBF0' },
    { key: 'custom', name: '自定义', color: '' }
  ]
};
const BG_STORAGE_KEY = 'recipe_skin_bg';
const CUSTOM_COLOR_KEY = 'recipe_custom_colors'; // { cute: '#xxx', clean: '#xxx', classic: '#xxx' }

// 主题对应的窗口背景色（overscroll 露出的底色，与 .page 的 var(--bg) 一致）
const BG_COLORS = {
  cute: '#FFF6EF',
  clean: '#F5F4F2',
  light: '#FAF6F1',
  cream: '#F8F2E8',
  latte: '#F3EBDF',
  sage: '#F1F4EC',
  mist: '#EEF2F6',
  custom: '#FAF6F1',
  dark: '#16120E'
};

function get() {
  const t = wx.getStorageSync(STORAGE_KEY);
  return THEMES.some((x) => x.key === t) ? t : DEFAULT;
}

// 广播当前主题 + 皮肤背景选择（自定义 tabBar 据此立即换肤换底）
function emitChange(key) {
  const app = getApp();
  if (app && app.bus) app.bus.emit('theme:change', { theme: key, skinBg: getSkinBg(key) });
}

function set(key) {
  wx.setStorageSync(STORAGE_KEY, key);
  const app = getApp();
  if (app && app.globalData) {
    app.globalData.theme = key;
  }
  emitChange(key);
  applyBackgroundColor(key);
}

// ---- 调色盘自定义色（scope：cute / clean 皮肤底色，classic 经典主题底色）----
function getCustomColor(scope) {
  const map = wx.getStorageSync(CUSTOM_COLOR_KEY) || {};
  return COLOR_RE.test(map[scope] || '') ? map[scope] : null;
}

function setCustomColor(scope, color) {
  if (!COLOR_RE.test(color)) return;
  const map = wx.getStorageSync(CUSTOM_COLOR_KEY) || {};
  map[scope] = color;
  wx.setStorageSync(CUSTOM_COLOR_KEY, map);
}

// 当前皮肤的背景选择（非皮肤主题返回 ''，页面绑定 bg-{{skinBg}} 即无类名）
function getSkinBg(skin) {
  const opts = SKIN_BGS[skin];
  if (!opts) return '';
  const map = wx.getStorageSync(BG_STORAGE_KEY) || {};
  return opts.some((o) => o.key === map[skin]) ? map[skin] : 'default';
}

// 切换皮肤背景：通知 tabBar 换底 + 同步导航栏/窗口底色，页面 class 由 applyTheme / 本页 setData 刷新
function setSkinBg(skin, bgKey) {
  const opts = SKIN_BGS[skin];
  if (!opts || !opts.some((o) => o.key === bgKey)) return;
  const map = wx.getStorageSync(BG_STORAGE_KEY) || {};
  map[skin] = bgKey;
  wx.setStorageSync(BG_STORAGE_KEY, map);
  const app = getApp();
  if (app && app.globalData && app.globalData.theme === skin) {
    emitChange(skin);
    applyBackgroundColor(skin);
  }
}

// 皮肤背景 / 经典自定义主题对应的实底色；无则 null 走默认表
function skinBgColor(key) {
  const opts = SKIN_BGS[key];
  if (!opts) return null;
  const cur = getSkinBg(key);
  if (cur === 'custom') return getCustomColor(key);
  const opt = opts.find((o) => o.key === cur);
  return (opt && opt.key !== 'default') ? opt.color : null;
}

// 主题实际生效的底色（含皮肤背景覆盖与经典自定义色）
function effectiveBgColor(key) {
  let c = skinBgColor(key);
  if (key === 'custom') c = getCustomColor('classic');
  return c || BG_COLORS[key] || BG_COLORS.light;
}

// 页面根节点内联样式：调色盘自选色直接覆盖底色（--bg 供子组件取色，--nav-blur 供毛玻璃导航栏）
function getPageBgStyle(key) {
  let c = null;
  if (key === 'cute' || key === 'clean') {
    if (getSkinBg(key) === 'custom') c = getCustomColor(key);
  } else if (key === 'custom') {
    c = getCustomColor('classic');
  }
  if (!c) return '';
  const r = parseInt(c.slice(1, 3), 16);
  const g = parseInt(c.slice(3, 5), 16);
  const b = parseInt(c.slice(5, 7), 16);
  return `background-color: ${c}; --bg: ${c}; --nav-blur: rgba(${r}, ${g}, ${b}, 0.88);`;
}

function applyBackgroundColor(key) {
  wx.setBackgroundColor({ backgroundColor: effectiveBgColor(key), fail: () => {} });
}

module.exports = {
  THEMES, GROUPS, SKIN_BGS, DEFAULT,
  get, set,
  getSkinBg, setSkinBg,
  getCustomColor, setCustomColor,
  getPageBgStyle, effectiveBgColor,
  applyBackgroundColor
};
