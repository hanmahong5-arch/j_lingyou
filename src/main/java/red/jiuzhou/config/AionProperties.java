package red.jiuzhou.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Aion游戏配置属性（Spring Boot 4 风格）
 *
 * <p>从 application.yml 中读取 aion.* 配置项：
 * <pre>
 * aion:
 *   xmlPath: D:\AionReal58\AionMap\XML
 *   localizedPath: D:\AionReal58\AionMap\XML\China
 * </pre>
 *
 * @author Claude
 * @version 1.0
 */
@Component
@ConfigurationProperties(prefix = "aion")
public class AionProperties {

    /** XML数据根目录 */
    private String xmlPath;

    /** 本地化XML目录 */
    private String localizedPath;

    /** 缓存过期时间（秒） */
    private int cacheExpireSeconds = 3600;

    /** 是否启用机制检测 */
    private boolean mechanismDetectionEnabled = true;

    // ========== Getters & Setters ==========

    public String getXmlPath() {
        return xmlPath;
    }

    public void setXmlPath(String xmlPath) {
        this.xmlPath = xmlPath;
    }

    public String getLocalizedPath() {
        return localizedPath;
    }

    public void setLocalizedPath(String localizedPath) {
        this.localizedPath = localizedPath;
    }

    public int getCacheExpireSeconds() {
        return cacheExpireSeconds;
    }

    public void setCacheExpireSeconds(int cacheExpireSeconds) {
        this.cacheExpireSeconds = cacheExpireSeconds;
    }

    public boolean isMechanismDetectionEnabled() {
        return mechanismDetectionEnabled;
    }

    public void setMechanismDetectionEnabled(boolean mechanismDetectionEnabled) {
        this.mechanismDetectionEnabled = mechanismDetectionEnabled;
    }

    @Override
    public String toString() {
        return "AionProperties{xmlPath='%s', localizedPath='%s', cacheExpireSeconds=%d}"
            .formatted(xmlPath, localizedPath, cacheExpireSeconds);
    }
}
