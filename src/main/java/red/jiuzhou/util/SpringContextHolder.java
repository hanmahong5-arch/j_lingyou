package red.jiuzhou.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 上下文持有者
 *
 * 用于在非 Spring 管理的类中获取 Spring Bean
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        applicationContext = context;
    }

    /**
     * 获取 Bean
     */
    public static <T> T getBean(Class<T> clazz) {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext 尚未初始化");
        }
        return applicationContext.getBean(clazz);
    }

    /**
     * 获取 Bean（按名称）
     */
    public static Object getBean(String name) {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext 尚未初始化");
        }
        return applicationContext.getBean(name);
    }

    /**
     * 获取 Bean（按名称和类型）
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext 尚未初始化");
        }
        return applicationContext.getBean(name, clazz);
    }

    /**
     * 获取 ApplicationContext
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * 检查是否已初始化
     */
    public static boolean isInitialized() {
        return applicationContext != null;
    }
}
