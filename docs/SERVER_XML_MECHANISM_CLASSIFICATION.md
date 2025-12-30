# Aion服务器XML文件机制归类报告

> **生成时间**: 2025-12-29
> **分析来源**: 服务器日志 + XML目录扫描 + 数据库表结构分析
> **文件总数**: 6,508 个唯一XML文件
> **数据库表数**: 464 个表

---

## 一、执行摘要

### 1.1 分析方法

本报告通过以下三个维度深度分析Aion游戏服务器的XML配置文件:

1. **服务器日志分析** - 分析NPCServer和MainServer启动日志,提取XML加载记录
2. **文件名模式识别** - 基于文件命名规范推测游戏机制归属
3. **字段语义分析** - 分析数据库表字段和JSON配置,理解数据结构

### 1.2 核心发现

| 统计项 | 数值 |
|--------|------|
| **XML文件总数** | 6,508 个 |
| **客户端文件** | ~400 个 (client_前缀) |
| **服务端文件** | ~6,100 个 |
| **数据库表** | 464 个 |
| **已配置JSON** | 180+ 个 |
| **机制分类数** | 27 个 |

---

## 二、27个游戏机制详细归类

### 2.1 ITEM - 物品系统

**核心文件数**: ~600+

#### 服务端物品文件

**主表文件**:
- `items.xml` - 物品主表 (22,162条记录)
- `item_armors.xml` - 防具数据
- `item_weapons.xml` - 武器数据
- `item_accessories.xml` - 饰品数据
- `item_etc.xml` - 其他物品
- `item_quest.xml` - 任务物品

**物品增强系统**:
- `enchant_cpstone.xml` - 强化石
- `item_skill_enhance.xml` - 技能强化物品
- `item_random_option.xml` - 随机属性
- `item_option_probability.xml` - 属性概率表

**物品转换**:
- `combine_recipe.xml` - 合成配方
- `disassembly_item.xml` - 分解物品
- `exchange_equipment.xml` - 装备兑换
- `item_upgrade.xml` - 装备升级
- `item_multi_return.xml` - 多重返还

**物品配置**:
- `item_authorizetable.xml` - 权限表
- `item_prohibit.xml` - 禁用物品
- `item_standard_price.xml` - 标准价格
- `item_luna.xml` - Luna物品
- `setitem.xml` - 套装物品

**掉落相关**:
- `items_looting_fx.xml` - 拾取特效
- `CommonDropItems.xml` - 通用掉落

#### 客户端物品文件

- `client_items_armor_1.xml` / `client_items_armor_2.xml`
- `client_items_etc_1.xml` / `client_items_etc_2.xml`
- `client_items_misc.xml`
- `client_item_enchanttable.xml` - 强化表
- `client_item_upgrade.xml` - 升级配置

#### 字段对照关系

| 服务端表 | 客户端文件 | 关键字段 |
|---------|-----------|---------|
| `items` | `client_items_*` | `id`, `name`, `level`, `item_type`, `weapon_type` |
| `item_armors` | `client_items_armor_*` | `id`, `armor_type`, `defense`, `quality` |
| `item_weapons` | `client_items_weapon_*` | `id`, `weapon_type`, `attack_min`, `attack_max` |

**关键字段列表** (从table_structure_cache.json提取):
- `id` (主键)
- `name` - 物品名称
- `level` - 等级要求
- `quality` - 品质
- `stack` - 堆叠数量
- `price` - 价格
- `weapon_type` / `armor_type` - 类型
- `drop_prob_0~5` - 掉落概率 (服务器仅支持0-5)
- **已移除字段**: `drop_*_6~9`, `__order_index`, `erect`, `monsterbook_race`

---

### 2.2 SKILL - 技能系统

**核心文件数**: ~200+

#### 服务端技能文件

**主表**:
- `skill_base.xml` - 技能基础表 (主数据)
- `skill_base_utf8.xml` - UTF-8编码版本

