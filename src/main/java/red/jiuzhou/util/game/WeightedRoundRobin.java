package red.jiuzhou.util.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 加权轮询选择器
 *
 * 用于游戏中按权重分配资源的场景，如：
 * - 怪物刷新点权重选择
 * - 掉落物品权重选择
 * - NPC出现概率控制
 *
 * 支持两种模式：
 * 1. 加权轮询（Weighted Round-Robin）：保证长期分布符合权重比例
 * 2. 加权随机（Weighted Random）：每次独立按权重随机选择
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class WeightedRoundRobin<T> {

    private static final Random RANDOM = new Random();

    /**
     * 带权重的元素
     */
    public static class WeightedItem<T> {
        private T item;
        private int weight;           // 原始权重
        private int effectiveWeight;  // 有效权重（用于轮询调整）
        private int currentWeight;    // 当前权重（动态变化）

        public WeightedItem(T item, int weight) {
            this.item = item;
            this.weight = weight;
            this.effectiveWeight = weight;
            this.currentWeight = 0;
        }

        public T getItem() { return item; }
        public int getWeight() { return weight; }
        public int getEffectiveWeight() { return effectiveWeight; }
        public int getCurrentWeight() { return currentWeight; }

        public void setEffectiveWeight(int effectiveWeight) { this.effectiveWeight = effectiveWeight; }
        public void setCurrentWeight(int currentWeight) { this.currentWeight = currentWeight; }

        /**
         * 重置权重状态
         */
        public void reset() {
            this.effectiveWeight = weight;
            this.currentWeight = 0;
        }
    }

    private List<WeightedItem<T>> items;
    private int totalWeight;

    public WeightedRoundRobin() {
        this.items = new ArrayList<>();
        this.totalWeight = 0;
    }

    /**
     * 添加带权重的元素
     */
    public void add(T item, int weight) {
        if (weight > 0) {
            items.add(new WeightedItem<>(item, weight));
            totalWeight += weight;
        }
    }

    /**
     * 添加多个元素
     */
    public void addAll(List<T> itemList, List<Integer> weights) {
        if (itemList.size() != weights.size()) {
            throw new IllegalArgumentException("Items and weights must have same size");
        }
        for (int i = 0; i < itemList.size(); i++) {
            add(itemList.get(i), weights.get(i));
        }
    }

    /**
     * 加权轮询选择（Nginx风格）
     *
     * 每次选择权重最高的元素，然后调整所有元素的当前权重。
     * 保证长期分布严格符合权重比例。
     *
     * @return 选中的元素，如果列表为空返回null
     */
    public T selectRoundRobin() {
        if (items.isEmpty()) {
            return null;
        }
        if (items.size() == 1) {
            return items.get(0).item;
        }

        WeightedItem<T> best = null;
        int total = 0;

        for (WeightedItem<T> item : items) {
            // 增加当前权重
            item.currentWeight += item.effectiveWeight;
            total += item.effectiveWeight;

            // 选择当前权重最高的
            if (best == null || item.currentWeight > best.currentWeight) {
                best = item;
            }
        }

        if (best != null) {
            // 减去总权重，实现平滑分配
            best.currentWeight -= total;
            return best.item;
        }

        return null;
    }

    /**
     * 加权随机选择
     *
     * 每次独立按权重随机选择，适用于概率独立的场景。
     *
     * @return 选中的元素，如果列表为空返回null
     */
    public T selectRandom() {
        if (items.isEmpty()) {
            return null;
        }
        if (items.size() == 1) {
            return items.get(0).item;
        }

        int randomWeight = RANDOM.nextInt(totalWeight);
        int cumulative = 0;

        for (WeightedItem<T> item : items) {
            cumulative += item.weight;
            if (randomWeight < cumulative) {
                return item.item;
            }
        }

        // 理论上不会到达这里
        return items.get(items.size() - 1).item;
    }

    /**
     * 批量选择（加权轮询）
     */
    public List<T> selectMultipleRoundRobin(int count) {
        List<T> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            T selected = selectRoundRobin();
            if (selected != null) {
                result.add(selected);
            }
        }
        return result;
    }

    /**
     * 批量选择（加权随机）
     */
    public List<T> selectMultipleRandom(int count) {
        List<T> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            T selected = selectRandom();
            if (selected != null) {
                result.add(selected);
            }
        }
        return result;
    }

    /**
     * 不重复的加权随机选择
     *
     * @param count 选择数量
     * @return 不重复的元素列表
     */
    public List<T> selectUniqueRandom(int count) {
        List<T> result = new ArrayList<>();
        List<WeightedItem<T>> remaining = new ArrayList<>(items);
        int remainingWeight = totalWeight;

        int selectCount = Math.min(count, items.size());
        for (int i = 0; i < selectCount; i++) {
            if (remaining.isEmpty()) {
                break;
            }

            int randomWeight = RANDOM.nextInt(remainingWeight);
            int cumulative = 0;
            WeightedItem<T> selected = null;

            for (WeightedItem<T> item : remaining) {
                cumulative += item.weight;
                if (randomWeight < cumulative) {
                    selected = item;
                    break;
                }
            }

            if (selected != null) {
                result.add(selected.item);
                remaining.remove(selected);
                remainingWeight -= selected.weight;
            }
        }

        return result;
    }

    /**
     * 重置所有权重状态
     */
    public void reset() {
        for (WeightedItem<T> item : items) {
            item.reset();
        }
    }

    /**
     * 清空所有元素
     */
    public void clear() {
        items.clear();
        totalWeight = 0;
    }

    /**
     * 获取元素数量
     */
    public int size() {
        return items.size();
    }

    /**
     * 获取总权重
     */
    public int getTotalWeight() {
        return totalWeight;
    }

    /**
     * 检查是否为空
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * 创建快速构建器
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * 构建器
     */
    public static class Builder<T> {
        private WeightedRoundRobin<T> selector = new WeightedRoundRobin<>();

        public Builder<T> add(T item, int weight) {
            selector.add(item, weight);
            return this;
        }

        public WeightedRoundRobin<T> build() {
            return selector;
        }
    }
}
