-- V6: 菜谱展示图（多图，最多4张）+ 步骤酌料
-- recipe.covers 存 JSON 数组字符串，如 ["url1","url2"]；cover 保留为第一张（列表/分享兼容）
ALTER TABLE `recipe`
  ADD COLUMN `covers` TEXT DEFAULT NULL COMMENT '展示图 JSON 数组（最多4张，cover 为第一张）' AFTER `cover`;

-- step.seasoning 存该步骤使用的酌料/调料
ALTER TABLE `step`
  ADD COLUMN `seasoning` VARCHAR(255) DEFAULT NULL COMMENT '使用酌料' AFTER `text`;
