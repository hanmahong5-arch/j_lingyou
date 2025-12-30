package red.jiuzhou.ui;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import red.jiuzhou.dbxml.DirectoryManagerDialog;
import red.jiuzhou.dbxml.TabConfLoad;
import red.jiuzhou.relationship.XmlRelationshipAnalyzer;
import red.jiuzhou.ui.features.FeatureCategory;
import red.jiuzhou.ui.features.FeatureDescriptor;
import red.jiuzhou.ui.features.FeatureLauncher;
import red.jiuzhou.ui.features.FeatureRegistry;
import red.jiuzhou.ui.features.FeatureTaskExecutor;
import red.jiuzhou.ui.features.StageFeatureLauncher;
import red.jiuzhou.analysis.aion.IdNameResolver;
import red.jiuzhou.util.AIAssistant;
import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.util.IncrementalMenuJsonGenerator;
import red.jiuzhou.util.YamlUtils;
import red.jiuzhou.util.YmlConfigUtil;
import red.jiuzhou.ui.components.EnhancedStatusBar;
import red.jiuzhou.ui.components.HotkeyManager;
import red.jiuzhou.ui.components.SearchableTreeView;
import red.jiuzhou.agent.ui.AgentChatStage;
import red.jiuzhou.pattern.rule.ui.DesignRuleStage;
import red.jiuzhou.ui.GameToolsStage;
import red.jiuzhou.ui.ServerKnowledgeStage;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;


/**
 * @className: red.jiuzhou.ui.Dbxmltool.java
 * @description: 主程序
 * @author: yanxq
 * @date:  2025-04-15 20:42
 * @version V1.0
 */
@SpringBootApplication(scanBasePackages = {
    "red.jiuzhou.api",
    "red.jiuzhou.util",
    "red.jiuzhou.agent",
    "red.jiuzhou.ai",
    "red.jiuzhou.analysis",
    "red.jiuzhou.config"
})
public class Dbxmltool extends Application {
    private ConfigurableApplicationContext springContext;


    private static final Logger log = LoggerFactory.getLogger(Dbxmltool.class);
    private final FeatureRegistry featureRegistry = FeatureRegistry.defaultRegistry();

    // 增强组件
    private EnhancedStatusBar statusBar;
    private HotkeyManager hotkeyManager;
    @Override
    public void init() {
        // 初始化 Spring 上下文
        springContext = new SpringApplicationBuilder(Dbxmltool.class).run();
    }

    @Override
    public void stop() {
        if (springContext != null) {
            springContext.close();
        }
        FeatureTaskExecutor.shutdown();
        // 清理状态栏资源
        if (statusBar != null) {
            statusBar.dispose();
        }
    }
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * JavaFX应用程序启动入口
     * 初始化主窗口,创建用户界面,配置菜单和工具栏
     *
     * 主要功能模块:
     * 1. 初始化数据库连接和AI助手
     * 2. 生成左侧目录菜单结构
     * 3. 创建顶部工具栏(包含各类功能按钮)
     * 4. 创建左右分割面板(左侧菜单树 + 右侧内容区)
     * 5. 配置Tab页切换监听器
     *
     * @param primaryStage JavaFX主窗口
     */
    @Override
    public void start(Stage primaryStage) {
        log.info("应用程序启动,当前数据库: {}", DatabaseUtil.getDbName());

        // ========== 性能优化：异步加载菜单配置（避免启动卡顿）==========
        // 当前选中的Tab名称
        AtomicReference<String> tabName = new AtomicReference<>("");

        // 创建主布局容器
        VBox root = new VBox();
        MenuTabPaneExample example = new MenuTabPaneExample();

        // 初始化AI助手 - 支持智能数据处理和转换
        try {
            AIAssistant aiAssistant = springContext.getBean(AIAssistant.class);
            example.setAiAssistant(aiAssistant);

            // 初始化AI主题转换服务
            red.jiuzhou.theme.AITransformService.initialize(aiAssistant);

            log.info("AI助手初始化成功");
        } catch (Exception e) {
            log.warn("AI助手初始化失败: {}", e.getMessage());
        }

        // 配置设计洞察功能网关 - 支持文件分析和数据可视化
        example.setFeatureGateway(new MenuTabPaneExample.FeatureGateway() {
            @Override
            public boolean supportsDesignerInsight() {
                // 启用设计洞察功能
                return true;
            }

            @Override
            public void openDesignerInsight(Path path) {
                // 打开设计洞察窗口分析指定文件
                if (path == null) {
                    return;
                }
                DesignerInsightStage stage = ensureDesignerInsightStage(primaryStage);
                if (stage != null) {
                    stage.inspectFile(path);
                } else {
                    log.warn("设计洞察窗口尚未就绪，无法打开文件: {}", path);
                }
            }
        });

        // 创建顶部Tab页容器
        TabPane tabPane = example.createTopPane();
        VBox rightControl = new PaginatedTable().createVbox(tabPane, new Tab(""));

        // 添加顶部工具栏(包含所有功能按钮)
        ToolBar toolBar = createToolBar(primaryStage, rightControl);
        root.getChildren().add(toolBar);

        // 创建左右分割面板
        SplitPane splitPane = new SplitPane();
        VBox leftControl = new VBox(new Label("菜单"));

        leftControl.setSpacing(8);
        leftControl.setPadding(new Insets(8));

        // ==================== 生成最新的左侧菜单配置 ====================
        // 启动时重新扫描目录生成菜单配置，确保显示最新的目录结构
        log.info("正在生成左侧菜单配置...");
        IncrementalMenuJsonGenerator.createJsonIncrementally();
        log.info("左侧菜单配置生成完成");

        // 读取左侧菜单配置并创建可搜索菜单树（增强版）
        String leftMenuJson = FileUtil.readUtf8String(YamlUtils.getProperty("file.homePath") + File.separator + "leftMenu.json");
        SearchableTreeView<String> searchableMenu = example.createSearchableLeftMenu(leftMenuJson, tabPane);
        TreeView<String> leftMenu = searchableMenu.getTreeView();  // 获取内部TreeView用于兼容

        // ==================== 异步启用机制过滤功能（性能优化）====================
        // 在后台线程扫描XML目录建立机制映射，避免阻塞UI启动
        String xmlPath = YamlUtils.getProperty("aion.xmlPath");
        if (xmlPath != null && !xmlPath.isEmpty()) {
            final String finalXmlPath = xmlPath;
            CompletableFuture.runAsync(() -> {
                log.info("开始异步扫描目录建立机制映射: {}", finalXmlPath);
                searchableMenu.scanDirectoryForMechanisms(finalXmlPath);
                log.info("目录扫描完成");
            }).thenRun(() -> {
                // 在JavaFX线程中启用机制过滤标签栏
                Platform.runLater(() -> {
                    searchableMenu.enableMechanismFilter(true);
                    log.info("机制过滤功能已启用");
                });
            }).exceptionally(ex -> {
                log.error("机制扫描失败: {}", ex.getMessage(), ex);
                Platform.runLater(() -> {
                    // 即使扫描失败也启用机制过滤，让用户可以正常使用其他功能
                    searchableMenu.enableMechanismFilter(true);
                });
                return null;
            });
        } else {
            // 如果没有配置XML路径，直接启用机制过滤
            searchableMenu.enableMechanismFilter(true);
        }

        // 组装左侧面板
        leftControl.getChildren().add(searchableMenu);  // 使用可搜索菜单树
        // 让菜单树占满可用空间
        VBox.setVgrow(searchableMenu, Priority.ALWAYS);

        // ==================== 组装主界面 ====================
        // 添加左右分割面板
        splitPane.getItems().addAll(leftControl, rightControl);
        // 设置分割比例: 左侧30% / 右侧70%
        splitPane.setDividerPositions(0.3);
        // 让分割面板占满剩余空间
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        root.getChildren().add(splitPane);

        // ==================== Tab页切换监听器 ====================
        // 当用户切换Tab时,自动刷新右侧内容区域
        tabPane.getSelectionModel().selectedItemProperty().addListener((observable1, oldTab, newTab) -> {
            if (newTab != null) {
                long startTime = System.currentTimeMillis();
                // 获取选中的 Tab 的名称
                String selectedTabName = newTab.getText();
                tabName.set(selectedTabName);
                log.info("切换到Tab: {} ", tabName);

                // 刷新右侧面板内容
                refreshRightControl(tabPane, newTab, rightControl);
                log.info("Tab切换耗时: {} ms", System.currentTimeMillis() - startTime);
            }
        });

        // ==================== 添加增强状态栏 ====================
        statusBar = new EnhancedStatusBar();
        statusBar.setConnectionStatus(true, DatabaseUtil.getDbName());
        statusBar.info("应用程序已启动");
        root.getChildren().add(statusBar);

        // ==================== 创建主场景并显示窗口 ====================
        Scene scene = new Scene(root, 1400, 720);
        primaryStage.setScene(scene);
        primaryStage.setTitle("DB_XML_TOOL - 数据库与XML转换工具 v2.0");

        // ==================== 初始化快捷键系统 ====================
        initializeHotkeys(primaryStage, scene);

        primaryStage.show();
        log.info("应用程序界面初始化完成");
    }

