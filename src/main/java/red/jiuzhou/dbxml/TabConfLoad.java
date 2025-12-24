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
     * 根据表名获取表配置
     *
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
        log.info("tabFilePath: {}", tabFilePath);
        String fPath = PathUtil.getConfPath(FileUtil.getParent(tabFilePath, 1));
        if("world".equals(tabName)){
            fPath = YamlUtils.getProperty("file.confPath") + File.separator + "Worlds";
        }

        tabFilePath = fPath + File.separator + tabName + ".json";
        log.info("加载配置文件：{}", tabFilePath);
        if(!FileUtil.exist(tabFilePath)){
            throw new RuntimeException("表配置文件不存在: " + tabFilePath);
        }
        String jsonContent = FileUtil.readUtf8String(tabFilePath);

        return JSON.parseObject(jsonContent, TableConf.class);
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