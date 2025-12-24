package red.jiuzhou.analysis.aion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.util.*;

/**
 * Aion游戏机制检测器
 *
 * <p>专门为Aion游戏XML配置文件设计的机制识别引擎，支持：
 * <ul>
 *   <li>文件夹级别匹配 - 批量归类大量同类文件</li>
 *   <li>精确文件名匹配 - 预定义的已知文件</li>
 *   <li>正则模式匹配 - 按优先级顺序识别</li>
 *   <li>本地化识别 - 区分公共/China目录文件</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class AionMechanismDetector {

    private static final Logger log = LoggerFactory.getLogger(AionMechanismDetector.class);

    // 文件夹到机制的映射
    private static final Map<String, AionMechanismCategory> FOLDER_MAPPINGS = new HashMap<>();

    // 精确文件名到机制的映射
    private static final Map<String, AionMechanismCategory> EXACT_FILE_MAPPINGS = new HashMap<>();

    // 本地化目录名（不区分大小写）
    private static final Set<String> LOCALIZATION_FOLDERS = new HashSet<>();

    static {
        // 文件夹级别映射
        FOLDER_MAPPINGS.put("AnimationMarkers", AionMechanismCategory.ANIMATION_MARKERS);
        FOLDER_MAPPINGS.put("Animations", AionMechanismCategory.ANIMATION);
        FOLDER_MAPPINGS.put("Custompreset", AionMechanismCategory.CHARACTER_PRESET);
        FOLDER_MAPPINGS.put("Subzones", AionMechanismCategory.SUBZONE);
        FOLDER_MAPPINGS.put("ID", AionMechanismCategory.ID_MAPPING);
        FOLDER_MAPPINGS.put("Special01", AionMechanismCategory.GAME_CONFIG);

        // 精确文件名映射（常见的核心文件）
        EXACT_FILE_MAPPINGS.put("abyss.xml", AionMechanismCategory.ABYSS);
        EXACT_FILE_MAPPINGS.put("abyss_rank.xml", AionMechanismCategory.ABYSS);
        EXACT_FILE_MAPPINGS.put("abyss_rank_points.xml", AionMechanismCategory.ABYSS);
        EXACT_FILE_MAPPINGS.put("siege_locations.xml", AionMechanismCategory.ABYSS);

        // NPC系统
        EXACT_FILE_MAPPINGS.put("npcs.xml", AionMechanismCategory.NPC);
        EXACT_FILE_MAPPINGS.put("npc_shouts.xml", AionMechanismCategory.NPC);
        EXACT_FILE_MAPPINGS.put("npc_walkers.xml", AionMechanismCategory.NPC);
        EXACT_FILE_MAPPINGS.put("npc_scores.xml", AionMechanismCategory.NPC);
        EXACT_FILE_MAPPINGS.put("npc_tribe_relation.xml", AionMechanismCategory.NPC);
        EXACT_FILE_MAPPINGS.put("npcfactions.xml", AionMechanismCategory.NPC);

        // 客户端NPC/怪物相关文件
        EXACT_FILE_MAPPINGS.put("client_strings_npc.xml", AionMechanismCategory.NPC);
        EXACT_FILE_MAPPINGS.put("client_strings_monster.xml", AionMechanismCategory.NPC);
        EXACT_FILE_MAPPINGS.put("client_strings_dic_monster.xml", AionMechanismCategory.NPC);

        // 物品系统
        EXACT_FILE_MAPPINGS.put("items.xml", AionMechanismCategory.ITEM);
        EXACT_FILE_MAPPINGS.put("item_sets.xml", AionMechanismCategory.ITEM);

        // 客户端物品相关文件
        EXACT_FILE_MAPPINGS.put("client_strings_item.xml", AionMechanismCategory.ITEM);
        EXACT_FILE_MAPPINGS.put("client_strings_item2.xml", AionMechanismCategory.ITEM);
        EXACT_FILE_MAPPINGS.put("client_strings_item3.xml", AionMechanismCategory.ITEM);
        EXACT_FILE_MAPPINGS.put("client_strings_dic_item.xml", AionMechanismCategory.ITEM);

        // 技能系统核心文件
        EXACT_FILE_MAPPINGS.put("skills.xml", AionMechanismCategory.SKILL);
        EXACT_FILE_MAPPINGS.put("skill_trees.xml", AionMechanismCategory.SKILL);
        EXACT_FILE_MAPPINGS.put("skill_base.xml", AionMechanismCategory.SKILL);
        EXACT_FILE_MAPPINGS.put("skill_charge.xml", AionMechanismCategory.SKILL);
        EXACT_FILE_MAPPINGS.put("skill_learns.xml", AionMechanismCategory.SKILL);
        EXACT_FILE_MAPPINGS.put("skill_conflictcounts.xml", AionMechanismCategory.SKILL);
        EXACT_FILE_MAPPINGS.put("skill_damageattenuation.xml", AionMechanismCategory.SKILL);
        EXACT_FILE_MAPPINGS.put("skill_prohibit.xml", AionMechanismCategory.SKILL);
        EXACT_FILE_MAPPINGS.put("skill_qualification.xml", AionMechanismCategory.SKILL);
        EXACT_FILE_MAPPINGS.put("skill_randomdamage.xml", AionMechanismCategory.SKILL);
        EXACT_FILE_MAPPINGS.put("skill_signetdata.xml", AionMechanismCategory.SKILL);

        // 特殊技能文件（不是skill_开头但属于技能系统）
        EXACT_FILE_MAPPINGS.put("abyss_leader_skill.xml", AionMechanismCategory.SKILL);
        EXACT_FILE_MAPPINGS.put("devanion_skill_enchant.xml", AionMechanismCategory.SKILL);
        EXACT_FILE_MAPPINGS.put("exceed_skillset.xml", AionMechanismCategory.SKILL);
        EXACT_FILE_MAPPINGS.put("pc_skill_skin.xml", AionMechanismCategory.SKILL);
        EXACT_FILE_MAPPINGS.put("polymorph_temp_skill.xml", AionMechanismCategory.SKILL);
        EXACT_FILE_MAPPINGS.put("stigma_hiddenskill.xml", AionMechanismCategory.SKILL);

        // 客户端技能相关文件
        EXACT_FILE_MAPPINGS.put("client_strings_skill.xml", AionMechanismCategory.SKILL);

        // 任务系统核心文件
        EXACT_FILE_MAPPINGS.put("quests.xml", AionMechanismCategory.QUEST);
        EXACT_FILE_MAPPINGS.put("quest_data.xml", AionMechanismCategory.QUEST);
        EXACT_FILE_MAPPINGS.put("quest.xml", AionMechanismCategory.QUEST);
        EXACT_FILE_MAPPINGS.put("quest_random_rewards.xml", AionMechanismCategory.QUEST);

        // 特殊任务文件（不是quest开头但属于任务系统）
        EXACT_FILE_MAPPINGS.put("data_driven_quest.xml", AionMechanismCategory.QUEST);
        EXACT_FILE_MAPPINGS.put("jumping_addquest.xml", AionMechanismCategory.QUEST);
        EXACT_FILE_MAPPINGS.put("jumping_endquest.xml", AionMechanismCategory.QUEST);
        EXACT_FILE_MAPPINGS.put("npcfactions_quest.xml", AionMechanismCategory.QUEST);

        // 客户端任务相关文件
        EXACT_FILE_MAPPINGS.put("client_strings_quest.xml", AionMechanismCategory.QUEST);

        // 商店系统
        EXACT_FILE_MAPPINGS.put("goodslists.xml", AionMechanismCategory.SHOP);
        EXACT_FILE_MAPPINGS.put("goodslist.xml", AionMechanismCategory.SHOP);
        EXACT_FILE_MAPPINGS.put("abgoodslist.xml", AionMechanismCategory.SHOP);
        EXACT_FILE_MAPPINGS.put("merchants.xml", AionMechanismCategory.SHOP);
        EXACT_FILE_MAPPINGS.put("toypet_merchant.xml", AionMechanismCategory.SHOP);

        // 宠物系统
        EXACT_FILE_MAPPINGS.put("toypets.xml", AionMechanismCategory.PET);
        EXACT_FILE_MAPPINGS.put("familiars.xml", AionMechanismCategory.PET);

        // 客户端宠物相关文件
        EXACT_FILE_MAPPINGS.put("client_strings_funcpet.xml", AionMechanismCategory.PET);

        // 制作系统
        EXACT_FILE_MAPPINGS.put("recipes.xml", AionMechanismCategory.CRAFT);
        EXACT_FILE_MAPPINGS.put("assembly.xml", AionMechanismCategory.CRAFT);
        EXACT_FILE_MAPPINGS.put("combine_recipe.xml", AionMechanismCategory.CRAFT);
        EXACT_FILE_MAPPINGS.put("luna_combine_recipe.xml", AionMechanismCategory.CRAFT);
        EXACT_FILE_MAPPINGS.put("luna_combine_recipes_settings.xml", AionMechanismCategory.CRAFT);

        EXACT_FILE_MAPPINGS.put("titles.xml", AionMechanismCategory.TITLE);

        EXACT_FILE_MAPPINGS.put("portals.xml", AionMechanismCategory.PORTAL);
        EXACT_FILE_MAPPINGS.put("fly_paths.xml", AionMechanismCategory.PORTAL);

        EXACT_FILE_MAPPINGS.put("instances.xml", AionMechanismCategory.INSTANCE);

        EXACT_FILE_MAPPINGS.put("gotchas.xml", AionMechanismCategory.GOTCHA);

        EXACT_FILE_MAPPINGS.put("game_config.xml", AionMechanismCategory.GAME_CONFIG);
        EXACT_FILE_MAPPINGS.put("global_config.xml", AionMechanismCategory.GAME_CONFIG);

        // 本地化目录
        LOCALIZATION_FOLDERS.add("china");
        LOCALIZATION_FOLDERS.add("cn");
        LOCALIZATION_FOLDERS.add("zh");
        LOCALIZATION_FOLDERS.add("tw");
        LOCALIZATION_FOLDERS.add("kr");
        LOCALIZATION_FOLDERS.add("jp");
        LOCALIZATION_FOLDERS.add("eu");
        LOCALIZATION_FOLDERS.add("na");
        LOCALIZATION_FOLDERS.add("ru");
    }

    private final File publicRoot;
    private final File localizedRoot;

    /**
     * 创建检测器
     *
     * @param publicRoot    公共XML根目录
     * @param localizedRoot 本地化XML目录（可为null）
     */
    public AionMechanismDetector(File publicRoot, File localizedRoot) {
        this.publicRoot = publicRoot;
        this.localizedRoot = localizedRoot;
    }

    /**
     * 扫描并构建机制视图
     */
    public AionMechanismView scan() {
        AionMechanismView view = new AionMechanismView();

        log.info("开始扫描Aion XML目录: {}", publicRoot.getAbsolutePath());

        // 扫描公共目录
        Set<String> publicFileNames = new HashSet<>();
        scanDirectory(publicRoot, publicRoot, view, false, publicFileNames);

        // 扫描本地化目录
        if (localizedRoot != null && localizedRoot.exists()) {
            log.info("扫描本地化目录: {}", localizedRoot.getAbsolutePath());
            Set<String> localizedFileNames = new HashSet<>();
            scanDirectory(localizedRoot, localizedRoot, view, true, localizedFileNames);

            // 检测本地化覆盖
            detectLocalizedOverrides(view, publicFileNames, localizedFileNames);
        }

        // 更新统计
        updateStatistics(view);

        log.info("扫描完成: {}", view.getStatistics().getSummary());
        return view;
    }

    /**
     * 递归扫描目录
     */
    private void scanDirectory(File dir, File root, AionMechanismView view,
                               boolean isLocalized, Set<String> fileNames) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isFile() && f.getName().toLowerCase().endsWith(".xml");
            }
        });

        File[] subDirs = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory();
            }
        });

        // 处理XML文件
        if (files != null) {
            for (File file : files) {
                String relativePath = getRelativePath(root, file);
                DetectionResult result = detect(file, relativePath, isLocalized);

                AionMechanismView.FileEntry entry = new AionMechanismView.FileEntry(
                        file.getName(),
                        relativePath,
                        file,
                        result,
                        isLocalized
                );

                view.addFile(entry);
                fileNames.add(file.getName().toLowerCase());
            }
        }

        // 递归处理子目录（跳过本地化目录避免重复扫描）
        if (subDirs != null) {
            for (File subDir : subDirs) {
                String dirName = subDir.getName().toLowerCase();
                // 如果是本地化目录，且当前不是本地化模式，则跳过（稍后单独扫描）
                if (!isLocalized && LOCALIZATION_FOLDERS.contains(dirName)) {
                    continue;
                }
                scanDirectory(subDir, root, view, isLocalized, fileNames);
            }
        }
    }

    /**
     * 检测单个文件的机制分类
     */
    public DetectionResult detect(File file, String relativePath, boolean isLocalized) {
        String fileName = file.getName();
        String fileNameLower = fileName.toLowerCase();
        String parentFolder = file.getParentFile() != null ? file.getParentFile().getName() : "";

        // 加载手动覆盖配置
        MechanismOverrideConfig overrideConfig = MechanismOverrideConfig.getInstance();

        // 0. 手动覆盖检查（最高优先级）
        if (overrideConfig.hasOverride(fileName)) {
            AionMechanismCategory overrideCategory = overrideConfig.getOverride(fileName);
            log.debug("手动覆盖匹配: {} -> {}", fileName, overrideCategory.getDisplayName());
            return DetectionResult.builder()
                    .category(overrideCategory)
                    .confidence(0.99)
                    .reasoning("手动覆盖配置: " + fileName)
                    .localized(isLocalized)
                    .relativePath(relativePath)
                    .build();
        }

        // 0.5. 排除列表检查
        if (overrideConfig.isExcluded(fileName)) {
            log.debug("文件在排除列表中: {}", fileName);
            return DetectionResult.builder()
                    .category(AionMechanismCategory.OTHER)
                    .confidence(0.95)
                    .reasoning("手动排除: " + fileName)
                    .localized(isLocalized)
                    .relativePath(relativePath)
                    .build();
        }

        // 1. 文件夹级别匹配（最高优先级）
        AionMechanismCategory folderCategory = FOLDER_MAPPINGS.get(parentFolder);
        if (folderCategory != null) {
            return DetectionResult.builder()
                    .category(folderCategory)
                    .confidence(0.95)
                    .reasoning("文件夹匹配: " + parentFolder)
                    .localized(isLocalized)
                    .relativePath(relativePath)
                    .build();
        }

        // 2. 精确文件名匹配
        AionMechanismCategory exactCategory = EXACT_FILE_MAPPINGS.get(fileNameLower);
        if (exactCategory != null) {
            log.debug("精确匹配成功: {} -> {}", fileName, exactCategory.getDisplayName());
            return DetectionResult.builder()
                    .category(exactCategory)
                    .confidence(0.98)
                    .reasoning("精确文件名匹配: " + fileName)
                    .localized(isLocalized)
                    .relativePath(relativePath)
                    .build();
        } else if (fileNameLower.contains("client_strings")) {
            // 调试日志：client_strings 文件未匹配
            log.warn("client_strings文件未精确匹配: {}, 将使用正则匹配", fileName);
        }

        // 3. 正则模式匹配（按优先级排序）
        List<AionMechanismCategory> sortedCategories = getSortedCategories();
        for (AionMechanismCategory category : sortedCategories) {
            if (category.matches(fileName)) {
                double confidence = 0.5 + (category.getPriority() * 0.05);
                confidence = Math.min(confidence, 0.9);

                return DetectionResult.builder()
                        .category(category)
                        .confidence(confidence)
                        .reasoning("正则模式匹配: " + category.getPattern().pattern())
                        .localized(isLocalized)
                        .relativePath(relativePath)
                        .build();
            }
        }

        // 4. 兜底分类
        return DetectionResult.builder()
                .category(AionMechanismCategory.OTHER)
                .confidence(0.3)
                .reasoning("未匹配任何已知模式")
                .localized(isLocalized)
                .relativePath(relativePath)
                .build();
    }

    /**
     * 获取按优先级排序的分类列表
     */
    private List<AionMechanismCategory> getSortedCategories() {
        List<AionMechanismCategory> categories = new ArrayList<>();
        for (AionMechanismCategory cat : AionMechanismCategory.values()) {
            if (cat != AionMechanismCategory.OTHER) {
                categories.add(cat);
            }
        }

        Collections.sort(categories, new Comparator<AionMechanismCategory>() {
            @Override
            public int compare(AionMechanismCategory a, AionMechanismCategory b) {
                return Integer.compare(b.getPriority(), a.getPriority());
            }
        });

        return categories;
    }

    /**
     * 检测本地化覆盖
     */
    private void detectLocalizedOverrides(AionMechanismView view,
                                          Set<String> publicFiles,
                                          Set<String> localizedFiles) {
        // 找出同时存在于两个目录的文件
        Set<String> overriddenFiles = new HashSet<>(publicFiles);
        overriddenFiles.retainAll(localizedFiles);

        for (String fileName : overriddenFiles) {
            File publicFile = findFile(publicRoot, fileName);
            File localizedFile = findFile(localizedRoot, fileName);

            if (publicFile != null && localizedFile != null) {
                DetectionResult result = detect(publicFile, fileName, false);
                AionMechanismView.LocalizedOverride override =
                        new AionMechanismView.LocalizedOverride(
                                fileName,
                                publicFile,
                                localizedFile,
                                result.getCategory()
                        );
                view.addLocalizedOverride(override);
            }
        }

        log.info("检测到 {} 个本地化覆盖文件", overriddenFiles.size());
    }

    /**
     * 在目录中查找文件（递归）
     */
    private File findFile(File dir, String fileName) {
        if (dir == null || !dir.exists()) {
            return null;
        }

        File direct = new File(dir, fileName);
        if (direct.exists() && direct.isFile()) {
            return direct;
        }

        File[] subDirs = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory();
            }
        });

        if (subDirs != null) {
            for (File subDir : subDirs) {
                File found = findFile(subDir, fileName);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * 获取相对路径
     */
    private String getRelativePath(File root, File file) {
        String rootPath = root.getAbsolutePath();
        String filePath = file.getAbsolutePath();

        if (filePath.startsWith(rootPath)) {
            String relative = filePath.substring(rootPath.length());
            if (relative.startsWith(File.separator)) {
                relative = relative.substring(1);
            }
            return relative;
        }

        return file.getName();
    }

    /**
     * 更新统计信息
     */
    private void updateStatistics(AionMechanismView view) {
        AionMechanismView.Statistics stats = view.getStatistics();
        for (AionMechanismView.MechanismGroup group : view.getNonEmptyGroups()) {
            stats.incrementCategory(group.getCategory());
        }
    }

    /**
     * 生成机制分布报告
     */
    public static String generateReport(AionMechanismView view) {
        StringBuilder sb = new StringBuilder();
        sb.append("Aion游戏机制分布统计\n");
        sb.append(repeatString("=", 60)).append("\n\n");

        List<AionMechanismView.MechanismGroup> groups = view.getNonEmptyGroups();

        // 按文件数量排序
        Collections.sort(groups, new Comparator<AionMechanismView.MechanismGroup>() {
            @Override
            public int compare(AionMechanismView.MechanismGroup a, AionMechanismView.MechanismGroup b) {
                return Integer.compare(b.getFileCount(), a.getFileCount());
            }
        });

        for (AionMechanismView.MechanismGroup group : groups) {
            AionMechanismCategory cat = group.getCategory();
            sb.append(String.format("【%s】 %d个文件 (公共:%d, 本地化:%d)\n",
                    cat.getDisplayName(),
                    group.getFileCount(),
                    group.getPublicFileCount(),
                    group.getLocalizedFileCount()));
            sb.append("  描述: ").append(cat.getDescription()).append("\n\n");
        }

        sb.append(repeatString("-", 60)).append("\n");
        sb.append(view.getStatistics().getSummary()).append("\n");

        // 本地化覆盖统计
        List<AionMechanismView.LocalizedOverride> overrides = view.getLocalizedOverrides();
        if (!overrides.isEmpty()) {
            sb.append("\n本地化覆盖: ").append(overrides.size()).append(" 个文件\n");
        }

        return sb.toString();
    }

    /**
     * Java 8兼容的字符串重复
     */
    private static String repeatString(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    // ==================== 静态工具方法（供其他类使用） ====================

    /**
     * 获取文件夹级别映射
     * 供 MechanismFileMapper 使用，保持数据源一致性
     *
     * @param dirName 目录名（不区分大小写）
     * @return 对应的机制分类，如果没有映射则返回 null
     */
    public static AionMechanismCategory getFolderMapping(String dirName) {
        if (dirName == null) return null;
        // 遍历查找（不区分大小写）
        for (Map.Entry<String, AionMechanismCategory> entry : FOLDER_MAPPINGS.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(dirName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 检测文件所属机制（静态方法）
     * 供 MechanismFileMapper 使用，保持检测逻辑一致性
     *
     * @param file 要检测的文件
     * @param parentCategory 父目录的机制分类（如果有的话）
     * @return 检测到的机制分类
     */
    public static AionMechanismCategory detectMechanismForFile(File file, AionMechanismCategory parentCategory) {
        if (file == null) return AionMechanismCategory.OTHER;

        String fileName = file.getName().toLowerCase();

        // 1. 检查手动覆盖配置（最高优先级）
        MechanismOverrideConfig overrideConfig = MechanismOverrideConfig.getInstance();
        if (overrideConfig.hasOverride(fileName)) {
            return overrideConfig.getOverride(fileName);
        }

        // 2. 检查是否被排除
        if (overrideConfig.isExcluded(fileName)) {
            return AionMechanismCategory.OTHER;
        }

        // 3. 精确文件名匹配
        AionMechanismCategory exact = EXACT_FILE_MAPPINGS.get(fileName);
        if (exact != null) {
            return exact;
        }

        // 4. 继承父目录机制（文件夹级别映射）
        if (parentCategory != null) {
            return parentCategory;
        }

        // 5. 检查文件所在目录是否有文件夹映射
        File parent = file.getParentFile();
        while (parent != null) {
            AionMechanismCategory folderCategory = getFolderMapping(parent.getName());
            if (folderCategory != null) {
                return folderCategory;
            }
            parent = parent.getParentFile();
        }

        // 6. 正则模式匹配（按优先级排序）
        List<AionMechanismCategory> sortedCategories = new ArrayList<>(Arrays.asList(AionMechanismCategory.values()));
        Collections.sort(sortedCategories, new Comparator<AionMechanismCategory>() {
            @Override
            public int compare(AionMechanismCategory a, AionMechanismCategory b) {
                return Integer.compare(b.getPriority(), a.getPriority());
            }
        });

        for (AionMechanismCategory category : sortedCategories) {
            if (category == AionMechanismCategory.OTHER) continue;
            if (category.matches(fileName)) {
                return category;
            }
        }

        // 7. 兜底分类
        return AionMechanismCategory.OTHER;
    }
}
