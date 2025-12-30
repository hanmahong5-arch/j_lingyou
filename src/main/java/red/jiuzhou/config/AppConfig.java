package red.jiuzhou.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 应用配置类（Spring Boot 4 风格）
 *
 * <p>启用配置属性绑定，支持类型安全的配置读取。
 *
 * @author Claude
 * @version 1.0
 */
@Configuration
@EnableConfigurationProperties({
    AionProperties.class,
    AiProperties.class
})
public class AppConfig {
    // 配置属性通过 @EnableConfigurationProperties 自动注册
}
