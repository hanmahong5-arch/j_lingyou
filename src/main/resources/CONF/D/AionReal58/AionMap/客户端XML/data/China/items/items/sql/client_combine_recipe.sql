DROP TABLE IF EXISTS xmldb_suiyue.client_combine_recipe;
CREATE TABLE xmldb_suiyue.client_combine_recipe (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(56) COMMENT 'name',
    `desc` VARCHAR(60) COMMENT 'desc',
    `combineskill` VARCHAR(11) COMMENT 'combineskill',
    `qualification_race` VARCHAR(8) COMMENT 'qualification_race',
    `required_skillpoint` VARCHAR(3) COMMENT 'required_skillpoint',
    `auto_learn` VARCHAR(1) COMMENT 'auto_learn',
    `product` VARCHAR(40) COMMENT 'product',
    `product_quantity` VARCHAR(3) COMMENT 'product_quantity',
    `combine_recipe_expansion` VARCHAR(64) COMMENT 'combine_recipe_expansion',
    `require_dp` VARCHAR(4) COMMENT 'require_dp',
    `mobile_event` VARCHAR(4) COMMENT 'mobile_event',
    `combo1_product` VARCHAR(42) COMMENT 'combo1_product',
    `combo2_product` VARCHAR(33) COMMENT 'combo2_product',
    `max_production_count` VARCHAR(2) COMMENT 'max_production_count',
    `desc_craftman` VARCHAR(42) COMMENT 'desc_craftman',
    `craft_delay_id` VARCHAR(6) COMMENT 'craft_delay_id',
    `craft_delay_time` VARCHAR(5) COMMENT 'craft_delay_time',
    `task_type` VARCHAR(1) COMMENT 'task_type',
    `combo3_product` VARCHAR(23) COMMENT 'combo3_product',
    `combo4_product` VARCHAR(23) COMMENT 'combo4_product',
    `highdeva_type` VARCHAR(4) COMMENT 'highdeva_type'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_combine_recipe';

DROP TABLE IF EXISTS xmldb_suiyue.client_combine_recipe__combine_recipe_expansion__data;
CREATE TABLE xmldb_suiyue.client_combine_recipe__combine_recipe_expansion__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `component_quantity` VARCHAR(1) COMMENT 'component_quantity',
    `component1` VARCHAR(38) COMMENT 'component1',
    `compo1_quantity` VARCHAR(4) COMMENT 'compo1_quantity',
    `component2` VARCHAR(38) COMMENT 'component2',
    `compo2_quantity` VARCHAR(3) COMMENT 'compo2_quantity',
    `component3` VARCHAR(32) COMMENT 'component3',
    `compo3_quantity` VARCHAR(4) COMMENT 'compo3_quantity',
    `component4` VARCHAR(51) COMMENT 'component4',
    `compo4_quantity` VARCHAR(3) COMMENT 'compo4_quantity',
    `component5` VARCHAR(28) COMMENT 'component5',
    `compo5_quantity` VARCHAR(4) COMMENT 'compo5_quantity',
    `component6` VARCHAR(28) COMMENT 'component6',
    `compo6_quantity` VARCHAR(3) COMMENT 'compo6_quantity',
    `component7` VARCHAR(30) COMMENT 'component7',
    `compo7_quantity` VARCHAR(3) COMMENT 'compo7_quantity',
    `component8` VARCHAR(29) COMMENT 'component8',
    `compo8_quantity` VARCHAR(2) COMMENT 'compo8_quantity'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_combine_recipe__combine_recipe_expansion__data';

