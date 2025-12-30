package red.jiuzhou.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.server.dao.ServerConfigFileDao;
import red.jiuzhou.server.model.ServerConfigFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 服务器日志分析器
 * 分析 MainServer/NPCServer 日志，提取服务器实际加载的 XML 文件列表
 */
public class ServerLogAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(ServerLogAnalyzer.class);

    private final ServerConfigFileDao dao;

    // XML 文件名匹配模式
    private static final Pattern XML_FILE_PATTERN = Pattern.compile("([a-zA-Z0-9_-]+\\.xml)", Pattern.CASE_INSENSITIVE);

    // 日志关键字模式（标识文件被加载）
    private static final List<Pattern> LOAD_PATTERNS = Arrays.asList(
            Pattern.compile("Loading.*?([a-zA-Z0-9_-]+\\.xml)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Loaded.*?([a-zA-Z0-9_-]+\\.xml)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Reading.*?([a-zA-Z0-9_-]+\\.xml)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Parse.*?([a-zA-Z0-9_-]+\\.xml)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Initialize.*?([a-zA-Z0-9_-]+\\.xml)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([a-zA-Z0-9_-]+\\.xml).*?loaded", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([a-zA-Z0-9_-]+\\.xml).*?successfully", Pattern.CASE_INSENSITIVE)
    );

    // 错误模式（标识文件加载失败但仍然被尝试加载）
    private static final List<Pattern> ERROR_PATTERNS = Arrays.asList(
            Pattern.compile("Error.*?([a-zA-Z0-9_-]+\\.xml)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Failed.*?([a-zA-Z0-9_-]+\\.xml)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\(([a-zA-Z0-9_-]+\\.xml)\\)", Pattern.CASE_INSENSITIVE)
    );

    public ServerLogAnalyzer() {
        this.dao = new ServerConfigFileDao();
    }

    public ServerLogAnalyzer(ServerConfigFileDao dao) {
        this.dao = dao;
    }

    /**
     * 分析指定日志目录
     */
    public AnalysisResult analyzeLogDirectory(String logDirPath) {
        AnalysisResult result = new AnalysisResult();
        Path logDir = Paths.get(logDirPath);

        if (!Files.exists(logDir) || !Files.isDirectory(logDir)) {
            log.error("日志目录不存在: {}", logDirPath);
            result.setErrorMessage("日志目录不存在: " + logDirPath);
            return result;
        }

        log.info("开始分析日志目录: {}", logDirPath);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logDir, "*.{err,log}")) {
            for (Path logFile : stream) {
                log.info("分析日志文件: {}", logFile.getFileName());
                analyzeLogFile(logFile.toString(), result);
            }
        } catch (IOException e) {
            log.error("读取日志目录失败: {}", e.getMessage(), e);
            result.setErrorMessage("读取日志目录失败: " + e.getMessage());
        }

        log.info("日志分析完成，共发现 {} 个 XML 文件", result.getXmlFiles().size());
        return result;
    }

    /**
     * 分析单个日志文件
     */
    public void analyzeLogFile(String logFilePath, AnalysisResult result) {
        Path logFile = Paths.get(logFilePath);

        if (!Files.exists(logFile)) {
            log.warn("日志文件不存在: {}", logFilePath);
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                analyzeLine(line, result);
            }
        } catch (IOException e) {
            log.error("读取日志文件失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 分析单行日志
     */
    private void analyzeLine(String line, AnalysisResult result) {
        // 检查是否包含 XML 文件加载信息
        for (Pattern pattern : LOAD_PATTERNS) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String fileName = matcher.group(1);
                result.addXmlFile(fileName, true);
                return;
            }
        }

        // 检查错误日志中的 XML 文件（说明服务器尝试加载过）
        for (Pattern pattern : ERROR_PATTERNS) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String fileName = matcher.group(1);
                result.addXmlFile(fileName, false);
                result.addError(fileName, line);
            }
        }
    }

    /**
     * 保存分析结果到数据库
     */
    public int saveAnalysisResult(AnalysisResult result, String serverModule) {
        int savedCount = 0;

        for (Map.Entry<String, FileLoadInfo> entry : result.getXmlFiles().entrySet()) {
            String fileName = entry.getKey();
            FileLoadInfo loadInfo = entry.getValue();

            ServerConfigFile configFile = new ServerConfigFile();
            configFile.setFileName(fileName);
            configFile.setIsServerLoaded(loadInfo.isSuccessfullyLoaded());
            configFile.setServerModule(serverModule);

            // 推断文件分类
            String category = inferCategory(fileName);
            configFile.setFileCategory(category);

            // 推断加载优先级
            int priority = inferPriority(fileName, category);
            configFile.setLoadPriority(priority);

            // 推断是否核心文件
            boolean isCritical = inferCritical(fileName, category);
            configFile.setIsCritical(isCritical);

            // 推断表名
            String tableName = inferTableName(fileName);
            configFile.setTableName(tableName);

            // 验证状态
            if (loadInfo.getErrorCount() > 0) {
                configFile.setValidationStatus("invalid");
                configFile.setValidationErrors(String.join("\n", loadInfo.getErrors()));
            } else if (loadInfo.isSuccessfullyLoaded()) {
                configFile.setValidationStatus("valid");
            } else {
                configFile.setValidationStatus("unknown");
            }

            try {
                dao.upsert(configFile);
                savedCount++;
            } catch (Exception e) {
                log.error("保存配置文件记录失败: {} - {}", fileName, e.getMessage());
            }
        }

        log.info("成功保存 {} 个配置文件记录", savedCount);
        return savedCount;
    }

    /**
     * 推断文件分类
     */
    private String inferCategory(String fileName) {
        String lowerName = fileName.toLowerCase();

        if (lowerName.contains("item")) return "items";
        if (lowerName.contains("skill")) return "skills";
        if (lowerName.contains("quest")) return "quests";
        if (lowerName.contains("npc")) return "npcs";
        if (lowerName.contains("world") || lowerName.contains("map")) return "worlds";
        if (lowerName.contains("spawn")) return "spawns";
        if (lowerName.contains("drop")) return "drops";
        if (lowerName.contains("recipe") || lowerName.contains("craft")) return "crafting";
        if (lowerName.contains("housing")) return "housing";
        if (lowerName.contains("reward")) return "rewards";
        if (lowerName.contains("config")) return "config";

        return "other";
    }

    /**
     * 推断加载优先级
     */
    private int inferPriority(String fileName, String category) {
        String lowerName = fileName.toLowerCase();

        // 核心配置文件 - 优先级 1
        if (lowerName.matches("(items?|skills?|npcs?|quests?|world)s?\\.xml")) {
            return ServerConfigFile.LoadPriority.CRITICAL.getLevel();
        }

        if (category.matches("items|skills|quests|npcs|worlds")) {
            return ServerConfigFile.LoadPriority.IMPORTANT.getLevel();
        }

        return ServerConfigFile.LoadPriority.NORMAL.getLevel();
    }

    /**
     * 推断是否核心文件
     */
    private boolean inferCritical(String fileName, String category) {
        String lowerName = fileName.toLowerCase();

        return lowerName.matches("(items?|skills?|skill_base|npcs?|quests?|world)s?\\.xml") ||
               category.matches("items|skills|quests|npcs|worlds");
    }

    /**
     * 推断表名（去掉 .xml 后缀）
     */
    private String inferTableName(String fileName) {
        return fileName.replaceAll("\\.xml$", "").toLowerCase();
    }

    /**
     * 分析结果类
     */
    public static class AnalysisResult {
        private final Map<String, FileLoadInfo> xmlFiles = new TreeMap<>();
        private String errorMessage;

        public void addXmlFile(String fileName, boolean successfullyLoaded) {
            xmlFiles.computeIfAbsent(fileName, k -> new FileLoadInfo())
                    .setSuccessfullyLoaded(successfullyLoaded);
        }

        public void addError(String fileName, String errorLine) {
            xmlFiles.computeIfAbsent(fileName, k -> new FileLoadInfo())
                    .addError(errorLine);
        }

        public Map<String, FileLoadInfo> getXmlFiles() { return xmlFiles; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }

    /**
     * 文件加载信息
     */
    public static class FileLoadInfo {
        private boolean successfullyLoaded = false;
        private final List<String> errors = new ArrayList<>();

        public boolean isSuccessfullyLoaded() { return successfullyLoaded; }
        public void setSuccessfullyLoaded(boolean loaded) { this.successfullyLoaded = loaded; }

        public void addError(String error) { errors.add(error); }
        public List<String> getErrors() { return errors; }
        public int getErrorCount() { return errors.size(); }
    }
}
