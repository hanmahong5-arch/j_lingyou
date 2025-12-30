package red.jiuzhou.pattern.collector;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.pattern.dao.AttrDictionaryDao;
import red.jiuzhou.pattern.model.AttrDictionary;
import red.jiuzhou.util.YamlUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 属性词汇收集器
 * 从 absolute_stat_to_pc.xml 提取服务端认可的属性定义
 * 并统计这些属性在各类配置文件中的使用情况
 */
public class AttrDictionaryCollector {
    private static final Logger log = LoggerFactory.getLogger(AttrDictionaryCollector.class);

    private final AttrDictionaryDao attrDao;

    // 属性名称映射（英文 -> 中文）
    private static final Map<String, String> ATTR_NAME_MAP = new LinkedHashMap<>();

    // 属性分类映射
    private static final Map<Pattern, String> CATEGORY_PATTERNS = new LinkedHashMap<>();

    static {
        // 基础属性
        ATTR_NAME_MAP.put("str", "力量");
        ATTR_NAME_MAP.put("vit", "体质");
        ATTR_NAME_MAP.put("agi", "敏捷");
        ATTR_NAME_MAP.put("dex", "精准");
        ATTR_NAME_MAP.put("kno", "知识");
        ATTR_NAME_MAP.put("will", "意志");

        // 生命魔力
        ATTR_NAME_MAP.put("max_hp", "生命上限");
        ATTR_NAME_MAP.put("hp_regen", "生命恢复");
        ATTR_NAME_MAP.put("current_bonushp", "生命加成");
        ATTR_NAME_MAP.put("max_mp", "魔力上限");
        ATTR_NAME_MAP.put("mp_regen", "魔力恢复");
        ATTR_NAME_MAP.put("current_bonusmp", "魔力加成");
        ATTR_NAME_MAP.put("max_dp", "神圣力上限");
        ATTR_NAME_MAP.put("current_bonusdp", "神圣力加成");
        ATTR_NAME_MAP.put("max_fp", "飞行能量上限");
        ATTR_NAME_MAP.put("fp_regen_land", "地面飞行能量恢复");
        ATTR_NAME_MAP.put("fp_regen_fly", "飞行时能量恢复");
        ATTR_NAME_MAP.put("current_bonusfp", "飞行能量加成");

        // 物理战斗
        ATTR_NAME_MAP.put("physical_attack", "物理攻击");
        ATTR_NAME_MAP.put("physical_defend", "物理防御");
        ATTR_NAME_MAP.put("min_damage", "最小伤害");
        ATTR_NAME_MAP.put("max_damage", "最大伤害");
        ATTR_NAME_MAP.put("dodge", "回避");
        ATTR_NAME_MAP.put("parry", "招架");
        ATTR_NAME_MAP.put("block", "格挡");
        ATTR_NAME_MAP.put("block_damage_reduce", "格挡伤害减免");
        ATTR_NAME_MAP.put("block_reduce_max", "格挡减免上限");
        ATTR_NAME_MAP.put("critical", "物理暴击");
        ATTR_NAME_MAP.put("hit_count", "攻击次数");
        ATTR_NAME_MAP.put("hit_accuracy", "物理命中");
        ATTR_NAME_MAP.put("physical_critical_reduce_rate", "物理暴击抵抗率");
        ATTR_NAME_MAP.put("physical_critical_damage_reduce", "物理暴击伤害减免");

        // 魔法战斗
        ATTR_NAME_MAP.put("magical_attack", "魔法攻击");
        ATTR_NAME_MAP.put("magical_defend", "魔法防御");
        ATTR_NAME_MAP.put("magical_resist", "魔法抵抗");
        ATTR_NAME_MAP.put("magical_skill_boost", "魔法增幅");
        ATTR_NAME_MAP.put("magical_skill_boost_resist", "魔法增幅抵抗");
        ATTR_NAME_MAP.put("heal_skill_boost", "治疗增幅");
        ATTR_NAME_MAP.put("magical_critical", "魔法暴击");
        ATTR_NAME_MAP.put("magical_hit_accuracy", "魔法命中");
        ATTR_NAME_MAP.put("concentration", "精神力");
        ATTR_NAME_MAP.put("magical_critical_reduce_rate", "魔法暴击抵抗率");
        ATTR_NAME_MAP.put("magical_critical_damage_reduce", "魔法暴击伤害减免");

        // PVP/PVE属性
        ATTR_NAME_MAP.put("pvp_attack_ratio_physical", "PVP物理攻击");
        ATTR_NAME_MAP.put("pvp_defend_ratio_physical", "PVP物理防御");
        ATTR_NAME_MAP.put("pve_attack_ratio_physical", "PVE物理攻击");
        ATTR_NAME_MAP.put("pve_defend_ratio_physical", "PVE物理防御");
        ATTR_NAME_MAP.put("pvp_attack_ratio_magical", "PVP魔法攻击");
        ATTR_NAME_MAP.put("pvp_defend_ratio_magical", "PVP魔法防御");
        ATTR_NAME_MAP.put("pve_attack_ratio_magical", "PVE魔法攻击");
        ATTR_NAME_MAP.put("pve_defend_ratio_magical", "PVE魔法防御");
        ATTR_NAME_MAP.put("pvp_dodge", "PVP回避");
        ATTR_NAME_MAP.put("pvp_block", "PVP格挡");
        ATTR_NAME_MAP.put("pvp_parry", "PVP招架");
        ATTR_NAME_MAP.put("pvp_hit_accuracy", "PVP物理命中");
        ATTR_NAME_MAP.put("pvp_magical_resist", "PVP魔法抵抗");
        ATTR_NAME_MAP.put("pvp_magical_hit_accuracy", "PVP魔法命中");

        // 元素抗性
        ATTR_NAME_MAP.put("elemental_resist_fire", "火属性抗性");
        ATTR_NAME_MAP.put("elemental_resist_earth", "地属性抗性");
        ATTR_NAME_MAP.put("elemental_resist_water", "水属性抗性");
        ATTR_NAME_MAP.put("elemental_resist_air", "风属性抗性");
        ATTR_NAME_MAP.put("elemental_resist_light", "光属性抗性");
        ATTR_NAME_MAP.put("elemental_resist_dark", "暗属性抗性");

        // 速度属性
        ATTR_NAME_MAP.put("run_speed", "移动速度");
        ATTR_NAME_MAP.put("walk_speed", "步行速度");
        ATTR_NAME_MAP.put("fly_speed", "飞行速度");
        ATTR_NAME_MAP.put("attack_range", "攻击距离");
        ATTR_NAME_MAP.put("attack_gap", "攻击间隔");
        ATTR_NAME_MAP.put("attack_delay", "攻击延迟");
        ATTR_NAME_MAP.put("boost_casting_time", "施法速度");

        // 分类模式
        CATEGORY_PATTERNS.put(Pattern.compile("^(str|vit|agi|dex|kno|will)$"), "基础属性");
        CATEGORY_PATTERNS.put(Pattern.compile("(max_hp|max_mp|max_dp|max_fp|_regen|bonushp|bonusmp|bonusdp|bonusfp)"), "生命魔力");
        CATEGORY_PATTERNS.put(Pattern.compile("^(physical_attack|physical_defend|min_damage|max_damage|dodge|parry|block|critical|hit_accuracy|hit_count)"), "物理战斗");
        CATEGORY_PATTERNS.put(Pattern.compile("^(magical_attack|magical_defend|magical_resist|magical_skill|heal_skill|magical_critical|magical_hit|concentration)"), "魔法战斗");
        CATEGORY_PATTERNS.put(Pattern.compile("(pvp_|pve_)"), "PVP/PVE");
        CATEGORY_PATTERNS.put(Pattern.compile("elemental_resist"), "元素抗性");
        CATEGORY_PATTERNS.put(Pattern.compile("(speed|range|gap|delay|casting)"), "速度属性");
        CATEGORY_PATTERNS.put(Pattern.compile("^ar_"), "异常抗性");
        CATEGORY_PATTERNS.put(Pattern.compile("_arp$"), "异常穿透");
    }