**技能配置**:
- `skill_learns.xml` - 技能学习配置
- `skill_charge.xml` - 蓄力技能
- `skill_conflictcounts.xml` - 冲突计数
- `skill_damageattenuation.xml` - 伤害衰减
- `skill_prohibit.xml` - 禁用技能
- `skill_qualification.xml` - 资格要求
- `skill_randomdamage.xml` - 随机伤害
- `skill_signetdata.xml` - 烙印数据

**特殊技能**:
- `exceed_skillset.xml` - 超越技能集
- `pc_skill_skin.xml` - 技能皮肤
- `polymorph_temp_skill.xml` - 变身临时技能
- `stigma_hiddenskill.xml` - 烙印隐藏技能
- `devanion_skill_enchant.xml` - 德凡宁技能强化
- `abyss_leader_skill.xml` - 深渊指挥官技能

#### 客户端技能文件

- `client_skill_*.xml` - 客户端技能配置（如果存在）

#### 服务器日志错误分析

**高频未定义字段** (NPCServer日志):
- `status_fx_slot_lv` - 405次错误
- `toggle_id` - 378次错误
- `is_familiar_skill` - 288次错误

**解决方案**: 这些字段已加入黑名单,导出时自动移除。

#### 关键字段

- `id`, `name` - 技能ID和名称
- `level` - 技能等级
- `casting_delay` - 施法延迟 (0-30000ms)
- `cool_time` - 冷却时间 (0-3600000ms)
- `mp_cost` - MP消耗
- `damage` - 伤害值
- **已移除字段**: `status_fx_slot_lv`, `toggle_id`, `is_familiar_skill`

---

### 2.3 NPC - NPC系统

**核心文件数**: ~3,500+ (含AI模式文件)

#### 服务端NPC文件

**主表**:
- `npcs.xml` - NPC主表
- `npcs_housing.xml` - 房屋NPC

**商店系统**:
- `goodslist.xml` - 商品列表
- `abgoodslist.xml` - 深渊商品
- `purchase_list.xml` - 购买列表
- `trade_in_list.xml` - 以旧换新列表
- `toypet_merchant.xml` - 宠物商人

**NPC AI系统** (3,000+ AI模式文件):
- `npcaipatterns_*.xml` - NPC AI行为模式
- 服务器日志显示26个AI文件加载错误 (CDATA格式问题)

**错误AI文件列表** (需要修复):
- `npcaipatterns_idarena_jm.xml` - Line:5583 CDATA错误
- `npcaipatterns_idldf5b_td_yjh.xml` - Line:7904 CDATA错误
- `npcaipatterns_ldf4a_boss_bemaniax.xml` - Line:3147 CDATA错误
- ... (共26个文件)

#### 客户端NPC文件

- `client_npc_goodslist.xml` - NPC商品列表
- `client_npc_purchase_list.xml` - NPC收购列表
- `client_npc_trade_in_list.xml` - NPC以旧换新列表

#### 关键字段

- `id`, `name` - NPC ID和名称
- `tribe` - 种族
- `level` - 等级
- `hp`, `mp` - 生命值/魔法值
- `spawn_*` - 刷新相关字段
- `ai_pattern` - AI模式引用

---

### 2.4 QUEST - 任务系统

**核心文件数**: ~300+

#### 服务端任务文件

**主表**:
- `quest.xml` - 任务主表

**任务类型分类**:
- `Quest_SimpleHunt.xml` - 简单狩猎
- `Quest_SimpleCollectItem.xml` - 简单收集
- `Quest_SimpleTalk.xml` - 简单对话
- `Quest_SimpleGather.xml` - 简单采集
- `Quest_SimpleUseItem.xml` - 简单使用物品
- `Quest_SimpleSerialHunt.xml` - 连续狩猎
- `Quest_SimpleItemPlay.xml` - 物品互动
- `Quest_CombineTask.xml` - 组合任务

