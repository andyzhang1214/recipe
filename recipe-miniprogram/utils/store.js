/**
 * 统一请求层（复用 nutcards 的 callContainer 方案）：
 * - USE_CLOUD=true（生产/体验版）：wx.cloud.callContainer，免备案免配域名
 * - USE_CLOUD=false（本地调试）：wx.request + BASE_URL=localhost:8080
 * 所有接口都走 callApi()，自动携带 Authorization，返回 body.data.data。
 */

const API = {
  USE_CLOUD: true, // 生产/体验版：true 走 wx.cloud.callContainer；本地联调改回 false
  BASE_URL: 'http://localhost:8080',
  CLOUD_ENV: 'REPLACE_WITH_YOUR_CLOUD_ENV_ID', // 云托管控制台 → 服务 → 环境ID
  CLOUD_SERVICE_NAME: 'recipe' // 云托管服务名（服务列表里的 recipe）
};

function getToken() {
  return wx.getStorageSync('recipe_token') || '';
}

function setToken(token) {
  wx.setStorageSync('recipe_token', token || '');
}

function clearToken() {
  wx.removeStorageSync('recipe_token');
}


// timeout=15000：callContainer 上限 15s；覆盖冷启动（服务缩容到 0 后重新拉起需 ~12s）
function callApi({ path, method = 'GET', data = {}, header = {}, timeout = 15000 }) {
  return new Promise((resolve, reject) => {
    const token = getToken();
    const finalHeader = Object.assign({}, header);
    if (token) finalHeader['Authorization'] = 'Bearer ' + token;

    const done = (body) => {
      if (body && body.code === 0) {
        resolve(body.data);
      } else if (body && body.code === 401) {
        // 未登录/登录过期：清理本地登录态并广播，让页面统一弹出登录层
        clearToken();
        const app = getApp();
        if (app && app.globalData) {
          app.globalData.token = '';
          app.globalData.userInfo = null;
        }
        const err = new Error(body.message || '请先登录');
        err.code = 'AUTH';
        reject(err);
      } else {
        const err = new Error((body && body.message) || '请求失败');
        if (body && body.code) err.code = body.code;
        reject(err);
      }
    };
    const fail = (err) => reject(new Error(err.errMsg || '网络异常'));

    if (API.USE_CLOUD) {
      wx.cloud.callContainer({
        config: { env: API.CLOUD_ENV },
        path,
        method,
        data,
        header: Object.assign({}, finalHeader, { 'X-WX-SERVICE': API.CLOUD_SERVICE_NAME }),
        timeout
      }).then(res => done(res.data)).catch(fail);
    } else {
      wx.request({
        url: API.BASE_URL + path,
        method,
        data,
        header: finalHeader,
        timeout,
        success: (res) => done(res.data),
        fail
      });
    }
  });
}

// ---- 简单内存缓存（低频公共数据，TTL 过期自动失效） ----
const _cache = new Map();
function cachedGet(path, ttl = 60000) {
  const now = Date.now();
  const hit = _cache.get(path);
  if (hit && now - hit.ts < ttl) {
    return Promise.resolve(hit.data);
  }
  return callApi({ path }).then((data) => {
    _cache.set(path, { ts: Date.now(), data });
    return data;
  });
}

/** 登录：wx.login 拿 code → 后端换 openid + JWT。返回含 isNew：true 表示新用户（昵称未完善），由登录弹层再收集头像昵称走 updateProfile 完成注册 */
function login({ nickname, avatar } = {}) {
  return new Promise((resolve, reject) => {
    wx.login({
      success: (res) => {
        if (!res.code) return reject(new Error('微信登录失败'));
        callApi({
          path: '/api/auth/wxlogin',
          method: 'POST',
          data: { code: res.code, nickname: nickname || '', avatar: avatar || '' }
        }).then((data) => {
          setToken(data.token);
          const app = getApp();
          app.globalData.token = data.token;
          app.globalData.userInfo = data;
          resolve(data);
        }).catch(reject);
      },
      fail: reject
    });
  });
}