    /**
     * 初始化快捷键系统
     */
    private void initializeHotkeys(Stage primaryStage, Scene scene) {
        hotkeyManager = HotkeyManager.getInstance();

        // 注册默认快捷键
        hotkeyManager.registerDefaults(new HotkeyManager.DefaultHotkeyHandler() {
            @Override
            public void onSearch() {
                statusBar.info("搜索功能 (Ctrl+F)");
            }

            @Override
            public void onRefresh() {
                statusBar.info("刷新中...");
                IncrementalMenuJsonGenerator.createJsonIncrementally();
                statusBar.success("刷新完成");
            }

            @Override
            public void onMechanismExplorer() {
                try {
                    AionMechanismExplorerStage stage = new AionMechanismExplorerStage();
                    stage.initOwner(primaryStage);
                    stage.show();
                    statusBar.info("已打开机制浏览器");
                } catch (Exception e) {
                    log.error("打开机制浏览器失败", e);
                    statusBar.error("打开机制浏览器失败");
                }
            }

            @Override
            public void onDesignerInsight() {
                try {
                    DesignerInsightStage stage = new DesignerInsightStage();
                    stage.initOwner(primaryStage);
                    stage.show();
                    statusBar.info("已打开设计洞察");
                } catch (Exception e) {
                    log.error("打开设计洞察失败", e);
                    statusBar.error("打开设计洞察失败");
                }
            }

            @Override
            public void onDataOperation() {
                try {
                    IdNameResolver.getInstance().preloadAllSystems();
                    DataOperationCenterStage stage = new DataOperationCenterStage(primaryStage);
                    stage.show();
                    statusBar.info("已打开数据操作中心");
                } catch (Exception e) {
                    log.error("打开数据操作中心失败", e);
                    statusBar.error("打开数据操作中心失败");
                }
            }

            @Override
            public void onShowHotkeys() {
                showHotkeyHelp();
            }

            @Override
            public void onHelp() {
                showHotkeyHelp();
            }
        });

        // 绑定快捷键到场景
        hotkeyManager.bindToScene(scene);
        log.info("快捷键系统初始化完成");
    }

    /**
     * 显示快捷键帮助
     */
    private void showHotkeyHelp() {
        String helpText = hotkeyManager.getHelpText();

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("快捷键帮助");
        alert.setHeaderText("可用的快捷键列表");

        TextArea textArea = new TextArea(helpText);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setStyle("-fx-font-family: 'Consolas', 'Microsoft YaHei'; -fx-font-size: 12px;");
        textArea.setPrefRowCount(20);
        textArea.setPrefColumnCount(50);

        alert.getDialogPane().setContent(textArea);
        alert.showAndWait();
    }