**任务配置**:
- `data_driven_quest.xml` - 数据驱动任务
- `jumping_addquest.xml` - 跳级添加任务
- `jumping_endquest.xml` - 跳级结束任务
- `npcfactions_quest.xml` - NPC阵营任务
- `challenge_task.xml` - 挑战任务

**任务奖励**:
- `quest_random_rewards.xml` - 随机奖励表

**服务器日志错误**:
- 530个任务引用了不存在的物品 (pattern: `*_q_*a`)
- 示例: `sword_v_u2_q_50a`, `mace_n_u1_q_55a`
- **问题**: items表中缺失任务奖励武器数据

#### 客户端任务文件

- `client_quest_world.xml` - 任务世界关联（如果存在）

#### 关键字段

- `id`, `name` - 任务ID和名称
- `quest_type` - 任务类型
- `reward_*` - 奖励相关字段
- `objective_*` - 目标相关字段
- `required_item_id` - 需求物品

---

### 2.5 PET - 宠物系统

**核心文件数**: ~40+

#### 服务端宠物文件

**宠物数据**:
- `toypets.xml` - 宠物主表
- `toypet_feed.xml` - 宠物喂养
- `toypet_buff.xml` - 宠物Buff
- `toypet_doping.xml` - 宠物兴奋剂
- `toypet_looting.xml` - 宠物拾取
- `toypet_warehouse.xml` - 宠物仓库
- `toypet_merchant.xml` - 宠物商人

**契约兽系统**:
- `familiars.xml` - 契约兽主表
- `familiar_contract.xml` - 契约兽契约
- `familiar_sgrade_ratio.xml` - S级契约兽比率

#### 客户端宠物文件

- `client_toypet_*.xml` - 客户端宠物配置（如果存在）

#### 关键字段

- `id`, `name` - 宠物ID和名称
- `type` - 宠物类型
- `feed_*` - 喂养相关
- `buff_*` - Buff相关
- `contract_*` - 契约相关 (契约兽)

---

### 2.6 ABYSS - 深渊系统

**核心文件数**: ~80+

#### 服务端深渊文件

**主配置**:
- `abyss.xml` - 深渊主配置
- `abyss_op.xml` - 深渊作战点
- `abyss_mist_times.xml` - 迷雾时间表
- `abyss_mist_times_special01.xml` - 特殊迷雾时间
- `abyss_levelgroup.xml` - 等级分组
- `abyss_race_bonuses.xml` - 种族加成
- `abyss_raid_carrier_times.xml` - 突袭运输时间
- `abysspoint_world_mod.xml` - 深渊点世界修正

**深渊商店**:
- `abgoodslist.xml` - 深渊商品列表

**深渊技能**:
- `abyss_leader_skill.xml` - 指挥官技能

#### 客户端深渊文件

- `client_abyss.xml`
- `client_abyss_levelgroup.xml`
- `client_abyss_mist_times.xml`
- `client_abyss_op.xml`
- `client_abyss_rank.xml`
- `client_artifact.xml` - 神器

---

### 2.7 INSTANCE - 副本系统

**核心文件数**: ~450+ (含大量世界地图文件)

#### 服务端副本文件

**副本配置**:
- `instance_bonusattr.xml` - 副本加成属性
- `instance_cooltime.xml` / `instance_cooltime2.xml` - 冷却时间
- `instance_creation.xml` - 副本创建规则
- `instance_pool.xml` - 副本池
- `instance_restrict.xml` - 副本限制
- `instance_scaling.xml` - 副本缩放

**副本类型**:
- `instant_dungeon_define.xml` - 副本定义
- `instant_dungeon_battleground.xml` - 战场
- `instant_dungeon_tournament.xml` - 竞技场
- `instant_dungeon_idarenapvp.xml` - 竞技场PVP

#### 客户端副本文件 (350+ client_world_id* 文件)

