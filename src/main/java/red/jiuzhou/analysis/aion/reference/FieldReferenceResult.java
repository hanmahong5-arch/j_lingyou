package red.jiuzhou.analysis.aion.reference;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 字段引用分析结果集合
 *
 * <p>封装一次分析的所有结果，提供多种视图和统计方法。
 *
 * @author Claude
 * @version 1.0
 */
public class FieldReferenceResult {

    /** 所有引用条目 */
    private final List<FieldReferenceEntry> entries = new ArrayList<>();

    /** 分析时间 */
    private LocalDateTime analysisTime;

    /** 分析耗时（毫秒） */
    private long analysisDuration;

    /** 分析的文件数 */
    private int analyzedFileCount;

    /** 错误信息（如果有） */
    private String error;

    // ========== 构造函数 ==========

    public FieldReferenceResult() {
        this.analysisTime = LocalDateTime.now();
    }

    // ========== 添加条目 ==========

    public void addEntry(FieldReferenceEntry entry) {
        entries.add(entry);
    }

    public void addAll(Collection<FieldReferenceEntry> entries) {
        this.entries.addAll(entries);
    }

    // ========== 基本统计 ==========

    /**
     * 获取总引用条目数
     */
    public int getTotalEntries() {
        return entries.size();
    }

    /**
     * 获取总引用数
     */
    public int getTotalReferences() {
        return entries.stream()
                .mapToInt(FieldReferenceEntry::getReferenceCount)
                .sum();
    }

    /**
     * 获取有效引用数
     */
    public int getTotalValidReferences() {
        return entries.stream()
                .mapToInt(FieldReferenceEntry::getValidReferences)
                .sum();
    }

    /**
     * 获取无效引用数
     */
    public int getTotalInvalidReferences() {
        return entries.stream()
                .mapToInt(FieldReferenceEntry::getInvalidReferences)
                .sum();
    }

    /**
     * 获取不同源文件数
     */
    public int getDistinctFileCount() {
        return (int) entries.stream()
                .map(FieldReferenceEntry::getSourceFile)
                .distinct()
                .count();
    }

    /**
     * 获取不同目标系统数
     */
    public int getDistinctSystemCount() {
        return (int) entries.stream()
                .map(FieldReferenceEntry::getTargetSystem)
                .distinct()
                .count();
    }

    // ========== 分组视图 ==========