    /**
     * 刷新右侧内容面板
     * 当用户切换Tab页时,根据新Tab的内容重新加载和渲染右侧数据区域
     *
     * 刷新流程:
     * 1. 清空当前右侧面板的所有内容
     * 2. 根据新Tab获取对应的数据源
     * 3. 创建分页表格组件展示数据
     * 4. 设置表格占满可用空间
     *
     * @param tabPane Tab页容器
     * @param newTab 新选中的Tab页
     * @param rightControl 右侧内容面板
     */
    private void refreshRightControl(TabPane tabPane, Tab newTab, VBox rightControl) {
        // 清除右侧面板当前的所有内容
        rightControl.getChildren().clear();

        // 创建新的分页表格组件
        // 根据选中的Tab加载对应的数据并展示
        PaginatedTable paginatedTable = new PaginatedTable();

        // 让表格组件占满所有可用空间
        VBox.setVgrow(rightControl, Priority.ALWAYS);

        // 将新的分页表格添加到右侧面板
        rightControl.getChildren().add(paginatedTable.createVbox(tabPane, newTab));
    }


    /**
     * 创建主工具栏
     * 包含数据管理、查询工具、数据处理和安全管理四大功能模块
     * 所有按钮均配备图标和详细的工具提示，帮助用户快速理解功能
     *
     * @param primaryStage 主窗口
     * @param rightControl 右侧控制面板
     * @return 配置完成的工具栏
     */
    private ToolBar createToolBar(Stage primaryStage, VBox rightControl) {
        ToolBar toolBar = new ToolBar();

        // ==================== 数据管理模块 ====================
        // 提供基础的数据配置和目录管理功能

        // 映射关系配置按钮 - 打开数据库驱动的映射管理器
        Button confButton = new Button("🔗 数据对照");
        confButton.setTooltip(new Tooltip(
            "客户端服务端数据结构对照工具\n\n" +
            "🎯 核心功能:\n" +
            "• 自动识别client_*与server_*表的对应关系\n" +
            "• 字段级差异对比(类型/长度/注释/默认值)\n" +
            "• 双向数据同步(支持选择性字段同步)\n" +
            "• 内置枚举值查询统计功能\n\n" +
            "💡 适用场景:\n" +
            "→ 检查客户端与服务端表结构一致性\n" +
            "→ 快速定位配置差异导致的问题\n" +
            "→ 统一更新两端数据结构"
        ));

        // 字段关联分析按钮 - 综合关联分析工具（整合了关系图功能）
        Button relationButton = new Button("🔍 关联分析");
        relationButton.setTooltip(new Tooltip(
            "智能分析配置文件的关联关系\n\n" +
            "🎯 三种分析模式:\n\n" +
            "1️⃣ Name字段关联:\n" +
            "• 分析name字段的跨表值匹配\n" +
            "• 发现道具名、技能名、NPC名等关联\n\n" +
            "2️⃣ ID引用分析:\n" +
            "• 检测*_id字段的引用关系\n" +
            "• 验证引用是否有效(目标ID存在)\n" +
            "• 持久化结果，下次无需重新分析\n\n" +
            "3️⃣ 机制关系图:\n" +
            "• 27个游戏机制的依赖网络\n" +
            "• 力导向布局自动排列\n" +
            "• 依赖链追踪和影响分析\n\n" +
            "💡 点击后进入关联分析窗口，可在多个Tab间切换"
        ));

        // 目录管理按钮 - 管理数据文件存储目录
        Button addDirectoryBtn = new Button("📁 路径配置");
        addDirectoryBtn.setTooltip(new Tooltip(
            "配置数据文件存储路径\n\n" +
            "🎯 核心功能:\n" +
            "• 管理XML配置文件存放位置\n" +
            "• 设置导入导出根目录\n" +
            "• 配置多项目工作区\n\n" +
            "💡 适用场景:\n" +
            "→ 切换不同游戏版本的配置路径\n" +
            "→ 组织多个项目的数据文件\n" +
            "→ 配置团队共享的数据目录"
        ));


        // ==================== 数据处理模块 ====================
        // 提供高级数据处理和批量操作功能

        // 搜索替换按钮 - 全局搜索和批量替换
        Button searchReplaceBtn = new Button("🔎 查找替换");
        searchReplaceBtn.setTooltip(new Tooltip(
            "全局搜索和批量替换工具\n\n" +
            "🎯 核心功能:\n" +
            "• 跨XML文件内容搜索\n" +
            "• 支持正则表达式匹配\n" +
            "• 批量替换预览确认\n" +
            "• 操作历史可回溯\n\n" +
            "💡 适用场景:\n" +
            "→ 批量修改配置项名称\n" +
            "→ 统一更新资源路径\n" +
            "→ 查找特定数值或ID"
        ));

        // 数据验证按钮 - 数据完整性和规则验证
        Button dataValidationBtn = new Button("✅ 数据校验");
        dataValidationBtn.setTooltip(new Tooltip(
            "智能数据完整性检查\n\n" +
            "🎯 核心功能:\n" +
            "• 检查必填字段缺失\n" +
            "• 验证数据类型和范围\n" +
            "• 检测外键引用错误\n" +
            "• 识别重复数据\n" +
            "• 生成问题清单报告\n\n" +
            "💡 适用场景:\n" +
            "→ 上线前配置完整性检查\n" +
            "→ 排查引用错误导致的Bug\n" +
            "→ 数据质量日常巡检"
        ));

        // 批量改写按钮 - 批量修改数据
        Button batchRewriteBtn = new Button("✏️ 批量编辑");
        batchRewriteBtn.setTooltip(new Tooltip(
            "条件批量数据修改工具\n\n" +
            "🎯 核心功能:\n" +
            "• 基于SQL条件筛选数据\n" +
            "• 支持公式和脚本计算\n" +
            "• 修改前预览影响范围\n" +
            "• 自动备份可一键回滚\n\n" +
            "💡 适用场景:\n" +
            "→ 游戏数值批量调整\n" +
            "→ 奖励配置统一修改\n" +
            "→ 测试数据快速清理"
        ));

        // 设计规则按钮 - 意图驱动的批量修改
        Button designRuleBtn = new Button("📐 设计规则");
        designRuleBtn.setTooltip(new Tooltip(
            "意图驱动批量数据修改\n\n" +
            "🎯 核心功能:\n" +
            "• 定义规则，自动应用到所有匹配记录\n" +
            "• 支持表达式语法(当前值×1.2等)\n" +
            "• 执行前预览变更统计\n" +
            "• 一键回滚已执行的规则\n\n" +
            "💡 使用示例:\n" +
            "→ 「法师装备魔攻提升20%」\n" +
            "→ 「50级以上技能伤害+15%」\n" +
            "→ 定义规则一次，批量修改127条"
        ));
        designRuleBtn.setStyle("-fx-background-color: #E3F2FD; -fx-font-weight: bold;");

        // 配置管理按钮 - 应用配置文件管理
        Button configEditorBtn = new Button("⚙ 配置管理");
        configEditorBtn.setTooltip(new Tooltip(
            "应用配置文件管理器\n\n" +
            "🎯 核心功能:\n" +
            "• 可视化编辑YAML/JSON/ENV配置\n" +
            "• 实时格式验证和结构预览\n" +
            "• 自动备份和版本恢复\n" +
            "• 敏感信息安全遮蔽\n\n" +
            "💡 支持配置:\n" +
            "→ application.yml 主配置\n" +
            "→ 机制覆盖配置\n" +
            "→ 菜单和映射配置\n\n" +
            "⌨ 快捷键: Ctrl+S保存 | Ctrl+F搜索"
        ));

        // ==================== 分析工具模块 ====================
        // 提供游戏数据分析和可视化功能

        // 机制浏览器按钮 - Aion游戏机制三层级可视化
        Button mechanismExplorerBtn = new Button("🎮 机制浏览器");
        mechanismExplorerBtn.setTooltip(new Tooltip(
            "Aion游戏机制三层级可视化导航\n\n" +
            "🎯 核心功能:\n" +
            "• 27个游戏机制分类(深渊/技能/物品/Luna等)\n" +
            "• 三层级导航：机制→文件→字段\n" +
            "• 字段引用关系分析和跳转\n" +
            "• 公共/本地化文件对比\n\n" +
            "💡 适用场景:\n" +
            "→ 快速定位游戏配置文件\n" +
            "→ 追踪数据间的引用关系\n" +
            "→ 理解游戏系统间的关联"
        ));


        // 服务器知识浏览器按钮 - 显示从服务器日志提取的知识
        Button serverKnowledgeBtn = new Button("📚 服务器知识");
        serverKnowledgeBtn.setTooltip(new Tooltip(
            "服务器知识浏览器\n\n" +
            "🎯 核心功能:\n" +
            "• 49个XML字段黑名单（服务端拒绝）\n" +
            "• 10条字段值修正规则\n" +
            "• 双服务器交叉验证结果\n" +
            "• 102,825行日志分析精华\n\n" +
            "💡 数据来源:\n" +
            "→ MainServer: 57,244个错误\n" +
            "→ NPCServer: 45,581个错误\n" +
            "→ 无服务端源码的宝贵知识\n\n" +
            "⚠️ 导出时自动过滤黑名单字段\n" +
            "确保XML符合服务端要求"
        ));
        serverKnowledgeBtn.setStyle("-fx-background-color: #FFF9C4; -fx-font-weight: bold;");

        // 服务器配置清单按钮 - 显示服务器实际加载的XML文件列表
        Button serverConfigBtn = new Button("📋 配置清单");
        serverConfigBtn.setTooltip(new Tooltip(
            "服务器配置文件清单\n\n" +
            "🎯 核心功能:\n" +
            "• 分析服务器日志，提取实际加载的XML文件\n" +
            "• 标记核心配置文件（优先级分类）\n" +
            "• 跟踪导入导出操作统计\n" +
            "• 文件验证状态和错误信息\n\n" +
            "💡 设计理念:\n" +
            "→ 「文件层的唯一真理」\n" +
            "→ 只关注服务器真正使用的文件\n" +
            "→ 工具导入导出优先处理这些文件\n\n" +
            "🔍 使用流程:\n" +
            "1. 点击「分析服务器日志」\n" +
            "2. 选择MainServer/log目录\n" +
            "3. 自动提取XML加载记录\n" +
            "4. 筛选查看不同类别的文件"
        ));
        serverConfigBtn.setStyle("-fx-background-color: #E1F5FE; -fx-font-weight: bold;");

        // AI数据助手按钮 - 自然语言操作游戏数据
        Button aiAgentBtn = new Button("🤖 AI助手");
        aiAgentBtn.setTooltip(new Tooltip(
            "AI游戏数据助手\n\n" +
            "🎯 核心功能:\n" +
            "• 自然语言查询数据\n" +
            "• 智能SQL生成\n" +
            "• 安全审核与预览\n" +
            "• 操作历史与回滚\n\n" +
            "💡 使用示例:\n" +
            "→ \"查询所有50级以上的紫色武器\"\n" +
            "→ \"把火属性技能伤害提高10%\"\n" +
            "→ \"分析技能伤害分布\""
        ));
        aiAgentBtn.setStyle("-fx-background-color: #E8F5E9; -fx-font-weight: bold;");

        // 刷怪点工具按钮 - 坐标生成、概率模拟
        Button gameToolsBtn = new Button("🎯 刷怪工具");
        gameToolsBtn.setTooltip(new Tooltip(
            "地图刷怪浏览与规划\n\n" +
            "🗺️ 地图浏览器:\n" +
            "• 浏览World目录下所有地图\n" +
            "• 查看刷怪区域(territory)配置\n" +
            "• 搜索NPC名或区域名\n" +
            "• 右键复制坐标到生成器\n\n" +
            "📍 刷怪点生成:\n" +
            "• 巡逻路线、圆形/环形刷怪区域\n" +
            "• 结果可直接复制为XML配置\n\n" +
            "🎲 概率模拟器:\n" +
            "• 怪物刷新权重验证\n" +
            "• 掉落概率测试"
        ));
        gameToolsBtn.setStyle("-fx-background-color: #FFF3E0;");

        // 机制关系图按钮 - 27个机制间的依赖关系可视化
        Button mechanismRelationBtn = new Button("🔗 关系图");
        mechanismRelationBtn.setTooltip(new Tooltip(
            "游戏机制关系图可视化\n\n" +
            "🎯 核心功能:\n" +
            "• 27个游戏机制的依赖网络\n" +
            "• 力导向布局自动排列\n" +
            "• 依赖链追踪和影响分析\n" +
            "• 交互式节点探索\n\n" +
            "💡 适用场景:\n" +
            "→ 理解机制间的整体关系\n" +
            "→ 新增BOSS时分析涉及的机制\n" +
            "→ 评估修改的影响范围"
        ));

        // ==================== 安全管理模块 ====================
        // 提供数据安全和灾难恢复功能

        // 紧急恢复按钮 - 数据紧急恢复工具
        Button emergencyRecoveryBtn = new Button("🚨 数据恢复");
        emergencyRecoveryBtn.setTooltip(new Tooltip(
            "数据紧急恢复中心\n\n" +
            "⚠️ 高危操作,请谨慎使用\n\n" +
            "🎯 核心功能:\n" +
            "• 从自动备份快速恢复\n" +
            "• 撤销危险批量操作\n" +
            "• 回滚到历史快照\n" +
            "• 恢复误删除数据\n\n" +
            "💡 适用场景:\n" +
            "→ 误删除重要配置数据\n" +
            "→ 批量操作导致数据错乱\n" +
            "→ 需要回退到某个版本"
        ));

        // 操作监控按钮 - 实时监控数据操作
        Button operationMonitorBtn = new Button("📊 操作日志");
        operationMonitorBtn.setTooltip(new Tooltip(
            "数据操作审计和监控\n\n" +
            "🎯 核心功能:\n" +
            "• 实时查看当前执行的操作\n" +
            "• SQL执行历史和性能统计\n" +
            "• 数据变更追踪记录\n" +
            "• 多人协作操作审计\n\n" +
            "💡 适用场景:\n" +
            "→ 追溯谁修改了某条数据\n" +
            "→ 分析慢查询性能问题\n" +
            "→ 监控团队操作规范性"
        ));

        // 备份管理按钮 - 数据备份管理
        Button backupManagerBtn = new Button("💾 备份中心");
        backupManagerBtn.setTooltip(new Tooltip(
            "数据备份策略管理\n\n" +
            "🎯 核心功能:\n" +
            "• 一键创建完整备份\n" +
            "• 配置自动备份计划\n" +
            "• 浏览备份历史版本\n" +
            "• 验证备份文件完整性\n" +
            "• 选择性恢复数据\n\n" +
            "💡 适用场景:\n" +
            "→ 重大版本更新前备份\n" +
            "→ 定期自动备份配置\n" +
            "→ 测试环境数据保护"
        ));

        // ==================== 按钮事件处理 ====================
        // 配置所有按钮的点击事件和业务逻辑

        // 映射关系 - 打开数据库驱动的映射管理器
        confButton.setOnAction(e -> {
            try {
                log.info("打开数据库映射管理器 - 自动加载所有client_*表");
                red.jiuzhou.ui.mapping.DatabaseMappingManager manager =
                    new red.jiuzhou.ui.mapping.DatabaseMappingManager(primaryStage);
                manager.show();
            } catch (Exception ex) {
                log.error("打开映射管理器失败", ex);
                showError("打开映射管理器失败: " + ex.getMessage());
            }
        });

        // 目录管理 - 打开目录配置对话框
        addDirectoryBtn.setOnAction(e -> {
            log.info("打开目录管理对话框");
            DirectoryManagerDialog dialog = new DirectoryManagerDialog(this::reloadAllDirectories);
            dialog.show(primaryStage);
        });

        // 字段关联 - 运行字段关联分析
        relationButton.setOnAction(event -> runRelationshipAnalysis(primaryStage, relationButton));

        // 机制浏览器 - 打开Aion机制三层级浏览器
        mechanismExplorerBtn.setOnAction(event -> {
            try {
                log.info("打开Aion机制浏览器");
                AionMechanismExplorerStage stage = new AionMechanismExplorerStage();
                stage.initOwner(primaryStage);
                stage.show();
            } catch (Exception e) {
                log.error("打开机制浏览器失败", e);
                showError("打开机制浏览器失败: " + e.getMessage());
            }
        });


        // 服务器知识浏览器 - 打开服务器知识窗口
        serverKnowledgeBtn.setOnAction(event -> {
            try {
                log.info("打开服务器知识浏览器");
                ServerKnowledgeStage stage = new ServerKnowledgeStage();
                stage.initOwner(primaryStage);
                stage.show();
            } catch (Exception e) {
                log.error("打开服务器知识浏览器失败", e);
                showError("打开服务器知识浏览器失败: " + e.getMessage());
            }
        });

        // 服务器配置清单 - 打开配置文件管理窗口
        serverConfigBtn.setOnAction(event -> {
            try {
                log.info("打开服务器配置文件清单");
                red.jiuzhou.server.ui.ServerConfigFileManagerStage stage =
                    new red.jiuzhou.server.ui.ServerConfigFileManagerStage();
                stage.initOwner(primaryStage);
                stage.show();
            } catch (Exception e) {
                log.error("打开服务器配置清单失败", e);
                showError("打开服务器配置清单失败: " + e.getMessage());
            }
        });

        // AI数据助手 - 打开AI对话窗口
        aiAgentBtn.setOnAction(event -> {
            try {
                log.info("打开AI数据助手");
                AgentChatStage stage = new AgentChatStage();
                stage.initOwner(primaryStage);
                stage.show();
            } catch (Exception e) {
                log.error("打开AI数据助手失败", e);
                showError("打开AI数据助手失败: " + e.getMessage());
            }
        });

        // 刷怪工具 - 打开游戏工具集窗口
        gameToolsBtn.setOnAction(event -> {
            try {
                log.info("打开刷怪工具");
                GameToolsStage stage = new GameToolsStage();
                stage.initOwner(primaryStage);
                stage.show();
            } catch (Exception e) {
                log.error("打开刷怪工具失败", e);
                showError("打开刷怪工具失败: " + e.getMessage());
            }
        });

        // 机制关系图 - 打开机制关系图可视化窗口
        mechanismRelationBtn.setOnAction(event -> {
            try {
                log.info("打开机制关系图窗口");
                MechanismRelationshipStage stage = new MechanismRelationshipStage();
                stage.initOwner(primaryStage);
                stage.show();
            } catch (Exception e) {
                log.error("打开机制关系图窗口失败", e);
                showError("打开机制关系图窗口失败: " + e.getMessage());
            }
        });


        // 搜索替换 - 打开全局搜索替换工具
        searchReplaceBtn.setOnAction(event -> {
            // SearchReplaceDialog 依赖 GlobalSearchEngine（使用 Lombok，暂时禁用）
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("功能提示");
            alert.setHeaderText("搜索替换功能暂时不可用");
            alert.setContentText("该功能依赖的组件使用了 Lombok，在 Java 25 环境下暂时禁用。\n\n请使用其他搜索功能或等待后续更新。");
            alert.showAndWait();
        });

        // 数据验证 - 显示校验使用说明
        dataValidationBtn.setOnAction(event -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("数据校验");
            alert.setHeaderText("快速校验当前表");
            alert.setContentText(
                "数据校验已集成到表格视图中:\n\n" +
                "1. 在左侧菜单选择一个表\n" +
                "2. 点击表格上方的「✓ 校验」按钮\n" +
                "3. 校验结果显示在下方日志面板\n\n" +
                "检查内容:\n" +
                "• 引用完整性 - 检测无效的外键引用\n" +
                "• 空值检测 - 检查必填字段是否为空\n" +
                "• 重复数据 - 识别重复的名称\n" +
                "• 异常值 - 检测偏离正常范围的数值"
            );
            alert.showAndWait();
        });

