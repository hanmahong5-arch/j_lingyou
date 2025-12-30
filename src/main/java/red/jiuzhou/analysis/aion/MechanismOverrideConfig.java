package red.jiuzhou.analysis.aion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏机制手动覆盖配置加载器
 *
 * 功能：
 * 1. 从 mechanism_manual_overrides.yml 加载手动分类配置
 * 2. 提供手动覆盖查询接口
 * 3. 支持配置重新加载
 * 4. 线程安全
 *
 * 使用场景：
 * - 设计师手动调整自动检测错误的文件分类
 * - 排除不属于任何游戏机制的文件
 *
 * @author Claude Sonnet 4.5
 * @date 2025-12-21
 */
public class MechanismOverrideConfig {
    private static final Logger log = LoggerFactory.getLogger(MechanismOverrideConfig.class);

    /** 配置文件路径 */
    private static final String CONFIG_FILE = "mechanism_manual_overrides.yml";

    /** 手动覆盖映射表：文件名(小写) -> 机制分类 */
    private final Map<String, AionMechanismCategory> overrideMap = new ConcurrentHashMap<>();

    /** 排除文件集合：文件名(小写) */
    private final Set<String> excludedFiles = ConcurrentHashMap.newKeySet();

    /** 单例实例 */
    private static volatile MechanismOverrideConfig instance;

    /**
     * 私有构造函数，自动加载配置
     */
    private MechanismOverrideConfig() {
        load();
    }

    /**
     * 获取单例实例
     */
    public static MechanismOverrideConfig getInstance() {
        if (instance == null) {
            synchronized (MechanismOverrideConfig.class) {
                if (instance == null) {
                    instance = new MechanismOverrideConfig();
                }
            }
        }
        return instance;
    }