    /**
     * 按目标系统分组
     */
    public Map<String, List<FieldReferenceEntry>> groupByTargetSystem() {
        return entries.stream()
                .collect(Collectors.groupingBy(
                        FieldReferenceEntry::getTargetSystem,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    /**
     * 按源文件分组
     */
    public Map<String, List<FieldReferenceEntry>> groupBySourceFile() {
        return entries.stream()
                .collect(Collectors.groupingBy(
                        FieldReferenceEntry::getSourceFileName,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    /**
     * 按源机制分组
     */
    public Map<String, List<FieldReferenceEntry>> groupBySourceMechanism() {
        return entries.stream()
                .filter(e -> e.getSourceMechanism() != null)
                .collect(Collectors.groupingBy(
                        FieldReferenceEntry::getSourceMechanism,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    // ========== 过滤方法 ==========

    /**
     * 获取有无效引用的条目
     */
    public List<FieldReferenceEntry> getInvalidEntries() {
        return entries.stream()
                .filter(FieldReferenceEntry::hasInvalidReferences)
                .collect(Collectors.toList());
    }

    /**
     * 按目标系统过滤
     */
    public List<FieldReferenceEntry> filterByTargetSystem(String targetSystem) {
        return entries.stream()
                .filter(e -> targetSystem.equals(e.getTargetSystem()))
                .collect(Collectors.toList());
    }

    /**
     * 按源文件过滤
     */
    public List<FieldReferenceEntry> filterBySourceFile(String fileName) {
        return entries.stream()
                .filter(e -> e.getSourceFileName().contains(fileName))
                .collect(Collectors.toList());
    }

    /**
     * 按源机制过滤
     */
    public List<FieldReferenceEntry> filterByMechanism(String mechanism) {
        return entries.stream()
                .filter(e -> mechanism.equals(e.getSourceMechanism()))
                .collect(Collectors.toList());
    }

    // ========== 排序方法 ==========

    /**
     * 按引用数降序排列
     */
    public List<FieldReferenceEntry> sortByReferenceCount() {
        return entries.stream()
                .sorted(Comparator.comparingInt(FieldReferenceEntry::getReferenceCount).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 按无效引用数降序排列
     */
    public List<FieldReferenceEntry> sortByInvalidCount() {
        return entries.stream()
                .sorted(Comparator.comparingInt(FieldReferenceEntry::getInvalidReferences).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 按有效率升序排列（问题优先）
     */
    public List<FieldReferenceEntry> sortByValidRate() {
        return entries.stream()
                .sorted(Comparator.comparingDouble(FieldReferenceEntry::getValidRate))
                .collect(Collectors.toList());
    }

    // ========== 统计信息 ==========

    /**
     * 获取目标系统统计
     */
    public List<SystemStats> getSystemStats() {
        Map<String, SystemStats> statsMap = new LinkedHashMap<>();

        for (FieldReferenceEntry entry : entries) {
            String system = entry.getTargetSystem();
            SystemStats stats = statsMap.computeIfAbsent(system, SystemStats::new);
            stats.addEntry(entry);
        }

        List<SystemStats> result = new ArrayList<>(statsMap.values());
        result.sort(Comparator.comparingInt(SystemStats::getTotalReferences).reversed());
        return result;
    }

    /**
     * 获取机制统计
     */
    public List<MechanismStats> getMechanismStats() {
        Map<String, MechanismStats> statsMap = new LinkedHashMap<>();

        for (FieldReferenceEntry entry : entries) {
            String mechanism = entry.getSourceMechanism();
            if (mechanism != null) {
                MechanismStats stats = statsMap.computeIfAbsent(mechanism, MechanismStats::new);
                stats.addEntry(entry);
            }
        }

        List<MechanismStats> result = new ArrayList<>(statsMap.values());
        result.sort(Comparator.comparingInt(MechanismStats::getTotalReferences).reversed());
        return result;
    }

    // ========== 内部统计类 ==========

    /**
     * 目标系统统计
     */
    public static class SystemStats {
        private final String systemName;
        private int entryCount;
        private int totalReferences;
        private int validReferences;
        private int invalidReferences;
        private final Set<String> sourceFiles = new LinkedHashSet<>();

        public SystemStats(String systemName) {
            this.systemName = systemName;
        }

        void addEntry(FieldReferenceEntry entry) {
            entryCount++;
            totalReferences += entry.getReferenceCount();
            validReferences += entry.getValidReferences();
            invalidReferences += entry.getInvalidReferences();
            sourceFiles.add(entry.getSourceFileName());
        }

        public String getSystemName() {
            return systemName;
        }

        public int getEntryCount() {
            return entryCount;
        }

        public int getTotalReferences() {
            return totalReferences;
        }

        public int getValidReferences() {
            return validReferences;
        }

        public int getInvalidReferences() {
            return invalidReferences;
        }

        public int getSourceFileCount() {
            return sourceFiles.size();
        }

        public double getValidRate() {
            int total = validReferences + invalidReferences;
            return total > 0 ? (double) validReferences / total : 1.0;
        }
    }

    /**
     * 机制统计
     */
    public static class MechanismStats {
        private final String mechanismName;
        private int entryCount;
        private int totalReferences;
        private final Set<String> targetSystems = new LinkedHashSet<>();
        private final Set<String> sourceFiles = new LinkedHashSet<>();

        public MechanismStats(String mechanismName) {
            this.mechanismName = mechanismName;
        }

        void addEntry(FieldReferenceEntry entry) {
            entryCount++;
            totalReferences += entry.getReferenceCount();
            targetSystems.add(entry.getTargetSystem());
            sourceFiles.add(entry.getSourceFileName());
        }

        public String getMechanismName() {
            return mechanismName;
        }

        public int getEntryCount() {
            return entryCount;
        }

        public int getTotalReferences() {
            return totalReferences;
        }

        public int getTargetSystemCount() {
            return targetSystems.size();
        }

        public int getSourceFileCount() {
            return sourceFiles.size();
        }

        public Set<String> getTargetSystems() {
            return Collections.unmodifiableSet(targetSystems);
        }
    }

    // ========== Getters and Setters ==========

    public List<FieldReferenceEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public LocalDateTime getAnalysisTime() {
        return analysisTime;
    }

    public void setAnalysisTime(LocalDateTime analysisTime) {
        this.analysisTime = analysisTime;
    }

    public long getAnalysisDuration() {
        return analysisDuration;
    }

    public void setAnalysisDuration(long analysisDuration) {
        this.analysisDuration = analysisDuration;
    }

    public int getAnalyzedFileCount() {
        return analyzedFileCount;
    }

    public void setAnalyzedFileCount(int analyzedFileCount) {
        this.analyzedFileCount = analyzedFileCount;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public boolean hasError() {
        return error != null;
    }

    /**
     * 获取分析摘要
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("分析完成: ");
        sb.append(getDistinctFileCount()).append("个文件, ");
        sb.append(getTotalEntries()).append("个字段, ");
        sb.append(getTotalReferences()).append("个引用");

        int invalid = getTotalInvalidReferences();
        if (invalid > 0) {
            sb.append(" (").append(invalid).append("个无效)");
        }

        sb.append(", 耗时").append(analysisDuration).append("ms");
        return sb.toString();
    }
}
