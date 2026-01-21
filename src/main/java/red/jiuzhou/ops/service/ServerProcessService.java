package red.jiuzhou.ops.service;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.ops.model.ServerProcess;
import red.jiuzhou.ops.model.ServerProcess.ProcessStatus;
import red.jiuzhou.ops.model.ServerProcess.StartupPriority;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Aion 服务器进程管理服务
 *
 * 功能:
 * - 扫描服务器目录发现所有服务进程
 * - 检测进程运行状态
 * - 启动/停止服务器进程
 * - 批量启动（按顺序）
 * - 配置持久化
 */
public class ServerProcessService {

    private static final Logger logger = LoggerFactory.getLogger(ServerProcessService.class);

    // 默认服务器路径
    private static final String DEFAULT_SERVER_PATH = "D:\\AionReal58\\AionServer";

    // 配置文件路径 - 保存到用户目录便于持久化
    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".lingyou");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("server_process_config.properties");

    // 服务器定义（名称 -> 显示名, exe名, 优先级, 启动顺序, 描述）
    private static final Map<String, ServerDefinition> SERVER_DEFINITIONS = new LinkedHashMap<>();

    static {
        // 按启动顺序定义所有服务器
        SERVER_DEFINITIONS.put("LogServer", new ServerDefinition(
            "日志服务器", "LogServer64.exe", StartupPriority.REQUIRED, 1,
            "收集所有服务器日志，推荐最先启动"));

        SERVER_DEFINITIONS.put("CacheD", new ServerDefinition(
            "缓存服务器", "CacheD64.exe", StartupPriority.REQUIRED, 2,
            "数据缓存服务，核心依赖"));

        SERVER_DEFINITIONS.put("L2AuthD", new ServerDefinition(
            "认证服务器", "L2AuthD.exe", StartupPriority.REQUIRED, 3,
            "玩家账号认证服务"));

        SERVER_DEFINITIONS.put("AuthGateD", new ServerDefinition(
            "认证网关", "AuthGateD.exe", StartupPriority.REQUIRED, 4,
            "认证请求路由网关"));

        SERVER_DEFINITIONS.put("AccountCacheServer", new ServerDefinition(
            "账号缓存服务器", "AccountCacheServer.exe", StartupPriority.REQUIRED, 5,
            "账号数据缓存"));

        SERVER_DEFINITIONS.put("MainServer", new ServerDefinition(
            "主游戏服务器", "Server64.exe", StartupPriority.REQUIRED, 6,
            "核心游戏逻辑服务器"));

        SERVER_DEFINITIONS.put("NPCServer", new ServerDefinition(
            "NPC服务器", "NPCSvr64.exe", StartupPriority.RECOMMENDED, 7,
            "NPC AI和行为处理"));

        SERVER_DEFINITIONS.put("ChatServer", new ServerDefinition(
            "聊天服务器", "ChannelChattingServer.exe", StartupPriority.RECOMMENDED, 8,
            "玩家聊天和频道管理"));

        SERVER_DEFINITIONS.put("GMServer", new ServerDefinition(
            "GM服务器", "GMServer.exe", StartupPriority.RECOMMENDED, 9,
            "GM命令和管理工具"));

        SERVER_DEFINITIONS.put("ICServer", new ServerDefinition(
            "IC服务器", "ICServer.exe", StartupPriority.OPTIONAL, 10,
            "跨服通信服务"));

        SERVER_DEFINITIONS.put("PAServer", new ServerDefinition(
            "PA服务器", "AionPAServer.exe", StartupPriority.OPTIONAL, 11,
            "付费/商城相关服务"));

        SERVER_DEFINITIONS.put("RankingServer", new ServerDefinition(
            "排名服务器", "RankingServer.exe", StartupPriority.OPTIONAL, 12,
            "玩家排名统计"));
    }

    // 服务器进程列表
    private final ObservableList<ServerProcess> serverProcesses = FXCollections.observableArrayList();

    // 服务器根目录
    private String serverRootPath = DEFAULT_SERVER_PATH;

    // 定时刷新执行器
    private ScheduledExecutorService refreshExecutor;

    // 状态变更监听器
    private Consumer<String> statusListener;

    public ServerProcessService() {
        loadConfig();
        scanServerProcesses();
    }

    public ServerProcessService(String serverPath) {
        this.serverRootPath = serverPath;
        loadConfig();
        scanServerProcesses();
    }

    /**
     * 获取服务器进程列表
     */
    public ObservableList<ServerProcess> getServerProcesses() {
        return serverProcesses;
    }

    /**
     * 获取服务器根目录
     */
    public String getServerRootPath() {
        return serverRootPath;
    }

    /**
     * 设置服务器根目录
     */
    public void setServerRootPath(String path) {
        this.serverRootPath = path;
        saveConfig();
        scanServerProcesses();
    }

    /**
     * 设置状态监听器
     */
    public void setStatusListener(Consumer<String> listener) {
        this.statusListener = listener;
    }

    /**
     * 扫描服务器目录，发现所有服务进程
     */
    public void scanServerProcesses() {
        serverProcesses.clear();

        Path rootPath = Paths.get(serverRootPath);
        if (!Files.exists(rootPath)) {
            notifyStatus("服务器目录不存在: " + serverRootPath);
            return;
        }

        // 按定义顺序添加服务器
        for (Map.Entry<String, ServerDefinition> entry : SERVER_DEFINITIONS.entrySet()) {
            String serverName = entry.getKey();
            ServerDefinition def = entry.getValue();

            // 查找exe文件
            String exePath = findExePath(rootPath, serverName, def.exeName);
            if (exePath != null) {
                ServerProcess process = new ServerProcess(serverName, def.displayName, exePath);
                process.setDescription(def.description);
                process.setPriority(def.priority);
                process.setStartupOrder(def.startupOrder);
                process.setStartupDelay(def.startupOrder > 1 ? 3 : 0); // 非第一个服务延迟3秒
                serverProcesses.add(process);
            }
        }

        // 扫描未定义的服务器（发现新增的）
        try (var stream = Files.list(rootPath)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                String dirName = dir.getFileName().toString();
                if (!SERVER_DEFINITIONS.containsKey(dirName)) {
                    // 尝试找到目录下的exe
                    try (var exeStream = Files.list(dir)) {
                        exeStream.filter(p -> p.toString().toLowerCase().endsWith(".exe"))
                            .findFirst()
                            .ifPresent(exeFile -> {
                                ServerProcess process = new ServerProcess(
                                    dirName,
                                    dirName + " (未识别)",
                                    exeFile.toString()
                                );
                                process.setPriority(StartupPriority.DISABLED);
                                process.setDescription("未在预设列表中的服务器");
                                process.setStartupOrder(99);
                                serverProcesses.add(process);
                            });
                    } catch (IOException e) {
                        logger.warn("扫描目录失败: {}", dir, e);
                    }
                }
            });
        } catch (IOException e) {
            logger.error("扫描服务器目录失败", e);
        }

        // 刷新状态
        refreshAllStatus();
        notifyStatus("发现 " + serverProcesses.size() + " 个服务器进程");
    }

    /**
     * 查找exe文件路径
     */
    private String findExePath(Path rootPath, String serverName, String exeName) {
        // 尝试标准路径
        Path standardPath = rootPath.resolve(serverName).resolve(exeName);
        if (Files.exists(standardPath)) {
            return standardPath.toString();
        }

        // 尝试不同的目录名映射
        Map<String, String> dirMapping = Map.of(
            "LogServer", "logServer",
            "CacheD", "Cached",
            "L2AuthD", "AuthD",
            "MainServer", "MainServer",
            "NPCServer", "NPCServer"
        );

        String altDir = dirMapping.get(serverName);
        if (altDir != null) {
            Path altPath = rootPath.resolve(altDir).resolve(exeName);
            if (Files.exists(altPath)) {
                return altPath.toString();
            }
        }

        // 递归搜索
        try (var stream = Files.walk(rootPath, 2)) {
            return stream
                .filter(p -> p.getFileName().toString().equalsIgnoreCase(exeName))
                .findFirst()
                .map(Path::toString)
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 刷新所有进程状态
     */
    public void refreshAllStatus() {
        // 获取所有运行中的进程
        Map<String, ProcessInfo> runningProcesses = getRunningProcesses();

        for (ServerProcess server : serverProcesses) {
            String processName = server.getProcessName().toLowerCase();
            ProcessInfo info = runningProcesses.get(processName);

            if (info != null) {
                server.setStatus(ProcessStatus.RUNNING);
                server.setPid(info.pid);
                server.setMemoryUsage(info.memoryKB * 1024L);
            } else {
                server.setStatus(ProcessStatus.STOPPED);
                server.setPid(-1);
                server.setMemoryUsage(0);
            }
        }
    }

    /**
     * 获取运行中的进程信息
     */
    private Map<String, ProcessInfo> getRunningProcesses() {
        Map<String, ProcessInfo> result = new HashMap<>();

        try {
            ProcessBuilder pb = new ProcessBuilder("tasklist", "/FO", "CSV", "/NH");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "GBK"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 格式: "进程名","PID","会话名","会话#","内存使用"
                    String[] parts = line.split("\",\"");
                    if (parts.length >= 5) {
                        String name = parts[0].replace("\"", "").toLowerCase();
                        try {
                            int pid = Integer.parseInt(parts[1].replace("\"", ""));
                            String memStr = parts[4].replace("\"", "")
                                .replace(",", "")
                                .replace(" K", "")
                                .replace(" ", "");
                            long memKB = Long.parseLong(memStr);
                            result.put(name, new ProcessInfo(pid, memKB));
                        } catch (NumberFormatException e) {
                            // 忽略解析错误
                        }
                    }
                }
            }
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("获取进程列表失败", e);
        }

        return result;
    }

    /**
     * 启动单个服务器
     */
    public CompletableFuture<Boolean> startServer(ServerProcess server) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (server.getStatus() == ProcessStatus.RUNNING) {
                    notifyStatus(server.getDisplayName() + " 已在运行中");
                    return true;
                }

                Platform.runLater(() -> server.setStatus(ProcessStatus.STARTING));
                notifyStatus("正在启动 " + server.getDisplayName() + "...");

                ProcessBuilder pb = new ProcessBuilder(server.getExePath());
                pb.directory(new File(server.getWorkingDir()));

                // 添加启动参数
                if (server.getStartupArgs() != null && !server.getStartupArgs().isEmpty()) {
                    pb.command().addAll(Arrays.asList(server.getStartupArgs().split("\\s+")));
                }

                pb.redirectErrorStream(true);
                Process process = pb.start();

                // 等待一小段时间确认启动
                Thread.sleep(2000);

                // 刷新状态
                refreshAllStatus();

                if (server.getStatus() == ProcessStatus.RUNNING) {
                    server.setLastStartTime(LocalDateTime.now());
                    notifyStatus(server.getDisplayName() + " 启动成功 (PID: " + server.getPid() + ")");
                    return true;
                } else {
                    Platform.runLater(() -> server.setStatus(ProcessStatus.ERROR));
                    notifyStatus(server.getDisplayName() + " 启动失败");
                    return false;
                }

            } catch (Exception e) {
                logger.error("启动服务器失败: " + server.getName(), e);
                Platform.runLater(() -> server.setStatus(ProcessStatus.ERROR));
                notifyStatus("启动失败: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * 停止单个服务器
     */
    public CompletableFuture<Boolean> stopServer(ServerProcess server) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (server.getStatus() != ProcessStatus.RUNNING) {
                    notifyStatus(server.getDisplayName() + " 未在运行");
                    return true;
                }

                Platform.runLater(() -> server.setStatus(ProcessStatus.STOPPING));
                notifyStatus("正在停止 " + server.getDisplayName() + "...");

                // 使用 taskkill 终止进程
                ProcessBuilder pb = new ProcessBuilder("taskkill", "/F", "/PID", String.valueOf(server.getPid()));
                Process process = pb.start();
                process.waitFor(10, TimeUnit.SECONDS);

                Thread.sleep(1000);
                refreshAllStatus();

                if (server.getStatus() == ProcessStatus.STOPPED) {
                    server.setLastStopTime(LocalDateTime.now());
                    notifyStatus(server.getDisplayName() + " 已停止");
                    return true;
                } else {
                    notifyStatus(server.getDisplayName() + " 停止失败");
                    return false;
                }

            } catch (Exception e) {
                logger.error("停止服务器失败: " + server.getName(), e);
                notifyStatus("停止失败: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * 批量启动所有必需和推荐的服务器
     */
    public CompletableFuture<Void> startAllServers() {
        return CompletableFuture.runAsync(() -> {
            notifyStatus("开始批量启动服务器...");

            // 按启动顺序排序
            List<ServerProcess> toStart = serverProcesses.stream()
                .filter(ServerProcess::shouldStart)
                .filter(s -> s.getStatus() != ProcessStatus.RUNNING)
                .sorted(Comparator.comparingInt(ServerProcess::getStartupOrder))
                .toList();

            int started = 0;
            for (ServerProcess server : toStart) {
                try {
                    boolean success = startServer(server).get(30, TimeUnit.SECONDS);
                    if (success) started++;

                    // 启动延迟
                    if (server.getStartupDelay() > 0) {
                        Thread.sleep(server.getStartupDelay() * 1000L);
                    }
                } catch (Exception e) {
                    logger.error("批量启动时出错: " + server.getName(), e);
                }
            }

            notifyStatus("批量启动完成: " + started + "/" + toStart.size() + " 个服务器");
        });
    }

    /**
     * 批量停止所有服务器
     */
    public CompletableFuture<Void> stopAllServers() {
        return CompletableFuture.runAsync(() -> {
            notifyStatus("开始批量停止服务器...");

            // 按启动顺序倒序停止
            List<ServerProcess> toStop = serverProcesses.stream()
                .filter(s -> s.getStatus() == ProcessStatus.RUNNING)
                .sorted(Comparator.comparingInt(ServerProcess::getStartupOrder).reversed())
                .toList();

            int stopped = 0;
            for (ServerProcess server : toStop) {
                try {
                    boolean success = stopServer(server).get(15, TimeUnit.SECONDS);
                    if (success) stopped++;
                } catch (Exception e) {
                    logger.error("批量停止时出错: " + server.getName(), e);
                }
            }

            notifyStatus("批量停止完成: " + stopped + "/" + toStop.size() + " 个服务器");
        });
    }

    /**
     * 启动自动刷新
     */
    public void startAutoRefresh(int intervalSeconds) {
        stopAutoRefresh();
        refreshExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ServerProcessRefresh");
            t.setDaemon(true);
            return t;
        });

        refreshExecutor.scheduleAtFixedRate(
            () -> Platform.runLater(this::refreshAllStatus),
            intervalSeconds,
            intervalSeconds,
            TimeUnit.SECONDS
        );
    }

    /**
     * 停止自动刷新
     */
    public void stopAutoRefresh() {
        if (refreshExecutor != null && !refreshExecutor.isShutdown()) {
            refreshExecutor.shutdown();
        }
    }

    /**
     * 保存配置到用户目录
     */
    public void saveConfig() {
        Properties props = new Properties();
        props.setProperty("serverRootPath", serverRootPath);

        for (ServerProcess server : serverProcesses) {
            String prefix = server.getName() + ".";
            props.setProperty(prefix + "priority", server.getPriority().name());
            props.setProperty(prefix + "startupOrder", String.valueOf(server.getStartupOrder()));
            props.setProperty(prefix + "startupDelay", String.valueOf(server.getStartupDelay()));
            props.setProperty(prefix + "startupArgs", server.getStartupArgs() != null ? server.getStartupArgs() : "");
            props.setProperty(prefix + "autoRestart", String.valueOf(server.isAutoRestart()));
        }

        try {
            // 确保配置目录存在
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            try (OutputStream out = Files.newOutputStream(CONFIG_FILE)) {
                props.store(out, "Aion Server Process Configuration - LingYou");
                logger.info("配置已保存到: {}", CONFIG_FILE);
            }
        } catch (IOException e) {
            logger.error("保存配置失败: {}", CONFIG_FILE, e);
        }
    }

    /**
     * 从用户目录加载配置
     */
    private void loadConfig() {
        if (!Files.exists(CONFIG_FILE)) {
            logger.info("配置文件不存在，使用默认配置: {}", CONFIG_FILE);
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
            props.load(in);
            serverRootPath = props.getProperty("serverRootPath", DEFAULT_SERVER_PATH);
            logger.info("已加载配置，服务器路径: {}", serverRootPath);
        } catch (IOException e) {
            logger.error("加载配置失败: {}", CONFIG_FILE, e);
        }
    }

    /**
     * 应用已保存的配置到服务器列表
     */
    public void applyLoadedConfig() {
        if (!Files.exists(CONFIG_FILE)) return;

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
            props.load(in);

            for (ServerProcess server : serverProcesses) {
                String prefix = server.getName() + ".";

                String priority = props.getProperty(prefix + "priority");
                if (priority != null) {
                    try {
                        server.setPriority(StartupPriority.valueOf(priority));
                    } catch (IllegalArgumentException ignored) {}
                }

                String order = props.getProperty(prefix + "startupOrder");
                if (order != null) {
                    server.setStartupOrder(Integer.parseInt(order));
                }

                String delay = props.getProperty(prefix + "startupDelay");
                if (delay != null) {
                    server.setStartupDelay(Integer.parseInt(delay));
                }

                String args = props.getProperty(prefix + "startupArgs");
                if (args != null) {
                    server.setStartupArgs(args);
                }

                String autoRestart = props.getProperty(prefix + "autoRestart");
                if (autoRestart != null) {
                    server.setAutoRestart(Boolean.parseBoolean(autoRestart));
                }
            }
        } catch (IOException e) {
            logger.error("应用配置失败", e);
        }
    }

    private void notifyStatus(String message) {
        logger.info(message);
        if (statusListener != null) {
            Platform.runLater(() -> statusListener.accept(message));
        }
    }

    /**
     * 清理资源
     */
    public void shutdown() {
        stopAutoRefresh();
        saveConfig();
    }

    // 内部类
    private record ServerDefinition(
        String displayName,
        String exeName,
        StartupPriority priority,
        int startupOrder,
        String description
    ) {}

    private record ProcessInfo(int pid, long memoryKB) {}
}
