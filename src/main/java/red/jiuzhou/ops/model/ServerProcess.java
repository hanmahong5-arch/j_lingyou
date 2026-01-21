package red.jiuzhou.ops.model;

import javafx.beans.property.*;

/**
 * Aion 服务器进程模型
 *
 * 用于管理和监控游戏服务器各组件的运行状态
 */
public class ServerProcess {

    /**
     * 进程启动优先级/必要性
     */
    public enum StartupPriority {
        REQUIRED("必需", "Core service, must be running", 1),
        RECOMMENDED("推荐", "Recommended for full functionality", 2),
        OPTIONAL("可选", "Optional, can be disabled", 3),
        DISABLED("禁用", "Manually disabled by admin", 4);

        private final String label;
        private final String description;
        private final int order;

        StartupPriority(String label, String description, int order) {
            this.label = label;
            this.description = description;
            this.order = order;
        }

        public String getLabel() { return label; }
        public String getDescription() { return description; }
        public int getOrder() { return order; }
    }

    /**
     * 进程运行状态
     */
    public enum ProcessStatus {
        RUNNING("运行中", "🟢", "-fx-text-fill: #4CAF50;"),
        STOPPED("已停止", "🔴", "-fx-text-fill: #f44336;"),
        STARTING("启动中", "🟡", "-fx-text-fill: #FF9800;"),
        STOPPING("停止中", "🟠", "-fx-text-fill: #FF5722;"),
        ERROR("错误", "❌", "-fx-text-fill: #9C27B0;"),
        UNKNOWN("未知", "⚪", "-fx-text-fill: #9E9E9E;");

        private final String label;
        private final String icon;
        private final String style;

        ProcessStatus(String label, String icon, String style) {
            this.label = label;
            this.icon = icon;
            this.style = style;
        }

        public String getLabel() { return label; }
        public String getIcon() { return icon; }
        public String getStyle() { return style; }
    }

    // 基本信息
    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty displayName = new SimpleStringProperty();
    private final StringProperty description = new SimpleStringProperty();
    private final StringProperty exePath = new SimpleStringProperty();
    private final StringProperty workingDir = new SimpleStringProperty();
    private final StringProperty processName = new SimpleStringProperty(); // 用于进程匹配

    // 状态信息
    private final ObjectProperty<ProcessStatus> status = new SimpleObjectProperty<>(ProcessStatus.UNKNOWN);
    private final ObjectProperty<StartupPriority> priority = new SimpleObjectProperty<>(StartupPriority.OPTIONAL);
    private final IntegerProperty pid = new SimpleIntegerProperty(-1);
    private final LongProperty memoryUsage = new SimpleLongProperty(0);
    private final DoubleProperty cpuUsage = new SimpleDoubleProperty(0);

    // 启动配置
    private final StringProperty startupArgs = new SimpleStringProperty("");
    private final IntegerProperty startupOrder = new SimpleIntegerProperty(0);
    private final IntegerProperty startupDelay = new SimpleIntegerProperty(0); // 启动延迟(秒)
    private final BooleanProperty autoRestart = new SimpleBooleanProperty(false);

    // 时间戳
    private final ObjectProperty<java.time.LocalDateTime> lastStartTime = new SimpleObjectProperty<>();
    private final ObjectProperty<java.time.LocalDateTime> lastStopTime = new SimpleObjectProperty<>();

    public ServerProcess() {}

    public ServerProcess(String name, String displayName, String exePath) {
        this.name.set(name);
        this.displayName.set(displayName);
        this.exePath.set(exePath);

        // 从exe路径提取工作目录和进程名
        java.io.File file = new java.io.File(exePath);
        this.workingDir.set(file.getParent());
        this.processName.set(file.getName());
    }

    // Getters and Setters with Property accessors

    public String getName() { return name.get(); }
    public void setName(String value) { name.set(value); }
    public StringProperty nameProperty() { return name; }

    public String getDisplayName() { return displayName.get(); }
    public void setDisplayName(String value) { displayName.set(value); }
    public StringProperty displayNameProperty() { return displayName; }

    public String getDescription() { return description.get(); }
    public void setDescription(String value) { description.set(value); }
    public StringProperty descriptionProperty() { return description; }

    public String getExePath() { return exePath.get(); }
    public void setExePath(String value) { exePath.set(value); }
    public StringProperty exePathProperty() { return exePath; }

    public String getWorkingDir() { return workingDir.get(); }
    public void setWorkingDir(String value) { workingDir.set(value); }
    public StringProperty workingDirProperty() { return workingDir; }

    public String getProcessName() { return processName.get(); }
    public void setProcessName(String value) { processName.set(value); }
    public StringProperty processNameProperty() { return processName; }

    public ProcessStatus getStatus() { return status.get(); }
    public void setStatus(ProcessStatus value) { status.set(value); }
    public ObjectProperty<ProcessStatus> statusProperty() { return status; }

    public StartupPriority getPriority() { return priority.get(); }
    public void setPriority(StartupPriority value) { priority.set(value); }
    public ObjectProperty<StartupPriority> priorityProperty() { return priority; }

    public int getPid() { return pid.get(); }
    public void setPid(int value) { pid.set(value); }
    public IntegerProperty pidProperty() { return pid; }

    public long getMemoryUsage() { return memoryUsage.get(); }
    public void setMemoryUsage(long value) { memoryUsage.set(value); }
    public LongProperty memoryUsageProperty() { return memoryUsage; }

    public double getCpuUsage() { return cpuUsage.get(); }
    public void setCpuUsage(double value) { cpuUsage.set(value); }
    public DoubleProperty cpuUsageProperty() { return cpuUsage; }

    public String getStartupArgs() { return startupArgs.get(); }
    public void setStartupArgs(String value) { startupArgs.set(value); }
    public StringProperty startupArgsProperty() { return startupArgs; }

    public int getStartupOrder() { return startupOrder.get(); }
    public void setStartupOrder(int value) { startupOrder.set(value); }
    public IntegerProperty startupOrderProperty() { return startupOrder; }

    public int getStartupDelay() { return startupDelay.get(); }
    public void setStartupDelay(int value) { startupDelay.set(value); }
    public IntegerProperty startupDelayProperty() { return startupDelay; }

    public boolean isAutoRestart() { return autoRestart.get(); }
    public void setAutoRestart(boolean value) { autoRestart.set(value); }
    public BooleanProperty autoRestartProperty() { return autoRestart; }

    public java.time.LocalDateTime getLastStartTime() { return lastStartTime.get(); }
    public void setLastStartTime(java.time.LocalDateTime value) { lastStartTime.set(value); }
    public ObjectProperty<java.time.LocalDateTime> lastStartTimeProperty() { return lastStartTime; }

    public java.time.LocalDateTime getLastStopTime() { return lastStopTime.get(); }
    public void setLastStopTime(java.time.LocalDateTime value) { lastStopTime.set(value); }
    public ObjectProperty<java.time.LocalDateTime> lastStopTimeProperty() { return lastStopTime; }

    /**
     * 判断进程是否正在运行
     */
    public boolean isRunning() {
        return status.get() == ProcessStatus.RUNNING;
    }

    /**
     * 判断是否应该启动（根据优先级）
     */
    public boolean shouldStart() {
        return priority.get() != StartupPriority.DISABLED;
    }

    /**
     * 格式化内存使用量
     */
    public String getFormattedMemoryUsage() {
        long bytes = memoryUsage.get();
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    @Override
    public String toString() {
        return String.format("ServerProcess[%s, status=%s, pid=%d]",
            displayName.get(), status.get().getLabel(), pid.get());
    }
}
