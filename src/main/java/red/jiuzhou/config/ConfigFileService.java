package red.jiuzhou.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 配置文件服务
 *
 * <p>提供配置文件的发现、加载、保存和备份功能。
 *
 * <p>核心特性：
 * <ul>
 *   <li>多格式支持 - YAML、JSON、Properties</li>
 *   <li>自动备份 - 保存前自动创建备份</li>
 *   <li>原子写入 - 先写临时文件再重命名</li>
 *   <li>敏感信息处理 - 密码和API Key遮蔽显示</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class ConfigFileService {

    private static final Logger log = LoggerFactory.getLogger(ConfigFileService.class);

    /** 备份目录名 */
    private static final String BACKUP_DIR = ".config_backups";

    /** 最大保留备份数 */
    private static final int MAX_BACKUPS = 10;

    /** 已注册的配置文件 */
    private final List<ConfigFileEntry> configFiles = new ArrayList<>();

    /** 项目根目录 */
    private final File projectRoot;

    /** 资源目录 */
    private final File resourcesDir;

    public ConfigFileService() {
        // 定位项目根目录
        String homePath = System.getProperty("user.dir");
        this.projectRoot = new File(homePath);
        this.resourcesDir = new File(projectRoot, "src/main/resources");

        // 发现并注册配置文件
        discoverConfigFiles();
    }

    /**
     * 发现并注册所有配置文件
     */
    private void discoverConfigFiles() {
        configFiles.clear();

        // 1. 核心配置 - application.yml
        registerConfig("application", "application.yml",
                new File(resourcesDir, "application.yml"),
                ConfigFileEntry.ConfigType.YAML,
                ConfigFileEntry.ConfigCategory.CORE,
                "应用主配置",
                "数据库连接、AI服务、文件路径等核心配置",
                true);

        // 2. 机制覆盖配置
        registerConfig("mechanism-override", "mechanism_manual_overrides.yml",
                new File(resourcesDir, "mechanism_manual_overrides.yml"),
                ConfigFileEntry.ConfigType.YAML,
                ConfigFileEntry.ConfigCategory.MECHANISM,
                "机制手动覆盖",
                "手动指定XML文件的机制分类",
                false);

        // 3. 机制文件映射
        File mappingFile = new File(resourcesDir, "mechanism_file_mappings.yml");
        if (mappingFile.exists()) {
            registerConfig("mechanism-mapping", "mechanism_file_mappings.yml",
                    mappingFile,
                    ConfigFileEntry.ConfigType.YAML,
                    ConfigFileEntry.ConfigCategory.MECHANISM,
                    "机制文件映射",
                    "文件夹到机制的映射规则",
                    false);
        }

        // 4. 左侧菜单配置
        registerConfig("left-menu", "LeftMenu.json",
                new File(resourcesDir, "LeftMenu.json"),
                ConfigFileEntry.ConfigType.JSON,
                ConfigFileEntry.ConfigCategory.MENU,
                "左侧菜单",
                "应用左侧导航菜单结构",
                false);

        // 5. 表映射配置
        registerConfig("tab-mapping", "tabMapping.json",
                new File(resourcesDir, "tabMapping.json"),
                ConfigFileEntry.ConfigType.JSON,
                ConfigFileEntry.ConfigCategory.DATABASE,
                "表映射配置",
                "数据库表与XML的映射关系",
                false);

        // 6. 查找项目根目录下的.env文件
        File[] envFiles = projectRoot.listFiles((dir, name) ->
                name.endsWith(".env") || name.startsWith(".env"));
        if (envFiles != null) {
            for (File envFile : envFiles) {
                registerConfig("env-" + envFile.getName(), envFile.getName(),
                        envFile,
                        ConfigFileEntry.ConfigType.ENV,
                        ConfigFileEntry.ConfigCategory.CORE,
                        "环境变量: " + envFile.getName(),
                        "环境变量配置文件",
                        true);
            }
        }

        log.info("发现 {} 个配置文件", configFiles.size());
    }

    /**
     * 注册配置文件
     */
    private void registerConfig(String id, String name, File file,
                                 ConfigFileEntry.ConfigType type,
                                 ConfigFileEntry.ConfigCategory category,
                                 String displayName, String description,
                                 boolean sensitive) {
        ConfigFileEntry entry = new ConfigFileEntry(id, name, file, type, category)
                .displayName(displayName)
                .description(description)
                .sensitive(sensitive);

        if (file.exists()) {
            entry.setLastModified(LocalDateTime.now());
        }

        configFiles.add(entry);
    }

    /**
     * 获取所有配置文件
     */
    public List<ConfigFileEntry> getAllConfigs() {
        return Collections.unmodifiableList(configFiles);
    }

    /**
     * 按分类获取配置文件
     */
    public List<ConfigFileEntry> getConfigsByCategory(ConfigFileEntry.ConfigCategory category) {
        List<ConfigFileEntry> result = new ArrayList<>();
        for (ConfigFileEntry entry : configFiles) {
            if (entry.getCategory() == category) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * 根据ID获取配置文件
     */
    public ConfigFileEntry getConfigById(String id) {
        for (ConfigFileEntry entry : configFiles) {
            if (entry.getId().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * 读取配置文件内容
     */
    public String readConfig(ConfigFileEntry entry) throws IOException {
        if (!entry.exists()) {
            throw new FileNotFoundException("配置文件不存在: " + entry.getFile().getAbsolutePath());
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(entry.getFile()), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }

        String text = content.toString();
        entry.setContent(text);
        return text;
    }

    /**
     * 保存配置文件
     *
     * @param entry   配置文件条目
     * @param content 新内容
     * @return 是否保存成功
     */
    public boolean saveConfig(ConfigFileEntry entry, String content) {
        try {
            File file = entry.getFile();

            // 1. 验证内容格式
            if (!validateContent(entry, content)) {
                throw new IllegalArgumentException("配置内容格式无效");
            }

            // 2. 创建备份
            if (file.exists()) {
                createBackup(file);
            }

            // 3. 原子写入（先写临时文件再重命名）
            File tempFile = new File(file.getParentFile(), file.getName() + ".tmp");
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
                writer.write(content);
            }

            // 4. 重命名临时文件
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // 5. 更新状态
            entry.setContent(content);
            entry.setModified(false);
            entry.setLastModified(LocalDateTime.now());

            log.info("配置文件已保存: {}", file.getName());
            return true;

        } catch (Exception e) {
            log.error("保存配置文件失败: {}", entry.getName(), e);
            return false;
        }
    }

    /**
     * 验证配置内容格式
     */
    private boolean validateContent(ConfigFileEntry entry, String content) {
        try {
            switch (entry.getType()) {
                case YAML:
                    Yaml yaml = new Yaml();
                    yaml.load(content);
                    return true;

                case JSON:
                    com.alibaba.fastjson.JSON.parse(content);
                    return true;

                case PROPERTIES:
                case ENV:
                    // Properties和ENV格式比较宽松，基本都能通过
                    return true;

                default:
                    return true;
            }
        } catch (Exception e) {
            log.warn("配置格式验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取配置文件目录
     */
    public File getConfigDir() {
        return resourcesDir;
    }

    /**
     * 创建备份（公开方法，供外部调用）
     */
    public void createBackup(ConfigFileEntry entry) throws IOException {
        if (entry.getFile().exists()) {
            createBackup(entry.getFile());
        }
    }

    /**
     * 创建备份（私有方法）
     */
    private void createBackup(File originalFile) throws IOException {
        File backupDir = new File(originalFile.getParentFile(), BACKUP_DIR);
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        // 生成备份文件名
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupName = originalFile.getName() + "." + timestamp + ".bak";
        File backupFile = new File(backupDir, backupName);

        // 复制文件
        Files.copy(originalFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        // 清理旧备份
        cleanupOldBackups(backupDir, originalFile.getName());

        log.debug("已创建备份: {}", backupFile.getName());
    }

    /**
     * 清理旧备份
     */
    private void cleanupOldBackups(File backupDir, String originalName) {
        File[] backups = backupDir.listFiles((dir, name) ->
                name.startsWith(originalName) && name.endsWith(".bak"));

        if (backups != null && backups.length > MAX_BACKUPS) {
            // 按修改时间排序
            Arrays.sort(backups, Comparator.comparingLong(File::lastModified));

            // 删除最旧的备份
            for (int i = 0; i < backups.length - MAX_BACKUPS; i++) {
                backups[i].delete();
            }
        }
    }

    /**
     * 获取备份列表
     */
    public List<File> getBackups(ConfigFileEntry entry) {
        File backupDir = new File(entry.getFile().getParentFile(), BACKUP_DIR);
        if (!backupDir.exists()) {
            return Collections.emptyList();
        }

        File[] backups = backupDir.listFiles((dir, name) ->
                name.startsWith(entry.getName()) && name.endsWith(".bak"));

        if (backups == null) {
            return Collections.emptyList();
        }

        // 按时间倒序排列
        Arrays.sort(backups, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return Arrays.asList(backups);
    }

    /**
     * 恢复备份
     */
    public boolean restoreBackup(ConfigFileEntry entry, File backupFile) {
        try {
            // 先备份当前文件
            if (entry.getFile().exists()) {
                createBackup(entry.getFile());
            }

            // 恢复备份
            Files.copy(backupFile.toPath(), entry.getFile().toPath(), StandardCopyOption.REPLACE_EXISTING);

            // 重新读取内容
            readConfig(entry);

            log.info("已恢复备份: {} -> {}", backupFile.getName(), entry.getName());
            return true;

        } catch (Exception e) {
            log.error("恢复备份失败", e);
            return false;
        }
    }

    /**
     * 解析YAML为Map结构（用于树形显示）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseYaml(String content) {
        try {
            Yaml yaml = new Yaml();
            Object result = yaml.load(content);
            if (result instanceof Map) {
                return (Map<String, Object>) result;
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            log.warn("解析YAML失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 将Map转换回YAML字符串
     */
    public String toYamlString(Map<String, Object> data) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setAllowUnicode(true);

        Yaml yaml = new Yaml(options);
        return yaml.dump(data);
    }

    /**
     * 遮蔽敏感信息
     */
    public String maskSensitiveContent(String content, ConfigFileEntry entry) {
        if (!entry.isSensitive()) {
            return content;
        }

        // 遮蔽常见敏感字段
        String[] sensitivePatterns = {
                "(password\\s*[:=]\\s*)([^\\s\\n]+)",
                "(apikey\\s*[:=]\\s*)([^\\s\\n]+)",
                "(api_key\\s*[:=]\\s*)([^\\s\\n]+)",
                "(secret\\s*[:=]\\s*)([^\\s\\n]+)",
                "(ACCESS_KEY_SECRET\\s*[:=]\\s*)([^\\s\\n]+)"
        };

        String masked = content;
        for (String pattern : sensitivePatterns) {
            masked = masked.replaceAll("(?i)" + pattern, "$1********");
        }

        return masked;
    }

    /**
     * 刷新配置文件列表
     */
    public void refresh() {
        discoverConfigFiles();
    }

    /**
     * 创建新配置文件
     */
    public ConfigFileEntry createConfig(String name, ConfigFileEntry.ConfigType type,
                                         ConfigFileEntry.ConfigCategory category,
                                         String initialContent) throws IOException {
        File file = new File(resourcesDir, name);
        if (file.exists()) {
            throw new IOException("配置文件已存在: " + name);
        }

        // 写入初始内容
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(initialContent != null ? initialContent : "");
        }

        // 注册配置
        String id = name.replaceAll("[^a-zA-Z0-9]", "-").toLowerCase();
        ConfigFileEntry entry = new ConfigFileEntry(id, name, file, type, category)
                .displayName(name);
        configFiles.add(entry);

        log.info("已创建配置文件: {}", name);
        return entry;
    }
}
