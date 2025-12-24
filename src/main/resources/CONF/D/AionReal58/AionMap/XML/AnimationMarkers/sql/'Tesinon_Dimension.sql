DROP TABLE IF EXISTS xmldb_suiyue.'Tesinon_Dimension;
CREATE TABLE xmldb_suiyue.'Tesinon_Dimension (
    `hitpoint` VARCHAR(255) PRIMARY KEY COMMENT 'hitpoint',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `sound` VARCHAR(64) COMMENT 'sound',
    `_attr__sound__file` VARCHAR(128) COMMENT 'file',
    `_attr__sound__TOOL_desc` VARCHAR(128) COMMENT 'TOOL_desc',
    `_attr__sound__TOOL_duration` VARCHAR(128) COMMENT 'TOOL_duration',
    `_attr__sound__TOOL_fadeoutStart` VARCHAR(128) COMMENT 'TOOL_fadeoutStart',
    `_attr__sound__TOOL_fadeoutTime` VARCHAR(128) COMMENT 'TOOL_fadeoutTime',
    `_attr__sound__TOOL_InRadius` VARCHAR(128) COMMENT 'TOOL_InRadius',
    `_attr__sound__TOOL_Is3D` VARCHAR(128) COMMENT 'TOOL_Is3D',
    `_attr__sound__TOOL_Loop` VARCHAR(128) COMMENT 'TOOL_Loop',
    `_attr__sound__TOOL_OutRadius` VARCHAR(128) COMMENT 'TOOL_OutRadius',
    `_attr__sound__TOOL_pan` VARCHAR(128) COMMENT 'TOOL_pan',
    `_attr__sound__TOOL_ParamId` VARCHAR(128) COMMENT 'TOOL_ParamId',
    `_attr__sound__TOOL_Stream` VARCHAR(128) COMMENT 'TOOL_Stream',
    `_attr__sound__type` VARCHAR(128) COMMENT 'type',
    `_attr__sound__vol` VARCHAR(128) COMMENT 'vol',
    `_attr__sound__when` VARCHAR(128) COMMENT 'when',
    `skillfx` VARCHAR(64) COMMENT 'skillfx',
    `_attr__skillfx__TOOL_ParamId` VARCHAR(128) COMMENT 'TOOL_ParamId',
    `_attr__skillfx__when` VARCHAR(128) COMMENT 'when',
    `_attr_animation_length` VARCHAR(128) COMMENT 'animation_length',
    `_attr_name` VARCHAR(128) COMMENT 'name'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = ''Tesinon_Dimension';

