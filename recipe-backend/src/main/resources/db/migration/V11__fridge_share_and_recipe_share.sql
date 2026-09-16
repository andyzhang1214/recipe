-- 菜谱分享到多队伍：全部队伍标记 + 指定队伍多对多
ALTER TABLE `recipe`
  ADD COLUMN `share_all_families` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=分享到作者全部队伍' AFTER `visibility`;

CREATE TABLE `recipe_family_share` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `recipe_id` BIGINT NOT NULL,
  `family_id` BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_recipe_family` (`recipe_id`, `family_id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜谱-队伍分享关系';

-- 个人冰箱共享：主人邀请他人共用冰箱
CREATE TABLE `fridge_member` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `owner_id` BIGINT NOT NULL COMMENT '冰箱主人',
  `user_id` BIGINT NOT NULL COMMENT '被邀请成员',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_owner_user` (`owner_id`, `user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人冰箱共享成员';

-- 个人冰箱邀请码（类似队伍邀请）
CREATE TABLE `fridge_invite` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `owner_id` BIGINT NOT NULL COMMENT '冰箱主人',
  `token` VARCHAR(32) NOT NULL,
  `valid_days` INT NOT NULL DEFAULT 7,
  `expires_at` DATETIME NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REVOKED',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_token` (`token`),
  KEY `idx_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人冰箱邀请';

-- 冰箱操作记录：谁添加/修改/删除了食材
CREATE TABLE `fridge_log` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `family_id` BIGINT NULL COMMENT '队伍冰箱时记录',
  `owner_user_id` BIGINT NULL COMMENT '个人/共享冰箱主人',
  `operator_id` BIGINT NOT NULL COMMENT '操作人',
  `action` VARCHAR(16) NOT NULL COMMENT 'ADD/UPDATE/DELETE',
  `item_name` VARCHAR(64) NOT NULL,
  `detail` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_fridge` (`family_id`, `owner_user_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='冰箱操作记录';
