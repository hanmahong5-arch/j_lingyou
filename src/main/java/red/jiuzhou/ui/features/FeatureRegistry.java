package red.jiuzhou.ui.features;

import java.util.ArrayList;
import java.util.List;

import red.jiuzhou.agent.ui.AgentChatStage;
import red.jiuzhou.pattern.rule.ui.DesignRuleStage;
import red.jiuzhou.ui.AionMechanismExplorerStage;
import red.jiuzhou.ui.ConfigEditorStage;
import red.jiuzhou.ui.DesignerInsightStage;
import red.jiuzhou.ui.GameToolsStage;
import red.jiuzhou.ui.MechanismRelationshipStage;
import red.jiuzhou.ui.ServerKnowledgeStage;
import red.jiuzhou.ui.ThemeStudioStage;

/**
 * Central registry describing all launchable feature modules.
 */
public final class FeatureRegistry {

    private final List<FeatureDescriptor> descriptors;

    private FeatureRegistry(List<FeatureDescriptor> descriptors) {
        this.descriptors = new ArrayList<>(descriptors);
    }

    public static FeatureRegistry defaultRegistry() {
        List<FeatureDescriptor> features = new ArrayList<>();

        features.add(new FeatureDescriptor(
                "designer-insight",
                "设计洞察",
                "针对 XML 数据的策划洞察与一致性分析",
                FeatureCategory.ANALYTICS,
                new StageFeatureLauncher(DesignerInsightStage::new)
        ));

        features.add(new FeatureDescriptor(
                "aion-mechanism-explorer",
                "Aion机制浏览器",
                "专为Aion游戏设计的机制分类和本地化对比工具，支持27个游戏系统分类",
                FeatureCategory.ANALYTICS,
                new StageFeatureLauncher(AionMechanismExplorerStage::new)
        ));

        features.add(new FeatureDescriptor(
                "mechanism-relationship",
                "机制关系图",
                "27个游戏机制之间的依赖关系可视化，支持力导向布局和交互式探索",
                FeatureCategory.ANALYTICS,
                new StageFeatureLauncher(MechanismRelationshipStage::new)
        ));

        features.add(new FeatureDescriptor(
                "theme-studio",
                "主题工作室",
                "统一管理并应用多套 UI 主题与资源",
                FeatureCategory.DESIGN_SYSTEM,
                new StageFeatureLauncher(ThemeStudioStage::new)
        ));

        features.add(new FeatureDescriptor(
                "agent-chat",
                "AI数据助手",
                "通过自然语言查询和修改游戏数据，支持智能SQL生成、安全审核和操作回滚",
                FeatureCategory.ANALYTICS,
                new StageFeatureLauncher(AgentChatStage::new)
        ));

        features.add(new FeatureDescriptor(
                "game-tools",
                "刷怪工具",
                "地图刷怪浏览与规划：浏览World目录刷怪配置、生成刷怪点坐标、概率模拟验证",
                FeatureCategory.GAME_TOOLS,
                new StageFeatureLauncher(GameToolsStage::new)
        ));

        features.add(new FeatureDescriptor(
                "design-rule",
                "设计规则",
                "意图驱动的批量数据修改：定义规则自动应用到所有匹配记录，支持预览和回滚",
                FeatureCategory.DESIGN_SYSTEM,
                new StageFeatureLauncher(DesignRuleStage::new)
        ));

        features.add(new FeatureDescriptor(
                "config-editor",
                "配置管理",
                "可视化查看和编辑应用配置文件（YAML、JSON），支持自动备份和格式验证",
                FeatureCategory.DESIGN_SYSTEM,
                new StageFeatureLauncher(ConfigEditorStage::new)
        ));

        features.add(new FeatureDescriptor(
                "game-feature-wizard",
                "游戏功能向导",
                "基于模板创建副本、任务、活动等游戏功能，智能表单自动识别字段类型，从现有数据克隆生成新内容",
                FeatureCategory.DESIGN_SYSTEM,
                new StageFeatureLauncher(red.jiuzhou.ui.wizard.GameFeatureWizard::new)
        ));

        features.add(new FeatureDescriptor(
                "server-knowledge",
                "服务器知识浏览器",
                "从102,825行服务器日志中提取的宝贵知识：49个黑名单字段、10条字段值修正规则、双服务器交叉验证结果",
                FeatureCategory.ANALYTICS,
                new StageFeatureLauncher(ServerKnowledgeStage::new)
        ));

        // TODO: 以下特性暂未实现，待添加对应的Stage类
        // - quest-editor: QuestEditorStage
        // - item-editor: ItemEditorStage
        // - skill-editor: SkillEditorStage
        // - npc-editor: NpcEditorStage

        return new FeatureRegistry(features);
    }

    public List<FeatureDescriptor> all() {
        return new ArrayList<>(descriptors);
    }

    public List<FeatureDescriptor> byCategory(FeatureCategory category) {
        List<FeatureDescriptor> result = new ArrayList<>();
        for (FeatureDescriptor descriptor : descriptors) {
            if (descriptor.category() == category) {
                result.add(descriptor);
            }
        }
        return result;
    }

    public FeatureDescriptor findById(String id) {
        return descriptors.stream()
                .filter(descriptor -> descriptor.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown feature id: " + id));
    }
}
