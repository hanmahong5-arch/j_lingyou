package red.jiuzhou.util.game;

import org.dom4j.*;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 刷怪配置编辑器 - 支持幂等性修改和保存
 *
 * 核心特性：
 * 1. 基于唯一键的UPSERT操作
 * 2. 原子性文件保存（先写临时文件，再原子替换）
 * 3. 文件级并发控制
 * 4. 操作审计日志
 * 5. 自动备份和回滚
 *
 * @author yanxq
 * @date 2025-01-19
 */
public class WorldSpawnEditor {

    private static final Logger log = LoggerFactory.getLogger(WorldSpawnEditor.class);

    private final WorldSpawnService spawnService;

    /** 文件锁映射（防止并发修改） */
    private final ConcurrentHashMap<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();

    /** 备份目录 */
    private static final String BACKUP_DIR = "world_backups/";

    /** 审计日志 */
    private final SpawnEditLogger auditLogger = new SpawnEditLogger();

    public WorldSpawnEditor(WorldSpawnService spawnService) {
        this.spawnService = spawnService;
    }

    /**
     * 添加或更新刷怪区域（幂等）
     *
     * @param mapName 地图名称
     * @param territory 刷怪区域
     * @return 操作结果（CREATED/UPDATED/NO_CHANGE）
     */
    public OperationResult upsertTerritory(String mapName, SpawnTerritory territory) {
        Lock lock = fileLocks.computeIfAbsent(mapName, k -> new ReentrantLock());
        lock.lock();
        try {
            // 1. 加载现有数据
            List<SpawnTerritory> existing = spawnService.loadMapSpawns(mapName);

            // 2. 查找是否已存在（基于区域名）
            Optional<SpawnTerritory> found = existing.stream()
                .filter(t -> t.getName() != null && t.getName().equals(territory.getName()))
                .findFirst();

            if (found.isPresent()) {
                SpawnTerritory oldTerritory = found.get();

                // 3. 检查是否有实际变化
                if (isTerritoryEqual(oldTerritory, territory)) {
                    auditLogger.log("NO_CHANGE", mapName, territory.getName(), "配置未变化");
                    return new OperationResult(OperationStatus.NO_CHANGE, oldTerritory, "配置未变化");
                }

                // 4. 执行更新
                replaceTerritoryInXml(mapName, territory);
                auditLogger.log("UPDATE", mapName, territory.getName(), "更新刷怪区域");
                return new OperationResult(OperationStatus.UPDATED, territory, "更新成功");
            } else {
                // 5. 执行新增
                addTerritoryToXml(mapName, territory);
                auditLogger.log("CREATE", mapName, territory.getName(), "新增刷怪区域");
                return new OperationResult(OperationStatus.CREATED, territory, "创建成功");
            }

        } catch (Exception e) {
            log.error("UPSERT操作失败: " + e.getMessage(), e);
            auditLogger.log("ERROR", mapName, territory.getName(), "操作失败: " + e.getMessage());
            return new OperationResult(OperationStatus.ERROR, territory, "操作失败: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 删除刷怪区域（幂等）
     */
    public OperationResult deleteTerritory(String mapName, String territoryName) {
        Lock lock = fileLocks.computeIfAbsent(mapName, k -> new ReentrantLock());
        lock.lock();
        try {
            List<SpawnTerritory> existing = spawnService.loadMapSpawns(mapName);
            Optional<SpawnTerritory> found = existing.stream()
                .filter(t -> t.getName() != null && t.getName().equals(territoryName))
                .findFirst();

            if (found.isPresent()) {
                removeTerritoryFromXml(mapName, territoryName);
                auditLogger.log("DELETE", mapName, territoryName, "删除刷怪区域");
                return new OperationResult(OperationStatus.DELETED, found.get(), "删除成功");
            } else {
                // 不存在也返回成功（幂等性）
                auditLogger.log("NO_CHANGE", mapName, territoryName, "区域不存在，无需删除");
                return new OperationResult(OperationStatus.NO_CHANGE, null, "区域不存在");
            }
        } catch (Exception e) {
            log.error("DELETE操作失败: " + e.getMessage(), e);
            return new OperationResult(OperationStatus.ERROR, null, "删除失败: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 批量导入刷怪区域（幂等）
     */
    public BatchOperationResult batchUpsert(String mapName, List<SpawnTerritory> territories) {
        Lock lock = fileLocks.computeIfAbsent(mapName, k -> new ReentrantLock());
        lock.lock();
        try {
            int created = 0, updated = 0, noChange = 0, errors = 0;
            List<String> errorMessages = new ArrayList<>();

            for (SpawnTerritory territory : territories) {
                try {
                    OperationResult result = upsertTerritory(mapName, territory);
                    switch (result.getStatus()) {
                        case CREATED: created++; break;
                        case UPDATED: updated++; break;
                        case NO_CHANGE: noChange++; break;
                        case ERROR: errors++; errorMessages.add(result.getMessage()); break;
                    }
                } catch (Exception e) {
                    errors++;
                    errorMessages.add(territory.getName() + ": " + e.getMessage());
                }
            }

            return new BatchOperationResult(created, updated, noChange, errors, errorMessages);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 添加刷怪区域到XML
     */
    private void addTerritoryToXml(String mapName, SpawnTerritory territory) throws Exception {
        WorldSpawnService.MapInfo mapInfo = getMapInfo(mapName);
        String xmlPath = mapInfo.getWorldNPath();

        // 读取XML
        Document document = readXmlDocument(xmlPath);
        Element root = document.getRootElement();
        Element npcSpawn = root.element("npc_spawn");

        if (npcSpawn == null) {
            throw new IllegalStateException("未找到npc_spawn元素");
        }

        // 转换Territory为XML Element
        Element territoryEl = territoryToElement(territory, document);
        npcSpawn.add(territoryEl);

        // 保存XML
        saveXmlToFile(mapName, document);
    }

    /**
     * 替换XML中的刷怪区域
     */
    private void replaceTerritoryInXml(String mapName, SpawnTerritory territory) throws Exception {
        WorldSpawnService.MapInfo mapInfo = getMapInfo(mapName);
        String xmlPath = mapInfo.getWorldNPath();

        // 读取XML
        Document document = readXmlDocument(xmlPath);
        Element root = document.getRootElement();
        Element npcSpawn = root.element("npc_spawn");

        if (npcSpawn == null) {
            throw new IllegalStateException("未找到npc_spawn元素");
        }

        // 查找并替换
        List<Element> territories = npcSpawn.elements("territory");
        for (int i = 0; i < territories.size(); i++) {
            Element el = territories.get(i);
            Element nameEl = el.element("name");
            if (nameEl != null && territory.getName().equals(nameEl.getTextTrim())) {
                // 找到了，替换
                Element newEl = territoryToElement(territory, document);
                territories.set(i, newEl);
                npcSpawn.content().set(npcSpawn.content().indexOf(el), newEl);
                break;
            }
        }

        // 保存XML
        saveXmlToFile(mapName, document);
    }

    /**
     * 从XML中移除刷怪区域
     */
    private void removeTerritoryFromXml(String mapName, String territoryName) throws Exception {
        WorldSpawnService.MapInfo mapInfo = getMapInfo(mapName);
        String xmlPath = mapInfo.getWorldNPath();

        // 读取XML
        Document document = readXmlDocument(xmlPath);
        Element root = document.getRootElement();
        Element npcSpawn = root.element("npc_spawn");

        if (npcSpawn == null) {
            throw new IllegalStateException("未找到npc_spawn元素");
        }

        // 查找并删除
        List<Element> territories = npcSpawn.elements("territory");
        for (Element el : territories) {
            Element nameEl = el.element("name");
            if (nameEl != null && territoryName.equals(nameEl.getTextTrim())) {
                npcSpawn.remove(el);
                break;
            }
        }

        // 保存XML
        saveXmlToFile(mapName, document);
    }

    /**
     * 读取XML文档
     */
    private Document readXmlDocument(String xmlPath) throws Exception {
        File file = new File(xmlPath);
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), "UTF-16")) {
            SAXReader saxReader = new SAXReader();
            return saxReader.read(reader);
        }
    }

    /**
     * 保存XML到文件（原子性操作）
     *
     * 流程：
     * 1. 创建备份
     * 2. 写入临时文件
     * 3. 验证XML格式
     * 4. 原子替换原文件
     */
    private void saveXmlToFile(String mapName, Document document) throws IOException {
        WorldSpawnService.MapInfo mapInfo = getMapInfo(mapName);
        Path xmlPath = Paths.get(mapInfo.getWorldNPath());
        Path tempPath = Paths.get(xmlPath.toString() + ".tmp");
        Path backupPath = null;

        try {
            // 1. 创建备份
            backupPath = createBackup(xmlPath);
            log.info("创建备份: {}", backupPath);

            // 2. 写入临时文件（UTF-16编码）
            OutputFormat format = OutputFormat.createPrettyPrint();
            format.setEncoding("UTF-16");
            format.setIndent(true);
            format.setIndentSize(2);

            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(tempPath.toFile()), "UTF-16")) {
                XMLWriter xmlWriter = new XMLWriter(writer, format);
                xmlWriter.write(document);
                xmlWriter.close();
            }

            // 3. 验证临时文件可读
            SAXReader reader = new SAXReader();
            try (InputStreamReader isr = new InputStreamReader(
                    new FileInputStream(tempPath.toFile()), "UTF-16")) {
                reader.read(isr); // 验证XML格式正确
            }

            // 4. 原子替换
            Files.move(tempPath, xmlPath, StandardCopyOption.REPLACE_EXISTING);

            // 5. 清除缓存
            spawnService.clearMapCache(mapName);

            log.info("保存成功: {}", xmlPath);

        } catch (Exception e) {
            // 失败时从备份恢复
            if (backupPath != null && Files.exists(backupPath)) {
                log.warn("保存失败，从备份恢复: {}", backupPath);
                Files.copy(backupPath, xmlPath, StandardCopyOption.REPLACE_EXISTING);
            }
            throw new IOException("保存XML失败: " + e.getMessage(), e);
        } finally {
            // 清理临时文件
            if (Files.exists(tempPath)) {
                Files.delete(tempPath);
            }
        }
    }

    /**
     * 创建备份文件
     */
    private Path createBackup(Path xmlPath) throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        Path backupDir = Paths.get(BACKUP_DIR);
        if (!Files.exists(backupDir)) {
            Files.createDirectories(backupDir);
        }

        String fileName = xmlPath.getFileName().toString();
        String mapName = xmlPath.getParent().getFileName().toString();
        Path backupPath = backupDir.resolve(mapName + "_" + fileName + "." + timestamp + ".bak");

        Files.copy(xmlPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        return backupPath;
    }

    /**
     * 将SpawnTerritory转换为XML Element
     */
    private Element territoryToElement(SpawnTerritory territory, Document document) {
        Element territoryEl = DocumentHelper.createElement("territory");

        // 设置属性
        if (territory.isMoveInTerritory()) {
            territoryEl.addAttribute("MoveIn_Territory", "TRUE");
        }
        if (territory.isNoRespawn()) {
            territoryEl.addAttribute("no_respawn", "TRUE");
        }
        if (territory.isGeneratePathfind()) {
            territoryEl.addAttribute("Generate_Pathfind", "TRUE");
        }
        if (territory.isAerialSpawn()) {
            territoryEl.addAttribute("Aerial_Spawn", "TRUE");
        }
        if (territory.getSpawnVersion() != 0) {
            territoryEl.addAttribute("spawn_version", String.valueOf(territory.getSpawnVersion()));
        }
        if (territory.getSpawnCountry() != -1) {
            territoryEl.addAttribute("spawn_country", String.valueOf(territory.getSpawnCountry()));
        }
        if (territory.getSensoryGroupId() != 0) {
            territoryEl.addAttribute("sensory_group_id", String.valueOf(territory.getSensoryGroupId()));
        }
        if (territory.getBroadcastGroupId() != 0) {
            territoryEl.addAttribute("broadcast_group_id", String.valueOf(territory.getBroadcastGroupId()));
        }

        // 添加子元素
        if (territory.getName() != null) {
            territoryEl.addElement("name").setText(territory.getName());
        }
        if (territory.getWeatherZoneName() != null) {
            territoryEl.addElement("weather_zone_name").setText(territory.getWeatherZoneName());
        }

        // 添加points_info
        if (!territory.getMoveAreaPoints().isEmpty() || !territory.getSpawnPoints().isEmpty()) {
            Element pointsInfo = territoryEl.addElement("points_info");

            // move_area_points
            if (!territory.getMoveAreaPoints().isEmpty()) {
                Element moveAreaPoints = pointsInfo.addElement("move_area_points");
                for (double[] point : territory.getMoveAreaPoints()) {
                    Element data = moveAreaPoints.addElement("data");
                    data.addElement("x").setText(String.valueOf(point[0]));
                    data.addElement("y").setText(String.valueOf(point[1]));
                }
            }

            // spawn points
            if (!territory.getSpawnPoints().isEmpty()) {
                Element points = pointsInfo.addElement("points");
                for (SpawnTerritory.SpawnPoint sp : territory.getSpawnPoints()) {
                    Element data = points.addElement("data");
                    data.addElement("x").setText(String.valueOf(sp.getX()));
                    data.addElement("y").setText(String.valueOf(sp.getY()));
                    data.addElement("z").setText(String.valueOf(sp.getZ()));
                    data.addElement("moveareaindex").setText(String.valueOf(sp.getMoveAreaIndex()));
                }
            }

            // checksurfacez
            if (territory.getCheckSurfaceZ() != 0) {
                pointsInfo.addElement("checksurfacez").setText(String.valueOf(territory.getCheckSurfaceZ()));
            }
        }

        // 添加NPCs
        if (!territory.getNpcs().isEmpty()) {
            Element npcsEl = territoryEl.addElement("npcs");
            for (SpawnTerritory.SpawnNpc npc : territory.getNpcs()) {
                Element npcEl = npcsEl.addElement("npc");
                if (npc.getSelectProb() != -1) {
                    npcEl.addAttribute("select_prob", String.valueOf(npc.getSelectProb()));
                }
                if (npc.getName() != null) {
                    npcEl.addElement("name").setText(npc.getName());
                }
                npcEl.addElement("spawn_time").setText(String.valueOf(npc.getSpawnTime()));
                if (npc.getSpawnTimeEx() != 0) {
                    npcEl.addElement("spawn_time_ex").setText(String.valueOf(npc.getSpawnTimeEx()));
                }
                npcEl.addElement("count").setText(String.valueOf(npc.getCount()));
                npcEl.addElement("initial_spawn_time").setText(String.valueOf(npc.getInitialSpawnTime()));
                npcEl.addElement("initial_spawn_time_ex").setText(String.valueOf(npc.getInitialSpawnTimeEx()));
                npcEl.addElement("initial_spawn_count").setText(String.valueOf(npc.getInitialSpawnCount()));
                if (npc.getIdleLiveRange() != 0) {
                    npcEl.addElement("idle_live_range").setText(String.valueOf(npc.getIdleLiveRange()));
                }
                if (npc.isDespawnAtAttackState()) {
                    npcEl.addElement("despawn_at_attack_state").setText("TRUE");
                }
            }
        }

        return territoryEl;
    }

    /**
     * 比较两个Territory是否相等（用于检测变化）
     */
    private boolean isTerritoryEqual(SpawnTerritory a, SpawnTerritory b) {
        if (!Objects.equals(a.getName(), b.getName())) return false;
        if (a.isNoRespawn() != b.isNoRespawn()) return false;
        if (a.isAerialSpawn() != b.isAerialSpawn()) return false;
        if (a.isGeneratePathfind() != b.isGeneratePathfind()) return false;
        if (a.getSpawnVersion() != b.getSpawnVersion()) return false;
        if (a.getSpawnCountry() != b.getSpawnCountry()) return false;

        // 比较NPC列表
        if (a.getNpcs().size() != b.getNpcs().size()) return false;
        for (int i = 0; i < a.getNpcs().size(); i++) {
            if (!isNpcEqual(a.getNpcs().get(i), b.getNpcs().get(i))) {
                return false;
            }
        }

        // 比较刷怪点
        if (a.getSpawnPoints().size() != b.getSpawnPoints().size()) return false;
        for (int i = 0; i < a.getSpawnPoints().size(); i++) {
            SpawnTerritory.SpawnPoint spa = a.getSpawnPoints().get(i);
            SpawnTerritory.SpawnPoint spb = b.getSpawnPoints().get(i);
            if (Math.abs(spa.getX() - spb.getX()) > 0.01 ||
                Math.abs(spa.getY() - spb.getY()) > 0.01 ||
                Math.abs(spa.getZ() - spb.getZ()) > 0.01) {
                return false;
            }
        }

        return true;
    }

    private boolean isNpcEqual(SpawnTerritory.SpawnNpc a, SpawnTerritory.SpawnNpc b) {
        return Objects.equals(a.getName(), b.getName())
            && a.getCount() == b.getCount()
            && a.getSpawnTime() == b.getSpawnTime()
            && a.getSpawnTimeEx() == b.getSpawnTimeEx()
            && a.getInitialSpawnTime() == b.getInitialSpawnTime()
            && a.getInitialSpawnTimeEx() == b.getInitialSpawnTimeEx()
            && a.getInitialSpawnCount() == b.getInitialSpawnCount();
    }

    /**
     * 获取地图信息
     */
    private WorldSpawnService.MapInfo getMapInfo(String mapName) {
        List<WorldSpawnService.MapInfo> maps = spawnService.getAvailableMaps();
        return maps.stream()
            .filter(m -> m.getName().equals(mapName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("地图不存在: " + mapName));
    }

    /**
     * 获取最近的备份列表
     */
    public List<Path> getBackupList(String mapName) throws IOException {
        Path backupDir = Paths.get(BACKUP_DIR);
        if (!Files.exists(backupDir)) {
            return Collections.emptyList();
        }

        List<Path> backups = new ArrayList<>();
        Files.list(backupDir)
            .filter(p -> p.getFileName().toString().startsWith(mapName + "_"))
            .sorted(Comparator.comparing(Path::toString).reversed())
            .limit(10)
            .forEach(backups::add);

        return backups;
    }

    /**
     * 从备份恢复
     */
    public void restoreFromBackup(String mapName, Path backupPath) throws IOException {
        WorldSpawnService.MapInfo mapInfo = getMapInfo(mapName);
        Path xmlPath = Paths.get(mapInfo.getWorldNPath());

        Files.copy(backupPath, xmlPath, StandardCopyOption.REPLACE_EXISTING);
        spawnService.clearMapCache(mapName);

        log.info("从备份恢复成功: {} -> {}", backupPath, xmlPath);
        auditLogger.log("RESTORE", mapName, backupPath.getFileName().toString(), "从备份恢复");
    }

    /**
     * 操作状态枚举
     */
    public enum OperationStatus {
        CREATED,   // 新创建
        UPDATED,   // 已更新
        DELETED,   // 已删除
        NO_CHANGE, // 无变化（幂等）
        ERROR      // 错误
    }

    /**
     * 操作结果
     */
    public static class OperationResult {
        private final OperationStatus status;
        private final SpawnTerritory territory;
        private final String message;

        public OperationResult(OperationStatus status, SpawnTerritory territory, String message) {
            this.status = status;
            this.territory = territory;
            this.message = message;
        }

        public OperationStatus getStatus() { return status; }
        public SpawnTerritory getTerritory() { return territory; }
        public String getMessage() { return message; }

        @Override
        public String toString() {
            return String.format("%s: %s", status, message);
        }
    }

    /**
     * 批量操作结果
     */
    public static class BatchOperationResult {
        private final int created;
        private final int updated;
        private final int noChange;
        private final int errors;
        private final List<String> errorMessages;

        public BatchOperationResult(int created, int updated, int noChange, int errors, List<String> errorMessages) {
            this.created = created;
            this.updated = updated;
            this.noChange = noChange;
            this.errors = errors;
            this.errorMessages = errorMessages;
        }

        public int getCreated() { return created; }
        public int getUpdated() { return updated; }
        public int getNoChange() { return noChange; }
        public int getErrors() { return errors; }
        public List<String> getErrorMessages() { return errorMessages; }

        public String getSummary() {
            return String.format("创建: %d, 更新: %d, 无变化: %d, 错误: %d",
                created, updated, noChange, errors);
        }

        @Override
        public String toString() {
            return getSummary();
        }
    }
}
