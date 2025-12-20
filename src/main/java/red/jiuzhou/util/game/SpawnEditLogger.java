package red.jiuzhou.util.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 刷怪编辑操作审计日志
 *
 * 记录所有刷怪配置的修改操作，包括：
 * - 操作类型（CREATE/UPDATE/DELETE等）
 * - 操作时间
 * - 地图名称
 * - 区域名称
 * - 详细信息
 *
 * @author yanxq
 * @date 2025-01-19
 */
public class SpawnEditLogger {

    private static final Logger log = LoggerFactory.getLogger(SpawnEditLogger.class);

    /** 审计日志文件路径 */
    private static final String AUDIT_LOG = "spawn_edit_audit.log";

    /**
     * 记录操作日志
     *
     * @param operation 操作类型（CREATE/UPDATE/DELETE/NO_CHANGE/ERROR/RESTORE）
     * @param mapName 地图名称
     * @param territoryName 区域名称
     * @param details 详细信息
     */
    public void log(String operation, String mapName, String territoryName, String details) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String user = System.getProperty("user.name", "unknown");

        String logEntry = String.format("[%s] [%s] %s | %s | %s | %s%n",
            timestamp, user, operation, mapName, territoryName, details);

        try (FileWriter fw = new FileWriter(AUDIT_LOG, true)) {
            fw.write(logEntry);
            fw.flush();
        } catch (IOException e) {
            log.error("写入审计日志失败: " + e.getMessage(), e);
        }
    }

    /**
     * 记录批量操作
     */
    public void logBatch(String operation, String mapName, int count, String summary) {
        log(operation, mapName, String.format("批量操作(%d个)", count), summary);
    }

    /**
     * 记录错误
     */
    public void logError(String mapName, String territoryName, String error) {
        log("ERROR", mapName, territoryName, "错误: " + error);
    }
}