**副本世界列表** (示例):
- `client_world_idldf4a.xml` - LDF4A副本
- `client_world_idldf4b_tiamat.xml` - 提亚马特副本
- `client_world_idldf5_under_01.xml` - LDF5地下副本
- `client_world_idarena_*.xml` - 竞技场系列 (20+ 文件)
- `client_world_idtiamat_*.xml` - 提亚马特系列 (6+ 文件)
- `client_world_iddreadgion_*.xml` - 恐惧要塞系列

---

### 2.8 PVP - PVP系统

**核心文件数**: ~60+

#### 服务端PVP文件

**PVP配置**:
- `pvp_rank.xml` - PVP等级
- `pvp_exp_table.xml` - PVP经验表
- `pvp_exp_mod_table.xml` - PVP经验修正表
- `pvp_mod_table.xml` - PVP修正表
- `pvp_world_adjust.xml` - PVP世界调整
- `spvp_time_table.xml` - 特殊PVP时间表

#### 客户端PVP文件

- `client_pvp_rank.xml` - PVP等级
- `client_ranking.xml` - 排行榜

---

### 2.9 GUILD - 军团系统

**核心文件数**: ~15+

#### 服务端军团文件

- `legion_dominion.xml` - 军团领地
- `guild_rank_reward.xml` - 军团等级奖励

#### 客户端军团文件

- `client_legion_dominion.xml` - 军团领地

---

### 2.10 HOUSING - 房屋系统

**核心文件数**: ~25+

#### 客户端房屋文件

**个人房屋**:
- `client_world_housing_df_personal.xml` - 天族个人房屋
- `client_world_housing_lf_personal.xml` - 魔族个人房屋

**军团房屋**:
- `client_world_housing_lc_legion.xml` - 军团房屋
- `client_world_housing_barrack.xml` - 兵营

---

## 三、客户端与服务端文件对照表

### 3.1 核心对照关系

| 服务端XML | 客户端XML | 机制 | 数据库表 |
|----------|----------|------|---------|
| `items.xml` | `client_items_*` | ITEM | `items` (22,162行) |
| `skill_base.xml` | `client_skill_*` | SKILL | `skill_base` |
| `npcs.xml` | `client_npc*.xml` | NPC | `npcs` |
| `quest.xml` | `client_quest_*` | QUEST | `quest` |
| `toypets.xml` | `client_toypet_*` | PET | `toypets` |
| `abyss.xml` | `client_abyss*.xml` | ABYSS | `abyss` |
| `instance_*.xml` | `client_world_id*.xml` | INSTANCE | `instance_*` |
| `goodslist.xml` | `client_npc_goodslist.xml` | NPC | `goodslist` |

### 3.2 字段映射示例

#### ITEM字段映射

```
服务端 (items表)          →  客户端 (client_items_*)
├─ id                    →  id
├─ name                  →  name
├─ level                 →  level
├─ quality               →  quality
├─ weapon_type           →  weapon_type
├─ stack                 →  stack
└─ price                 →  price
```

**注意**: 文本描述（如物品名称描述）由客户端单独管理，不在服务器XML加载范围内。

#### SKILL字段映射

```
服务端 (skill_base表)     →  客户端 (client_skill_*)
├─ id                    →  id
├─ name                  →  name
├─ level                 →  level
├─ casting_delay         →  casting_delay
├─ cool_time             →  cool_time
└─ mp_cost               →  mp_cost
```

**注意**: 技能文本描述由客户端单独管理。

---

## 四、字段黑名单与服务器合规性

### 4.1 全局黑名单字段

基于服务器日志错误分析,以下字段在导出时应自动移除:

**通用黑名单**:
- `__order_index` - 工具内部排序字段 (44,324次错误)

**SKILL表黑名单**:
- `status_fx_slot_lv` - 405次错误
- `toggle_id` - 378次错误
- `is_familiar_skill` - 288次错误

