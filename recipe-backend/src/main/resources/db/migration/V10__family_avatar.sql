-- 队伍头像：创建/设置时可上传，列表与详情展示完整图片
ALTER TABLE `family`
  ADD COLUMN `avatar` VARCHAR(512) DEFAULT NULL COMMENT '队伍头像' AFTER `name`;
