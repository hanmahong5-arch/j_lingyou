package red.jiuzhou.analysis.aion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 机制-文件映射服务
 *
 * 建立游戏机制分类与XML文件的双向映射关系：
 * - 机制 → 文件列表（按机制过滤目录树）
 * - 文件 → 机制（显示文件所属机制标签）
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class MechanismFileMapper {

    private static final Logger log = LoggerFactory.getLogger(MechanismFileMapper.class);

    /** 单例实例 */
    private static MechanismFileMapper instance;

    /** 文件路径 → 机制分类 缓存 */
    private final Map<String, AionMechanismCategory> fileToMechanism = new ConcurrentHashMap<>();

    /** 机制分类 → 文件路径集合 缓存 */
    private final Map<AionMechanismCategory, Set<String>> mechanismToFiles = new ConcurrentHashMap<>();

    /** 已扫描的根目录 */
    private final Set<String> scannedRoots = new HashSet<>();

    /** 常用机制（显示在标签栏） */
    private static final List<AionMechanismCategory> COMMON_MECHANISMS = Arrays.asList(
        AionMechanismCategory.ITEM,
        AionMechanismCategory.NPC,
        AionMechanismCategory.SKILL,
        AionMechanismCategory.QUEST,
        AionMechanismCategory.DROP,
        AionMechanismCategory.INSTANCE,
        AionMechanismCategory.SHOP,
        AionMechanismCategory.CRAFT,
        AionMechanismCategory.ABYSS,
        AionMechanismCategory.PET,
        AionMechanismCategory.ENCHANT,
        AionMechanismCategory.CLIENT_STRINGS
    );

    /**
     * 使用 AionMechanismDetector 的静态映射，保持一致性
     * 不再维护独立的映射副本，避免数据不一致
     */

    private MechanismFileMapper() {
        // 初始化每个机制的文件集合
        for (AionMechanismCategory category : AionMechanismCategory.values()) {
            mechanismToFiles.put(category, ConcurrentHashMap.newKeySet());
        }
    }

    /**
     * 获取单例实例
     */
    public static synchronized MechanismFileMapper getInstance() {
        if (instance == null) {
            instance = new MechanismFileMapper();
        }
        return instance;
    }

    /**
     * 扫描目录并建立映射
     */
    public void scanDirectory(String rootPath) {
        if (rootPath == null || rootPath.isEmpty()) {
            log.warn("扫描路径为空，跳过");
            return;
        }

        File root = new File(rootPath);
        if (!root.exists()) {
            log.warn("目录不存在: {}", rootPath);
            return;
        }
        if (!root.isDirectory()) {
            log.warn("路径不是目录: {}", rootPath);
            return;
        }

        String normalizedPath;
        try {
            normalizedPath = root.getCanonicalPath().toLowerCase();
        } catch (Exception e) {
            normalizedPath = root.getAbsolutePath().toLowerCase();
        }

        if (scannedRoots.contains(normalizedPath)) {
            log.debug("目录已扫描过: {}", rootPath);
            return;
        }

        log.info("开始扫描目录建立机制映射: {}", rootPath);
        long start = System.currentTimeMillis();
        int initialCount = fileToMechanism.size();

        try {
            scanRecursively(root, null);
            scannedRoots.add(normalizedPath);

            int newFiles = fileToMechanism.size() - initialCount;
            log.info("目录扫描完成: 新增 {} 个文件, 总计 {} 个文件, 耗时 {}ms",
                newFiles, fileToMechanism.size(), System.currentTimeMillis() - start);

            // 输出机制统计
            if (log.isDebugEnabled()) {
                Map<AionMechanismCategory, Integer> stats = getMechanismStats();
                log.debug("机制分布: {}", stats);
            }
        } catch (Exception e) {
            log.error("扫描目录失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 异步扫描目录（不阻塞UI）
     */
    public void scanDirectoryAsync(String rootPath, Runnable onComplete) {
        new Thread(() -> {
            scanDirectory(rootPath);
            if (onComplete != null) {
                javafx.application.Platform.runLater(onComplete);
            }
        }, "MechanismScanner").start();
    }

    /**
     * 递归扫描目录
     * 使用 AionMechanismDetector 的文件夹映射
     */
    private void scanRecursively(File dir, AionMechanismCategory parentCategory) {
        if (dir == null || !dir.exists()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        // 检查当前目录是否有文件夹级别映射（使用 AionMechanismDetector）
        String dirName = dir.getName().toLowerCase();
        AionMechanismCategory folderCategory = AionMechanismDetector.getFolderMapping(dirName);
        if (folderCategory != null) {
            parentCategory = folderCategory;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                scanRecursively(file, parentCategory);
            } else if (file.getName().toLowerCase().endsWith(".xml")) {
                AionMechanismCategory category = detectMechanism(file, parentCategory);
                registerFile(file.getAbsolutePath(), category);
            }
        }
    }

    /**
     * 检测文件所属机制
     * 委托给 AionMechanismDetector 以保持一致性
     */
    private AionMechanismCategory detectMechanism(File file, AionMechanismCategory parentCategory) {
        // 委托给 AionMechanismDetector 的静态方法
        return AionMechanismDetector.detectMechanismForFile(file, parentCategory);
    }

    /**
     * 注册文件映射
     */
    private void registerFile(String filePath, AionMechanismCategory category) {
        fileToMechanism.put(filePath.toLowerCase(), category);
        mechanismToFiles.get(category).add(filePath);
    }

    /**
     * 获取文件所属机制
     */
    public AionMechanismCategory getMechanism(String filePath) {
        if (filePath == null) return AionMechanismCategory.OTHER;
        return fileToMechanism.getOrDefault(filePath.toLowerCase(), AionMechanismCategory.OTHER);
    }

    /**
     * 获取机制下的所有文件
     */
    public Set<String> getFiles(AionMechanismCategory category) {
        return mechanismToFiles.getOrDefault(category, Collections.emptySet());
    }

    /**
     * 检查文件是否属于指定机制
     */
    public boolean belongsTo(String filePath, AionMechanismCategory category) {
        return getMechanism(filePath) == category;
    }

    /**
     * 获取常用机制列表（用于标签栏显示）
     */
    public List<AionMechanismCategory> getCommonMechanisms() {
        return COMMON_MECHANISMS;
    }

    /**
     * 获取所有机制列表（30种）
     */
    public List<AionMechanismCategory> getAllMechanisms() {
        return Arrays.asList(AionMechanismCategory.values());
    }

    /**
     * 获取所有机制及其文件数量统计
     */
    public Map<AionMechanismCategory, Integer> getMechanismStats() {
        Map<AionMechanismCategory, Integer> stats = new LinkedHashMap<>();
        for (AionMechanismCategory category : AionMechanismCategory.values()) {
            int count = mechanismToFiles.get(category).size();
            if (count > 0) {
                stats.put(category, count);
            }
        }
        return stats;
    }

    /**
     * 按机制过滤文件路径
     */
    public boolean matchesMechanism(String filePath, AionMechanismCategory category) {
        if (category == null) return true; // null表示不过滤
        return getMechanism(filePath) == category;
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        fileToMechanism.clear();
        for (Set<String> files : mechanismToFiles.values()) {
            files.clear();
        }
        scannedRoots.clear();
        log.info("机制映射缓存已清除");
    }

    /**
     * 获取机制的显示信息
     */
    public static class MechanismInfo {
        private final AionMechanismCategory category;
        private final int fileCount;

        public MechanismInfo(AionMechanismCategory category, int fileCount) {
            this.category = category;
            this.fileCount = fileCount;
        }

        public AionMechanismCategory getCategory() { return category; }
        public int getFileCount() { return fileCount; }
        public String getDisplayName() { return category.getDisplayName(); }
        public String getColor() { return category.getColor(); }
        public String getIcon() { return category.getIcon(); }
    }

    /**
     * 获取机制信息列表（用于UI显示）
     */
    public List<MechanismInfo> getMechanismInfoList() {
        List<MechanismInfo> list = new ArrayList<>();
        for (AionMechanismCategory category : COMMON_MECHANISMS) {
            int count = mechanismToFiles.get(category).size();
            list.add(new MechanismInfo(category, count));
        }
        return list;
    }

    /**
     * 检测文件路径的机制（静态方法，不依赖缓存）
     * 委托给 AionMechanismDetector 以保持一致性
     */
    public static AionMechanismCategory detectMechanismStatic(String filePath) {
        if (filePath == null) return AionMechanismCategory.OTHER;
        return AionMechanismDetector.detectMechanismForFile(new File(filePath), null);
    }
}