    /**
     * 加载配置文件
     */
    public synchronized void load() {
        try {
            // 清空现有配置
            overrideMap.clear();
            excludedFiles.clear();

            // 加载YAML文件
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE);
            if (inputStream == null) {
                log.warn("未找到配置文件: {}, 将使用空配置", CONFIG_FILE);
                return;
            }

            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(inputStream);

            if (config == null) {
                log.warn("配置文件为空: {}", CONFIG_FILE);
                return;
            }

            // 解析 manual_overrides 部分
            parseManualOverrides(config);

            // 解析 excluded_files 部分
            parseExcludedFiles(config);

            log.info("成功加载机制覆盖配置 - 手动覆盖: {} 个文件, 排除: {} 个文件",
                    overrideMap.size(), excludedFiles.size());

        } catch (Exception e) {
            log.error("加载配置文件失败: {}, 错误: {}", CONFIG_FILE, e.getMessage(), e);
        }
    }

    /**
     * 解析 manual_overrides 配置
     */
    @SuppressWarnings("unchecked")
    private void parseManualOverrides(Map<String, Object> config) {
        Object manualOverridesObj = config.get("manual_overrides");
        if (!(manualOverridesObj instanceof Map)) {
            return;
        }

        Map<String, Object> manualOverrides = (Map<String, Object>) manualOverridesObj;

        for (Map.Entry<String, Object> entry : manualOverrides.entrySet()) {
            String categoryName = entry.getKey();
            Object filesObj = entry.getValue();

            // 转换为枚举
            AionMechanismCategory category;
            try {
                category = AionMechanismCategory.valueOf(categoryName);
            } catch (IllegalArgumentException e) {
                log.warn("未知的机制分类: {}, 跳过", categoryName);
                continue;
            }

            // 解析文件列表
            if (filesObj instanceof List) {
                List<String> files = (List<String>) filesObj;
                for (String fileName : files) {
                    if (fileName != null && !fileName.trim().isEmpty()) {
                        String fileNameLower = fileName.toLowerCase();

                        // 检查是否重复
                        if (overrideMap.containsKey(fileNameLower)) {
                            log.warn("文件 {} 在多个机制中重复配置，将使用第一个匹配项: {}",
                                    fileName, overrideMap.get(fileNameLower).getDisplayName());
                        } else {
                            overrideMap.put(fileNameLower, category);
                            log.debug("添加手动覆盖: {} -> {}", fileName, category.getDisplayName());
                        }
                    }
                }
            }
        }
    }

    /**
     * 解析 excluded_files 配置
     */
    @SuppressWarnings("unchecked")
    private void parseExcludedFiles(Map<String, Object> config) {
        Object excludedFilesObj = config.get("excluded_files");
        if (!(excludedFilesObj instanceof List)) {
            return;
        }

        List<String> excludedFilesList = (List<String>) excludedFilesObj;
        for (String fileName : excludedFilesList) {
            if (fileName != null && !fileName.trim().isEmpty()) {
                String fileNameLower = fileName.toLowerCase();
                excludedFiles.add(fileNameLower);
                log.debug("添加排除文件: {}", fileName);
            }
        }
    }

    /**
     * 检查文件是否有手动覆盖配置
     *
     * @param fileName 文件名
     * @return true 如果有手动覆盖
     */
    public boolean hasOverride(String fileName) {
        if (fileName == null) {
            return false;
        }
        return overrideMap.containsKey(fileName.toLowerCase());
    }

    /**
     * 获取文件的手动覆盖分类
     *
     * @param fileName 文件名
     * @return 机制分类，如果没有手动覆盖则返回 null
     */
    public AionMechanismCategory getOverride(String fileName) {
        if (fileName == null) {
            return null;
        }
        return overrideMap.get(fileName.toLowerCase());
    }

    /**
     * 检查文件是否被排除
     *
     * @param fileName 文件名
     * @return true 如果文件被排除
     */
    public boolean isExcluded(String fileName) {
        if (fileName == null) {
            return false;
        }
        return excludedFiles.contains(fileName.toLowerCase());
    }

    /**
     * 获取所有手动覆盖的文件数量
     */
    public int getOverrideCount() {
        return overrideMap.size();
    }

    /**
     * 获取所有排除的文件数量
     */
    public int getExcludedCount() {
        return excludedFiles.size();
    }

    /**
     * 获取指定机制的手动覆盖文件列表
     *
     * @param category 机制分类
     * @return 文件名列表
     */
    public List<String> getOverrideFiles(AionMechanismCategory category) {
        List<String> files = new ArrayList<>();
        for (Map.Entry<String, AionMechanismCategory> entry : overrideMap.entrySet()) {
            if (entry.getValue() == category) {
                files.add(entry.getKey());
            }
        }
        return files;
    }

    /**
     * 获取所有排除的文件列表
     */
    public Set<String> getExcludedFiles() {
        return new HashSet<>(excludedFiles);
    }

    /**
     * 重新加载配置
     */
    public void reload() {
        log.info("重新加载机制覆盖配置...");
        load();
    }

    /**
     * 添加手动覆盖
     *
     * @param fileName 文件名
     * @param category 机制分类
     */
    public synchronized void addOverride(String fileName, AionMechanismCategory category) {
        if (fileName == null || category == null) {
            return;
        }
        String fileNameLower = fileName.toLowerCase();
        overrideMap.put(fileNameLower, category);
        log.info("添加手动覆盖: {} -> {}", fileName, category.getDisplayName());
    }

    /**
     * 删除手动覆盖
     *
     * @param fileName 文件名
     */
    public synchronized void removeOverride(String fileName) {
        if (fileName == null) {
            return;
        }
        String fileNameLower = fileName.toLowerCase();
        AionMechanismCategory removed = overrideMap.remove(fileNameLower);
        if (removed != null) {
            log.info("删除手动覆盖: {}", fileName);
        }
    }

    /**
     * 添加排除文件
     *
     * @param fileName 文件名
     */
    public synchronized void addExcluded(String fileName) {
        if (fileName == null) {
            return;
        }
        String fileNameLower = fileName.toLowerCase();
        excludedFiles.add(fileNameLower);
        log.info("添加排除文件: {}", fileName);
    }

    /**
     * 删除排除文件
     *
     * @param fileName 文件名
     */
    public synchronized void removeExcluded(String fileName) {
        if (fileName == null) {
            return;
        }
        String fileNameLower = fileName.toLowerCase();
        boolean removed = excludedFiles.remove(fileNameLower);
        if (removed) {
            log.info("删除排除文件: {}", fileName);
        }
    }

    /**
     * 保存配置到YAML文件
     */
    public synchronized void save() {
        try {
            // 构建配置Map
            Map<String, Object> config = new LinkedHashMap<>();

            // 构建 manual_overrides 部分
            Map<String, List<String>> manualOverrides = new LinkedHashMap<>();
            for (AionMechanismCategory category : AionMechanismCategory.values()) {
                if (category == AionMechanismCategory.OTHER) {
                    continue;
                }
                List<String> files = getOverrideFiles(category);
                if (!files.isEmpty()) {
                    Collections.sort(files);
                    manualOverrides.put(category.name(), files);
                }
            }
            config.put("manual_overrides", manualOverrides);

            // 构建 excluded_files 部分
            List<String> excludedList = new ArrayList<>(excludedFiles);
            Collections.sort(excludedList);
            config.put("excluded_files", excludedList);

            // 保存到文件
            String configPath = getConfigFilePath();
            File configFile = new File(configPath);

            // 确保父目录存在
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 使用DumperOptions配置YAML格式
            org.yaml.snakeyaml.DumperOptions options = new org.yaml.snakeyaml.DumperOptions();
            options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            options.setIndent(2);

            Yaml yaml = new Yaml(options);
            try (java.io.FileWriter writer = new java.io.FileWriter(configFile)) {
                // 写入文件头注释
                writer.write("# 游戏机制手动覆盖配置\n");
                writer.write("# 此文件由UI界面自动生成，也可以手动编辑\n");
                writer.write("# 修改后重启应用即可生效\n\n");

                // 写入YAML内容
                yaml.dump(config, writer);
            }

            log.info("成功保存机制覆盖配置到: {}", configPath);

        } catch (Exception e) {
            log.error("保存配置文件失败: {}", e.getMessage(), e);
            throw new RuntimeException("保存配置文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取配置文件路径
     */
    private String getConfigFilePath() {
        // 尝试获取classes目录下的路径
        String classPath = getClass().getClassLoader().getResource("").getPath();
        return classPath + CONFIG_FILE;
    }

    /**
     * 获取所有手动覆盖的条目（用于UI显示）
     */
    public Map<String, AionMechanismCategory> getAllOverrides() {
        return new HashMap<>(overrideMap);
    }
}
