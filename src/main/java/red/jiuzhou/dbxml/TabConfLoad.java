package red.jiuzhou.dbxml;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.util.PathUtil;
import red.jiuzhou.util.YamlUtils;

import org.springframework.core.io.ClassPathResource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TabConfLoad {
    private static final Logger log = LoggerFactory.getLogger(TabConfLoad.class);

    // 配置文件路径（延迟初始化）
    private static String CONFIG_FILE_PATH;

    // ========== 缓存机制 ==========
    // 配置文件缓存（key: tabName:configPath）
    private static final Map<String, TableConf> CONF_CACHE = new ConcurrentHashMap<>();
    // 缓存时间戳（用于 TTL 判断）
    private static final Map<String, Long> CACHE_TIMESTAMPS = new ConcurrentHashMap<>();
    // 缓存有效期：5分钟
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    /**
     * 获取配置文件路径（延迟初始化）
     */
    private static synchronized String getConfigFilePath() {
        if (CONFIG_FILE_PATH == null) {
            try {
                ClassPathResource classPathResource = new ClassPathResource("application.yml");
                try (InputStream inputStream = classPathResource.getInputStream()) {
                    CONFIG_FILE_PATH = classPathResource.getFile().getParent();
                    log.info("配置文件路径：{}", CONFIG_FILE_PATH);
                }
            } catch (IOException e) {
                log.warn("无法加载配置文件路径，使用默认值", e);
                // 使用默认路径
                CONFIG_FILE_PATH = System.getProperty("user.dir") + File.separator + "target" + File.separator + "classes";
            }
        }
        return CONFIG_FILE_PATH;
    }

    /**
     * 根据表名获取表配置（带缓存）
     *
     * @param tabName 表名
     * @param tabFilePath 配置表路径
     * @return 表配置对象
     * @throws RuntimeException 如果未找到对应的表配置
     */
    public static TableConf getTale(String tabName, String tabFilePath) {
        if (tabName == null || tabName.isEmpty()) {
            throw new IllegalArgumentException("表名不能为空");
        }
        if (tabFilePath == null || tabFilePath.isEmpty()) {
            throw new IllegalArgumentException("配置文件路径不能为空");
        }

        // 计算配置文件的实际路径
        String configPath = computeConfigPath(tabName, tabFilePath);
        String cacheKey = tabName + ":" + configPath;

        // 检查缓存是否有效
        Long timestamp = CACHE_TIMESTAMPS.get(cacheKey);
        if (timestamp != null && System.currentTimeMillis() - timestamp < CACHE_TTL_MS) {
            TableConf cached = CONF_CACHE.get(cacheKey);
            if (cached != null) {
                log.debug("从缓存加载配置: {}", cacheKey);
                return cached;
            }
        }

        // 缓存未命中或已过期，从文件加载
        log.info("从文件加载配置: {}", configPath);
        if (!FileUtil.exist(configPath)) {
            throw new RuntimeException("表配置文件不存在: " + configPath);
        }
        String jsonContent = FileUtil.readUtf8String(configPath);
        TableConf conf = JSON.parseObject(jsonContent, TableConf.class);

        // 更新缓存
        CONF_CACHE.put(cacheKey, conf);
        CACHE_TIMESTAMPS.put(cacheKey, System.currentTimeMillis());

        return conf;
    }

    /**
     * 计算配置文件的完整路径
     */
    private static String computeConfigPath(String tabName, String tabFilePath) {
        String fPath = PathUtil.getConfPath(FileUtil.getParent(tabFilePath, 1));
        if ("world".equals(tabName)) {
            fPath = YamlUtils.getProperty("file.confPath") + File.separator + "Worlds";
        }
        return fPath + File.separator + tabName + ".json";
    }

    /**
     * 失效指定表的缓存
     *
     * @param tabName 表名（如果为 null 则清空所有缓存）
     */
    public static void invalidateCache(String tabName) {
        if (tabName == null) {
            clearAllCache();
            return;
        }
        String prefix = tabName + ":";
        CONF_CACHE.entrySet().removeIf(e -> e.getKey().startsWith(prefix));
        CACHE_TIMESTAMPS.entrySet().removeIf(e -> e.getKey().startsWith(prefix));
        log.info("缓存已失效: {}", tabName);
    }

    /**
     * 清空所有缓存
     */
    public static void clearAllCache() {
        CONF_CACHE.clear();
        CACHE_TIMESTAMPS.clear();
        log.info("所有配置缓存已清空");
    }

    /**
     * 获取缓存统计信息
     */
    public static String getCacheStats() {
        return String.format("TabConfLoad 缓存: %d 个配置, %d 个时间戳",
                CONF_CACHE.size(), CACHE_TIMESTAMPS.size());
    }

    public static TableNode getTableNodeByTabName(String tabName, String tabFilePath) {
        return TableForestBuilder.buildOneForest(getTale(tabName, tabFilePath)).tableIndex.get(tabName);
    }

    public static String getRootTableName(String tableName, String tabFilePath) {
        TableNode node = TableForestBuilder.buildOneForest(getTale(tableName, tabFilePath)).tableIndex.get(tableName);
        if (node == null) {
            throw new IllegalArgumentException("表名不存在于树中: " + tableName);
        }

        while (node.parent != null) {
            node = node.parent;
        }

        return node.tableName;
    }
}