        // 批量改写 - 暂时禁用（依赖 EnhancedBatchRewriter 使用 Lombok）
        batchRewriteBtn.setOnAction(event -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("功能提示");
            alert.setHeaderText("批量改写功能暂时不可用");
            alert.setContentText("该功能依赖的组件使用了 Lombok，在 Java 25 环境下暂时禁用。\n\n" +
                "请使用其他编辑功能或等待后续更新。");
            alert.showAndWait();
        });

        // 设计规则 - 打开设计规则工作台
        designRuleBtn.setOnAction(event -> {
            try {
                log.info("打开设计规则工作台");
                DesignRuleStage stage = new DesignRuleStage();
                stage.initOwner(primaryStage);
                stage.show();
            } catch (Exception e) {
                log.error("打开设计规则工作台失败", e);
                showError("打开设计规则工作台失败: " + e.getMessage());
            }
        });

        // 配置管理 - 打开配置文件编辑器
        configEditorBtn.setOnAction(event -> {
            try {
                log.info("打开配置文件管理器");
                ConfigEditorStage stage = new ConfigEditorStage();
                stage.initOwner(primaryStage);
                stage.show();
                statusBar.info("已打开配置管理器");
            } catch (Exception e) {
                log.error("打开配置管理器失败", e);
                showError("打开配置管理器失败: " + e.getMessage());
            }
        });

        // 紧急恢复 - 暂时禁用（依赖 DataSafetyManager 使用 Lombok）
        emergencyRecoveryBtn.setOnAction(event -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("功能提示");
            alert.setHeaderText("紧急恢复功能暂时不可用");
            alert.setContentText("该功能依赖的组件使用了 Lombok，在 Java 25 环境下暂时禁用。\n\n" +
                "请使用手动恢复或等待后续更新。");
            alert.showAndWait();
        });

        // 操作监控 - 暂时禁用（依赖 DataSafetyManager 使用 Lombok）
        operationMonitorBtn.setOnAction(event -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("功能提示");
            alert.setHeaderText("操作监控功能暂时不可用");
            alert.setContentText("该功能依赖的组件使用了 Lombok，在 Java 25 环境下暂时禁用。\n\n" +
                "请等待后续更新。");
            alert.showAndWait();
        });

        // 备份管理 - 暂时禁用（依赖 DataSafetyManager 使用 Lombok）
        backupManagerBtn.setOnAction(event -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("功能提示");
            alert.setHeaderText("备份管理功能暂时不可用");
            alert.setContentText("该功能依赖的组件使用了 Lombok，在 Java 25 环境下暂时禁用。\n\n" +
                "请使用手动备份或等待后续更新。");
            alert.showAndWait();
        });

        // ==================== 工具栏布局（优化版）====================
        // 移除了重复和禁用的按钮，优化了分组逻辑
        // 按照使用频率和功能相关性分组，提升游戏设计师的工作效率

        // 创建状态标签 - 显示当前数据库连接状态
        Label statusLabel = new Label("📡 数据库: " + DatabaseUtil.getDbName());
        statusLabel.setStyle("-fx-padding: 0 10 0 10; -fx-font-size: 11px; -fx-text-fill: #666;");
        statusLabel.setTooltip(new Tooltip("当前连接的数据库名称"));

        // 创建弹性空间,将状态信息推到右侧
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 工具栏按钮优化说明：
        // ✅ 移除重复：删除工具栏中的"数据校验"（表格视图中已有）
        // ✅ 整合功能：合并"关联分析"和"关系图"为一个按钮（多Tab窗口）
        // ✅ 清理禁用：移除5个临时禁用的按钮（查找替换、批量编辑、数据恢复、操作日志、备份中心）
        // ✅ 优化分组：按使用频率重新组织（核心功能 > 分析工具 > 设计工具 > 专业工具）

        // 优化后的工具栏布局（共8个按钮，从原来的16个精简）：
        // [核心功能] | [分析工具] | [设计工具] | [专业工具] ... [状态信息]
        toolBar.getItems().addAll(
            // ========== 核心功能模块 ==========
            // 最常用的基础数据配置功能
            confButton,          // 🔗 数据对照
            addDirectoryBtn,     // 📁 路径配置
            new Separator(),

            // ========== 分析工具模块 ==========
            // 数据关系分析和可视化
            relationButton,      // 🔍 关联分析（整合了关系图）
            mechanismExplorerBtn,// 🎮 机制浏览器
            serverKnowledgeBtn,  // 📚 服务器知识
            serverConfigBtn,     // 📋 配置清单
            aiAgentBtn,          // 🤖 AI助手
            new Separator(),

            // ========== 设计工具模块 ==========
            // 高级设计和配置功能
            designRuleBtn,       // 📐 设计规则
            configEditorBtn,     // ⚙ 配置管理
            new Separator(),

            // ========== 专业工具模块 ==========
            // 特殊领域的专业工具
            gameToolsBtn,        // 🎯 刷怪工具

            // 状态信息区域（右对齐）
            spacer, statusLabel
        );

        return toolBar;
    }

    /**
     * 构建映射配置文件的完整路径
     * 根据菜单项名称拼接出对应的JSON配置文件路径
     *
     * @param menuName 菜单项名称
     * @return 配置文件的完整路径
     */
    private String buildMenuPath(String menuName) {
        String basePath = YamlUtils.getProperty("file.homePath");
        return basePath + File.separator + menuName + ".json";
    }

    /**
     * 显示信息提示对话框
     * 用于向用户展示一般性信息或功能开发状态
     *
     * @param title 对话框标题
     * @param message 提示信息内容
     */
    public void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 确保设计洞察窗口已初始化
     * 从功能注册表中查找并初始化设计洞察窗口,用于分析和可视化设计数据
     *
     * @param owner 父窗口
     * @return 设计洞察窗口实例,如果初始化失败则返回null
     */
    private DesignerInsightStage ensureDesignerInsightStage(Stage owner) {
        // 从功能注册表中查找分析类别的设计洞察功能
        FeatureDescriptor descriptor = featureRegistry
                .byCategory(FeatureCategory.ANALYTICS)
                .stream()
                .filter(d -> d.launcher() instanceof StageFeatureLauncher)
                .findFirst()
                .orElse(null);

        if (descriptor == null) {
            log.warn("设计洞察功能未注册");
            return null;
        }

        // 启动设计洞察窗口
        FeatureLauncher launcher = descriptor.launcher();
        StageFeatureLauncher stageLauncher = (StageFeatureLauncher) launcher;
        Stage stage = stageLauncher.ensureStage(owner);
        if (stage instanceof DesignerInsightStage) {
            return (DesignerInsightStage) stage;
        }

        log.warn("设计洞察窗口类型不匹配: {}", stage != null ? stage.getClass().getName() : "null");
        return null;
    }

    private Node buildFeatureCluster(FeatureCategory category, Stage owner) {
        List<FeatureDescriptor> descriptors = featureRegistry.byCategory(category);
        if (descriptors.isEmpty()) {
            return null;
        }

        HBox container = new HBox(6);
        container.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label(category.displayName());
        label.setStyle("-fx-font-weight: bold;");
        container.getChildren().add(label);

        descriptors.stream()
                .map(descriptor -> createFeatureButton(descriptor, owner))
                .forEach(container.getChildren()::add);

        return container;
    }

    private Button createFeatureButton(FeatureDescriptor descriptor, Stage owner) {
        Button button = new Button(descriptor.displayName());
        button.setMnemonicParsing(false);
        button.getStyleClass().add("toolbar-feature-button");

        String description = descriptor.description();
        if (description != null && !description.trim().isEmpty()) {
            button.setTooltip(new Tooltip(description));
        }

        button.setOnAction(event -> descriptor.launcher().launch(owner));
        return button;
    }

    /**
     * 运行字段关联分析
     * 分析数据库中表与表之间、字段与字段之间的关联关系
     * 生成关联关系报告并可视化展示结果
     *
     * 功能特点:
     * - 自动检测外键关系
     * - 识别数据引用和依赖
     * - 支持取消长时间运行的分析
     * - 实时显示分析进度
     * - 生成详细的关系分析报告
     *
     * @param owner 父窗口
     * @param triggerButton 触发分析的按钮(用于在分析过程中禁用)
     */
    private void runRelationshipAnalysis(Stage owner, Button triggerButton) {
        // 禁用触发按钮,防止重复点击
        triggerButton.setDisable(true);

        // 创建进度指示器
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        Label messageLabel = new Label("正在分析字段关联，请稍候...");
        Label detailLabel = new Label("");
        detailLabel.setWrapText(true);
        detailLabel.setMaxWidth(320);
        Button cancelButton = new Button("取消");

        // 创建进度对话框
        VBox box = new VBox(12, indicator, messageLabel, detailLabel, cancelButton);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(18, 28, 18, 28));
        box.setMinWidth(300);

        Stage progressStage = new Stage();
        progressStage.initOwner(owner);
        progressStage.initModality(Modality.APPLICATION_MODAL);
        progressStage.setResizable(false);
        progressStage.setTitle("字段关联分析");
        progressStage.setScene(new Scene(box));

        // 取消标志,用于支持用户中断分析
        AtomicBoolean cancelFlag = new AtomicBoolean(false);

        Task<XmlRelationshipAnalyzer.RelationshipReport> task = new Task<XmlRelationshipAnalyzer.RelationshipReport>() {
            @Override
            protected XmlRelationshipAnalyzer.RelationshipReport call() {
                XmlRelationshipAnalyzer.AnalysisOptions options = XmlRelationshipAnalyzer.AnalysisOptions.create()
                    .withProgressCallback(path -> {
                        if (path != null) {
                            Platform.runLater(() -> detailLabel.setText(path.toString()));
                        }
                    })
                    .withCancellationSupplier(() -> cancelFlag.get());
                try {
                    return XmlRelationshipAnalyzer.analyzeCurrentDatabase(options);
                } catch (XmlRelationshipAnalyzer.AnalysisCancelledException ex) {
                    cancel(true);
                    throw new CancellationException("analysis_cancelled");
                }
            }
        };

        cancelButton.setOnAction(e -> {
            if (cancelFlag.compareAndSet(false, true)) {
                messageLabel.setText("正在取消，请稍候...");
                cancelButton.setDisable(true);
                task.cancel();
            }
        });

        progressStage.setOnCloseRequest(evt -> {
            if (task.isRunning()) {
                evt.consume();
                cancelButton.fire();
            }
        });

        task.setOnSucceeded(evt -> {
            progressStage.close();
            triggerButton.setDisable(false);
            XmlRelationshipAnalyzer.RelationshipReport report = task.getValue();
            if (report.getRelationships().isEmpty()) {
                Alert alert = new Alert(AlertType.INFORMATION, "未检测到明显的字段关联。", ButtonType.OK);
                alert.initOwner(owner);
                alert.showAndWait();
            } else {
                RelationshipAnalysisStage stage = new RelationshipAnalysisStage(report);
                stage.initOwner(owner);
                stage.show();
            }
        });

        task.setOnFailed(evt -> {
            progressStage.close();
            triggerButton.setDisable(false);
            Throwable ex = task.getException();
            if (ex instanceof CancellationException || (ex != null && ex.getCause() instanceof CancellationException)) {
                Alert alert = new Alert(AlertType.INFORMATION, "已取消字段关联分析。", ButtonType.OK);
                alert.initOwner(owner);
                alert.showAndWait();
            } else {
                log.error("字段关联分析失败", ex);
                Alert alert = new Alert(AlertType.ERROR,
                        "字段关联分析失败: " + (ex != null ? ex.getMessage() : "未知错误"), ButtonType.OK);
                alert.initOwner(owner);
                alert.showAndWait();
            }
        });

        task.setOnCancelled(evt -> {
            progressStage.close();
            triggerButton.setDisable(false);
            Alert alert = new Alert(AlertType.INFORMATION, "已取消字段关联分析。", ButtonType.OK);
            alert.initOwner(owner);
            alert.showAndWait();
        });

        Thread worker = new Thread(task, "xml-relationship-analyzer");
        worker.setDaemon(true);
        worker.start();

        progressStage.show();
    }

    /**
     * 重新加载所有目录配置
     * 当用户修改目录设置后,重新生成菜单配置JSON文件
     * 使目录结构的变更立即在左侧菜单中生效
     */
    private void reloadAllDirectories() {
        log.info("重新加载目录配置");
        IncrementalMenuJsonGenerator.createJsonIncrementally();
    }

    /**
     * 显示错误提示对话框
     * 用于向用户展示操作失败或异常信息
     *
     * @param message 错误信息内容
     */
    public void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("错误提示");
        alert.setHeaderText("操作失败");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void restartApplications() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "确定要重启应用吗？", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
                    File currentFile = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());

                    // 检查文件是否为最新版本
                    long lastModified = currentFile.lastModified();
                    System.out.println("Current file last modified: " + new Date(lastModified));

                    List<String> command = new ArrayList<>();
                    command.add(javaBin);

                    if (currentFile.getName().endsWith(".jar")) {
                        // JAR 运行模式
                        command.add("-jar");
                        command.add(currentFile.getPath());
                    } else {
                        // IDE 运行模式
                        String mainClass = "red.jiuzhou.ui.Dbxmltool"; // 替换为你的 Main 类路径
                        command.add("-cp");
                        command.add(System.getProperty("java.class.path"));
                        command.add(mainClass);
                    }

                    // 启动新进程
                    Process process = new ProcessBuilder(command).inheritIO().start();

                    // 等待子进程启动完成
                    if (process.isAlive()) {
                        System.out.println("Application restarted successfully with latest code.");
                        System.exit(0); // 退出当前进程
                    } else {
                        throw new RuntimeException("Failed to start the new process.");
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR, "重启失败：" + e.getMessage(), ButtonType.OK);
                    errorAlert.showAndWait();
                }
            }
        });
    }

    /**
     * 递归构建映射关系菜单树
     * 根据JSON配置文件构建多层级的菜单结构,支持无限层级嵌套
     *
     * 菜单结构说明:
     * - 有子节点的项目显示为子菜单(Menu)
     * - 无子节点的项目显示为菜单项(MenuItem)
     * - 点击菜单项会打开对应的JSON配置编辑器
     *
     * @param menuItems 父菜单项列表(用于添加新的菜单项)
     * @param node 当前JSON节点(包含name和children属性)
     * @param fullPath 当前节点的完整路径(用于定位配置文件)
     */
    private void buildMenu(javafx.collections.ObservableList<MenuItem> menuItems, JSONObject node, String fullPath) {
        String name = node.getString("name");
        JSONArray children = node.getJSONArray("children");

        // 更新当前菜单的完整路径
        String currentPath = fullPath + File.separator + name;

        // 如果有子节点,创建子菜单并递归处理
        if (children != null && !children.isEmpty()) {
            Menu submenu = new Menu(name);
            for (int i = 0; i < children.size(); i++) {
                // 递归构建子菜单项
                buildMenu(submenu.getItems(), children.getJSONObject(i), currentPath);
            }
            menuItems.add(submenu);
        } else {
            // 叶子节点,创建可点击的菜单项
            MenuItem menuItem = new MenuItem(name);
            menuItem.setOnAction(event -> {
                // 打开JSON配置编辑器
                EditorStage.openJsonEditorWindow(YamlUtils.getProperty("file.homePath") + currentPath + ".json");
            });
            menuItems.add(menuItem);
        }
    }
}