    public AttrDictionaryCollector() {
        this.attrDao = new AttrDictionaryDao();
    }

    public AttrDictionaryCollector(AttrDictionaryDao attrDao) {
        this.attrDao = attrDao;
    }

    /**
     * 从absolute_stat_to_pc.xml收集属性定义
     */
    public CollectionResult collectFromXml() {
        CollectionResult result = new CollectionResult();

        try {
            String xmlPath = findAbsoluteStatXml();
            if (xmlPath == null) {
                result.setSuccess(false);
                result.setMessage("未找到 absolute_stat_to_pc.xml 文件");
                return result;
            }

            log.info("开始解析属性定义文件: {}", xmlPath);

            String content = FileUtil.readString(xmlPath, StandardCharsets.UTF_16);
            Document document = DocumentHelper.parseText(content);
            Element root = document.getRootElement();

            // 获取 absolute_stat_to_pc 元素
            Element statElement = root.element("absolute_stat_to_pc");
            if (statElement == null) {
                result.setSuccess(false);
                result.setMessage("XML结构不正确，缺少 absolute_stat_to_pc 元素");
                return result;
            }

            List<AttrDictionary> attrs = new ArrayList<>();
            List<Element> elements = statElement.elements();

            for (Element elem : elements) {
                String attrCode = elem.getName();
                if ("id".equals(attrCode) || "name".equals(attrCode)) {
                    continue; // 跳过非属性字段
                }

                AttrDictionary attr = new AttrDictionary(attrCode);
                attr.setAttrName(getAttrName(attrCode));
                attr.setAttrCategory(inferCategory(attrCode));
                attr.setSourceFile("absolute_stat_to_pc.xml");
                attr.setIsPercentage(isPercentageAttr(attrCode));

                attrs.add(attr);
            }

            // 批量保存到数据库
            for (AttrDictionary attr : attrs) {
                attrDao.saveOrUpdate(attr);
            }

            result.setSuccess(true);
            result.setMessage("成功收集 " + attrs.size() + " 个属性定义");
            result.setCollectedCount(attrs.size());
            result.setDetails(attrs);

            log.info("属性词典收集完成，共 {} 个属性", attrs.size());

        } catch (Exception e) {
            log.error("属性词典收集失败", e);
            result.setSuccess(false);
            result.setMessage("收集失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 查找absolute_stat_to_pc.xml文件
     */
    private String findAbsoluteStatXml() {
        // 优先使用配置的路径
        try {
            String aionXmlPath = YamlUtils.getProperty("aion.xmlPath");
            if (aionXmlPath != null) {
                File file = new File(aionXmlPath, "absolute_stat_to_pc.xml");
                if (file.exists()) {
                    return file.getAbsolutePath();
                }
            }
        } catch (Exception ignored) {}

        // 在resources中查找
        String[] possiblePaths = {
            "src/main/resources/CONF/D/AionReal58/AionMap/XML/allNodeXml/absolute_stat_to_pc.xml",
            "CONF/D/AionReal58/AionMap/XML/allNodeXml/absolute_stat_to_pc.xml"
        };

        for (String path : possiblePaths) {
            File file = new File(path);
            if (file.exists()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }

    /**
     * 获取属性中文名称
     */
    private String getAttrName(String attrCode) {
        String name = ATTR_NAME_MAP.get(attrCode);
        if (name != null) {
            return name;
        }

        // 尝试从代码推断名称
        if (attrCode.startsWith("ar_")) {
            String statusName = attrCode.substring(3);
            return getStatusName(statusName) + "抗性";
        }
        if (attrCode.endsWith("_arp")) {
            String statusName = attrCode.substring(0, attrCode.length() - 4);
            return getStatusName(statusName) + "穿透";
        }

        return attrCode; // 保持原代码
    }

    /**
     * 获取状态异常名称
     */
    private String getStatusName(String status) {
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("poison", "中毒");
        statusMap.put("bleed", "流血");
        statusMap.put("paralyze", "麻痹");
        statusMap.put("sleep", "睡眠");
        statusMap.put("blind", "致盲");
        statusMap.put("charm", "魅惑");
        statusMap.put("disease", "疾病");
        statusMap.put("silence", "沉默");
        statusMap.put("fear", "恐惧");
        statusMap.put("curse", "诅咒");
        statusMap.put("confuse", "混乱");
        statusMap.put("stun", "眩晕");
        statusMap.put("petrification", "石化");
        statusMap.put("stumble", "绊倒");
        statusMap.put("stagger", "僵直");
        statusMap.put("openaerial", "浮空");
        statusMap.put("snare", "陷阱");
        statusMap.put("slow", "减速");
        statusMap.put("spin", "旋转");
        statusMap.put("bind", "束缚");
        statusMap.put("deform", "变形");
        statusMap.put("pulled", "拉拽");
        statusMap.put("nofly", "禁飞");
        statusMap.put("root", "定身");

        return statusMap.getOrDefault(status, status);
    }

    /**
     * 推断属性分类
     */
    private String inferCategory(String attrCode) {
        for (Map.Entry<Pattern, String> entry : CATEGORY_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(attrCode).find()) {
                return entry.getValue();
            }
        }
        return "其他";
    }

    /**
     * 判断是否为百分比属性
     */
    private boolean isPercentageAttr(String attrCode) {
        return attrCode.contains("ratio") ||
               attrCode.contains("rate") ||
               attrCode.contains("reduce") ||
               attrCode.contains("boost");
    }

    /**
     * 收集结果
     */
    public static class CollectionResult {
        private boolean success;
        private String message;
        private int collectedCount;
        private List<?> details;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public int getCollectedCount() { return collectedCount; }
        public void setCollectedCount(int collectedCount) { this.collectedCount = collectedCount; }

        public List<?> getDetails() { return details; }
        public void setDetails(List<?> details) { this.details = details; }

        @Override
        public String toString() {
            return "CollectionResult{" +
                    "success=" + success +
                    ", message='" + message + '\'' +
                    ", collectedCount=" + collectedCount +
                    '}';
        }
    }
}