function logout() {
  clearToken();
  const app = getApp();
  app.globalData.token = '';
  app.globalData.userInfo = null;
}

function fetchMe() {
  return callApi({ path: '/api/auth/me', method: 'GET' });
}

/** 更新个人资料（昵称/头像/主页可见性，可只传其一） */
function updateProfile(data) {
  return callApi({ path: '/api/auth/profile', method: 'PUT', data });
}

// ---- 关注 / 用户主页 ----
function fetchUserProfile(uid) {
  return callApi({ path: `/api/users/${uid}/profile` });
}
function fetchUserRecipes(uid, page) {
  return callApi({ path: `/api/users/${uid}/recipes?page=${page}&size=10` });
}
function toggleFollow(uid) {
  return callApi({ path: `/api/follows/${uid}/toggle`, method: 'POST' });
}
function fetchMyFollows(type) {
  return callApi({ path: `/api/follows/my?type=${type}` });
}
function fetchUserFollows(uid, type) {
  return callApi({ path: `/api/follows/${uid}/list?type=${type}` });
}
function fetchFollowStatus(uids) {
  return callApi({ path: `/api/follows/status?uids=${uids.join(',')}` });
}

/**
 * 图片直传 COS：
 * 1. wx.compressImage 压缩（封面/冰箱图 1080、步骤图 720，质量按原图尺寸动态）
 * 2. 后端取预签名 PUT URL（POST /api/upload/presign）
 * 3. wx.uploadFile 直传，返回可访问的 fileUrl
 */
function compressImage(path, maxSize) {
  return new Promise((resolve, reject) => {
    wx.getImageInfo({
      src: path,
      success: (info) => {
        const longSide = Math.max(info.width, info.height);
        const quality = longSide > 2000 ? 60 : 80;
        wx.compressImage({
          src: path,
          quality,
          compressedWidth: longSide > maxSize ? maxSize : longSide,
          success: (res) => resolve(res.tempFilePath),
          fail: () => resolve(path)
        });
      },
      fail: () => resolve(path)
    });
  });
}

