DROP TABLE IF EXISTS xmldb_suiyue.client_assembly_items;
CREATE TABLE xmldb_suiyue.client_assembly_items (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(39) COMMENT 'name',
    `part_critical_prob` VARCHAR(4) COMMENT 'part_critical_prob',
    `part_critical_name` VARCHAR(17) COMMENT 'part_critical_name',
    `assemble_parts` VARCHAR(64) COMMENT 'assemble_parts'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_assembly_items';

DROP TABLE IF EXISTS xmldb_suiyue.client_assembly_items__assemble_parts__data;
CREATE TABLE xmldb_suiyue.client_assembly_items__assemble_parts__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `part_item` VARCHAR(49) COMMENT 'part_item',
    `part_item_num` VARCHAR(4) COMMENT 'part_item_num'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_assembly_items__assemble_parts__data';