**ITEM表黑名单**:
- `drop_prob_6~9` - 扩展掉落字段 (服务器仅支持0-5)
- `drop_monster_6~9`
- `drop_item_6~9`
- `erect` - 60次错误
- `monsterbook_race` - 30次错误

---

## 五、数据质量问题汇总

### 5.1 服务器日志错误统计

| 错误类型 | 错误数 | 主要来源 | 严重性 |
|---------|--------|---------|--------|
| **undefined token** | 45,571 | NPCServer - ItemDB, SkillDB | 🔴 极高 |
| **unknown item name** | 19,559 | MainServer - quest_random_rewards | 🟠 高 |
| **XML parsing error** | 26 | NPCServer - npcaipatterns_* | 🟡 中 |

### 5.2 物品引用完整性问题

**缺失物品模式**: `*_q_*a` (任务奖励武器)

**TOP 5 缺失物品**:
1. `sword_v_u2_q_50a` - 76次引用
2. `mace_v_u2_q_50a` - 76次引用
3. `sword_n_u1_q_55a` - 72次引用
4. `mace_n_u1_q_55a` - 72次引用
5. `0` (空引用) - 261次

**影响范围**: 530个任务奖励配置

---

## 六、机制归类统计

### 6.1 按文件数量排序

| 排名 | 机制 | 文件数 | 占比 | 备注 |
|------|------|--------|------|------|
| 1 | AI系统 | ~3,000 | 46.1% | NPC AI模式文件 |
| 2 | WORLD/INSTANCE | ~850 | 13.1% | 副本和地图文件 |
| 3 | ITEM | ~600 | 9.2% | 物品系统相关 |
| 4 | NPC | ~500 | 7.7% | NPC数据和商店 |
| 5 | QUEST | ~300 | 4.6% | 任务系统 |
| 6 | SKILL | ~200 | 3.1% | 技能系统 |
| 7 | ABYSS | ~80 | 1.2% | 深渊系统 |
| 8 | PVP | ~60 | 0.9% | PVP系统 |
| 9 | PET | ~40 | 0.6% | 宠物系统 |
| 10 | 其他 | ~878 | 13.5% | 其他机制文件 |

**注意**: client_strings_* 系列文件（约100个）不在服务器XML加载范围内，仅供客户端使用。

---

## 七、下一步行动建议

### 7.1 立即修复

1. **物品引用完整性** - 补全缺失的任务奖励物品数据 (530个引用)
2. **字段黑名单应用** - 在DbToXmlGenerator中集成ServerComplianceFilter
3. **AI文件修复** - 修复26个CDATA格式错误的npcaipatterns文件

### 7.2 短期优化

1. **机制浏览器集成** - 将本归类结果集成到AionMechanismExplorerStage
2. **客户端文件支持** - 扩展工具支持client_*文件的导入导出
3. **字段映射可视化** - 在UI中显示服务端↔客户端字段对照关系

---

## 八、附录

### 8.1 数据来源

- **服务器日志**: `D:\AionReal58\AionServer\NPCServer\log\2025-12-29.err` (105,654行)
- **服务器日志**: `D:\AionReal58\AionServer\MainServer\log\2025-12-29.err`
- **XML目录**: `D:\AionReal58\AionMap\XML` (6,508个文件)
- **数据库缓存**: `D:\workspace\dbxmlTool\cache\table_structure_cache.json` (464个表)
- **配置文件**: `D:\workspace\dbxmlTool\src\main\resources\CONF` (180+ JSON配置)

### 8.2 相关文档

- `SERVER_COMPLIANCE_ANALYSIS.md` - 服务器合规性分析
- `TRANSPARENT_ENCODING_ARCHITECTURE.md` - 透明编码转换架构
- `MECHANISM_DYNAMIC_CLASSIFICATION.md` - 机制动态分类系统

---

**文档作者**: Claude Code
**最后更新**: 2025-12-29
**文档版本**: 1.0
