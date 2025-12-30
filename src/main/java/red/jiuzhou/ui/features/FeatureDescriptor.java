package red.jiuzhou.ui.features;

import java.util.Objects;

/**
 * 特性描述符（Record）
 *
 * <p>声明式描述一个可启动的特性按钮
 *
 * @param id          特性唯一标识
 * @param displayName 显示名称
 * @param description 功能描述
 * @param category    特性分类
 * @param launcher    启动器
 */
public record FeatureDescriptor(
    String id,
    String displayName,
    String description,
    FeatureCategory category,
    FeatureLauncher launcher
) {
    /**
     * 紧凑构造器 - 参数校验
     */
    public FeatureDescriptor {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(launcher, "launcher must not be null");
    }
}