function uploadImage(tempFilePath, maxSize = 1080) {
  return compressImage(tempFilePath, maxSize).then((compressed) => {
    const name = compressed.split('/').pop() || 'image.jpg';
    const ext = (name.includes('.') ? name.split('.').pop() : 'jpg').toLowerCase();
    const MIME = { jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', webp: 'image/webp', gif: 'image/gif' };
    const contentType = MIME[ext] || 'image/jpeg';
    return callApi({
      path: '/api/upload/presign',
      method: 'POST',
      data: { fileName: name, contentType }
    }).then((sign) => new Promise((resolve, reject) => {
      wx.uploadFile({
        url: sign.uploadUrl,
        filePath: compressed,
        name: 'file',
        formData: { key: sign.key }, // POST 表单上传必须带 key 字段，否则报 Key 不匹配
        success: (res) => {
          if (res.statusCode >= 200 && res.statusCode < 300) {
            resolve(sign.fileUrl);
          } else {
            reject(new Error('上传失败(' + res.statusCode + ')'));
          }
        },
        fail: (err) => {
          const msg = err.errMsg || '';
          if (msg.indexOf('url not in domain list') >= 0) {
            reject(new Error('COS 域名未加白名单：请在小程序后台「开发设置-服务器域名-uploadFile」添加图片域名'));
          } else if (msg.indexOf('fail timeout') >= 0) {
            reject(new Error('上传超时，请重试'));
          } else {
            reject(new Error(msg || '上传失败'));
          }
        }
      });
    }));
  });
}

// ---- 首页 Banner（隐藏：后台推送才展示） ----
function fetchBanners() {
  return cachedGet('/api/banners', 60000);
}

// ---- 菜谱 ----
function fetchPublicRecipes(page, size, keyword) {
  const q = `page=${page || 0}&size=${size || 10}` + (keyword ? `&keyword=${encodeURIComponent(keyword)}` : '');
  return callApi({ path: `/api/recipes/public?${q}` });
}
function fetchMyRecipes(status) {
  const q = status ? `?status=${encodeURIComponent(status)}` : '';
  return callApi({ path: `/api/recipes/mine${q}` });
}
function fetchRecipeDetail(id) {
  return callApi({ path: `/api/recipes/${id}` });
}
function createRecipe(data) {
  return callApi({ path: '/api/recipes', method: 'POST', data });
}
function updateRecipe(id, data) {
  return callApi({ path: `/api/recipes/${id}`, method: 'PUT', data });
}
function deleteRecipe(id) {
  return callApi({ path: `/api/recipes/${id}`, method: 'DELETE' });
}
function rateRecipe(id, stars) {
  return callApi({ path: `/api/recipes/${id}/rating`, method: 'POST', data: { stars } });
}
function fetchComments(id) {
  return callApi({ path: `/api/recipes/${id}/comments` });
}
function addComment(id, content, parentId) {
  return callApi({ path: `/api/recipes/${id}/comments`, method: 'POST', data: { content, parentId: parentId || null } });
}

// ---- 热量库（食材联想） ----
function searchFoodCalories(keyword) {
  return callApi({ path: `/api/food-calories/search?keyword=${encodeURIComponent(keyword || '')}` });
}

// ---- 收藏 ----
function toggleFavorite(recipeId) {
  return callApi({ path: `/api/favorites/toggle/${recipeId}`, method: 'POST' });
}
function fetchFavoriteStatus(recipeId) {
  return callApi({ path: `/api/favorites/status/${recipeId}` });
}
function fetchMyFavorites() {
  return callApi({ path: '/api/favorites/mine' });
}

// ---- 热量查询（游客可用） ----
function fetchFoodCalories(keyword, category) {
  const q = `keyword=${encodeURIComponent(keyword || '')}` + (category ? `&category=${encodeURIComponent(category)}` : '');
  return cachedGet(`/api/food-calories?${q}`, 60000);
}

// ---- 圈层 ----
function createFamily(data) {
  return callApi({ path: '/api/family', method: 'POST', data });
}
function fetchMyFamilies() {
  return callApi({ path: '/api/family/mine' });
}
function joinFamily(joinCode) {
  return callApi({ path: '/api/family/join', method: 'POST', data: { joinCode } });
}
function joinByInvite(invite) {
  return callApi({ path: '/api/family/join-by-invite', method: 'POST', data: { invite } });
}
function joinFamilyById(id) {
  return callApi({ path: `/api/family/${id}/join`, method: 'POST' });
}

// ---- 群员点餐「我想吃」 ----
function submitMealRequest(data) {
  return callApi({ path: '/api/meal-requests', method: 'POST', data });
}
function fetchMealOverview() {
  return callApi({ path: '/api/meal-requests/overview' });
}
function doneMealRequest(id, recipeId) {
  return callApi({ path: `/api/meal-requests/${id}/done`, method: 'POST', data: recipeId ? { recipeId } : {} });
}
function learningFromMeal(mealId) {
  return callApi({ path: `/api/learning/from-meal/${mealId}`, method: 'POST' });
}
function fetchLearning() {
  return callApi({ path: '/api/learning' });
}
function deleteLearning(id) {
  return callApi({ path: `/api/learning/${id}`, method: 'DELETE' });
}
function fetchFamilyDetail(id) {
  return callApi({ path: `/api/family/${id}` });
}
function fetchFamilyMembers(id) {
  return callApi({ path: `/api/family/${id}/members` });
}
function fetchFamilyRecipes(id) {
  return callApi({ path: `/api/family/${id}/recipes` });
}
function approveMember(id, memberId, pass) {
  return callApi({ path: `/api/family/${id}/members/${memberId}/approve?pass=${pass}`, method: 'POST' });
}
function removeMember(id, memberId) {
  return callApi({ path: `/api/family/${id}/members/${memberId}`, method: 'DELETE' });
}
function updateFamily(id, data) {
  return callApi({ path: `/api/family/${id}`, method: 'PUT', data });
}
function dissolveFamily(id) {
  return callApi({ path: `/api/family/${id}`, method: 'DELETE' });
}
function createInvite(id, type, validDays) {
  return callApi({ path: `/api/family/${id}/invites`, method: 'POST', data: { type, validDays } });
}
function fetchInvites(id) {
  return callApi({ path: `/api/family/${id}/invites` });
}
function revokeInvite(inviteId) {
  return callApi({ path: `/api/family/invites/${inviteId}`, method: 'DELETE' });
}

// ---- 冰箱（familyId=队伍冰箱 / ownerId=共享冰箱 / 缺省=个人冰箱） ----
function fetchFridgeItems(sort, category, familyId, ownerId) {
  const parts = [];
  if (sort) parts.push(`sort=${sort}`);
  if (category) parts.push(`category=${encodeURIComponent(category)}`);
  if (familyId) parts.push(`familyId=${familyId}`);
  if (ownerId) parts.push(`ownerId=${ownerId}`);
  return callApi({ path: `/api/fridge-items${parts.length ? '?' + parts.join('&') : ''}` });
}
function createFridgeItem(data) {
  return callApi({ path: '/api/fridge-items', method: 'POST', data });
}
function deleteFridgeItem(id) {
  return callApi({ path: `/api/fridge-items/${id}`, method: 'DELETE' });
}
function fetchFridgeLogs(familyId, ownerId) {
  const parts = [];
  if (familyId) parts.push(`familyId=${familyId}`);
  if (ownerId) parts.push(`ownerId=${ownerId}`);
  return callApi({ path: `/api/fridge-items/logs${parts.length ? '?' + parts.join('&') : ''}` });
}

// ---- 个人冰箱共享（邀请/加入/成员） ----
function createFridgeInvite(validDays) {
  return callApi({ path: '/api/fridge-share/invites', method: 'POST', data: { validDays } });
}
function joinFridgeByInvite(token) {
  return callApi({ path: '/api/fridge-share/join', method: 'POST', data: { token } });
}
function fetchSharedFridges() {
  return callApi({ path: '/api/fridge-share/shared-with-me' });
}

module.exports = {
  API,
  callApi,
  login,
  logout,
  getToken,
  fetchMe,
  updateProfile,
  fetchUserProfile,
  fetchUserRecipes,
  toggleFollow,
  fetchMyFollows,
  fetchUserFollows,
  fetchFollowStatus,
  uploadImage,
  fetchBanners,
  fetchPublicRecipes,
  fetchMyRecipes,
  fetchRecipeDetail,
  createRecipe,
  updateRecipe,
  deleteRecipe,
  rateRecipe,
  fetchComments,
  addComment,
  toggleFavorite,
  fetchFavoriteStatus,
  fetchMyFavorites,
  fetchFoodCalories,
  searchFoodCalories,
  createFamily,
  fetchMyFamilies,
  joinFamily,
  joinByInvite,
  joinFamilyById,
  submitMealRequest,
  fetchMealOverview,
  doneMealRequest,
  learningFromMeal,
  fetchLearning,
  deleteLearning,
  fetchFamilyDetail,
  fetchFamilyMembers,
  fetchFamilyRecipes,
  approveMember,
  removeMember,
  updateFamily,
  dissolveFamily,
  createInvite,
  fetchInvites,
  revokeInvite,
  fetchFridgeItems,
  createFridgeItem,
  deleteFridgeItem,
  fetchFridgeLogs,
  createFridgeInvite,
  joinFridgeByInvite,
  fetchSharedFridges
};
