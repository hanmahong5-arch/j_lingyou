DROP TABLE IF EXISTS xmldb_suiyue.pve_adjust_special01;
CREATE TABLE xmldb_suiyue.pve_adjust_special01 (
    `__comment__` VARCHAR(255) PRIMARY KEY COMMENT '__comment__',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `pve_attack_ratio_physical_light` VARCHAR(5) COMMENT 'pve_attack_ratio_physical_light',
    `pve_defend_ratio_physical_light` VARCHAR(3) COMMENT 'pve_defend_ratio_physical_light',
    `pve_attack_ratio_magical_light` VARCHAR(5) COMMENT 'pve_attack_ratio_magical_light',
    `pve_defend_ratio_magical_light` VARCHAR(3) COMMENT 'pve_defend_ratio_magical_light',
    `pve_attack_ratio_physical_dark` VARCHAR(5) COMMENT 'pve_attack_ratio_physical_dark',
    `pve_defend_ratio_physical_dark` VARCHAR(3) COMMENT 'pve_defend_ratio_physical_dark',
    `pve_attack_ratio_magical_dark` VARCHAR(5) COMMENT 'pve_attack_ratio_magical_dark',
    `pve_defend_ratio_magical_dark` VARCHAR(3) COMMENT 'pve_defend_ratio_magical_dark',
    `_attr_name` VARCHAR(128) COMMENT 'name'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'pve_adjust_special01';

