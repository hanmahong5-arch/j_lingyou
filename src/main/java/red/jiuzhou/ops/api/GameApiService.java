package red.jiuzhou.ops.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.ops.db.SqlServerConnection;
import red.jiuzhou.ops.model.GameCharacter;
import red.jiuzhou.ops.model.GameItem;
import red.jiuzhou.ops.service.CharacterService;
import red.jiuzhou.ops.service.GameOpsService;

import java.util.*;

/**
 * 游戏 API 服务层
 *
 * 为移动端/Web端提供 REST API 接口的底层服务：
 * - 玩家自助服务（传送、抽奖、交易）
 * - 付费功能（移动坐标、VIP特权）
 * - 游戏规则执行（技能保留、死亡惩罚）
 *
 * 设计原则：
 * 1. 所有操作需要验证玩家权限
 * 2. 付费操作需要验证余额
 * 3. 敏感操作需要审计日志
 * 4. 支持运维端实时控制开关
 *
 * @author yanxq
 * @date 2026-01-16
 */
public class GameApiService {

    private static final Logger log = LoggerFactory.getLogger(GameApiService.class);

    private final SqlServerConnection connection;
    private final GameOpsService opsService;
    private final CharacterService characterService;

    // 配置开关（运维端可控制）
    private final Map<String, Boolean> featureFlags = new HashMap<>();
    private final Map<String, Object> gameRules = new HashMap<>();

    public GameApiService(SqlServerConnection connection) {
        this.connection = connection;
        this.opsService = new GameOpsService(connection);
        this.characterService = new CharacterService(connection);
        initializeDefaultRules();
    }

    private void initializeDefaultRules() {
        // 功能开关
        featureFlags.put("teleport.enabled", true);
        featureFlags.put("lottery.enabled", true);
        featureFlags.put("trading.enabled", true);
        featureFlags.put("skillRetention.enabled", true);
        featureFlags.put("deathPenalty.enabled", true);

        // 游戏规则参数
        gameRules.put("skill.retentionCount", 10);           // 每个玩家保留10个技能
        gameRules.put("death.itemLossChance", 0.3);          // 死亡30%概率掉落物品
        gameRules.put("death.itemLossCount", 3);             // 最多掉落3件物品
        gameRules.put("teleport.costPerKm", 1000L);          // 每公里传送费用
        gameRules.put("lottery.ticketPrice", 10000L);        // 抽奖券价格
        gameRules.put("trading.feeRate", 0.05);              // 交易手续费5%
    }

    // ==================== 功能开关控制 ====================

    /**
     * 设置功能开关（运维端调用）
     */
    public void setFeatureFlag(String feature, boolean enabled) {
        featureFlags.put(feature, enabled);
        log.info("功能开关变更: {} = {}", feature, enabled);
    }

    /**
     * 获取功能开关状态
     */
    public boolean isFeatureEnabled(String feature) {
        return featureFlags.getOrDefault(feature, false);
    }

    /**
     * 设置游戏规则参数（运维端调用）
     */
    public void setGameRule(String rule, Object value) {
        gameRules.put(rule, value);
        log.info("游戏规则变更: {} = {}", rule, value);
    }

    /**
     * 获取游戏规则参数
     */
    @SuppressWarnings("unchecked")
    public <T> T getGameRule(String rule, T defaultValue) {
        return (T) gameRules.getOrDefault(rule, defaultValue);
    }

    // ==================== 玩家自助服务 ====================

    /**
     * 付费传送服务
     *
     * @param charId 角色ID
     * @param targetWorldId 目标世界ID
     * @param x 目标X坐标
     * @param y 目标Y坐标
     * @param z 目标Z坐标
     * @return 操作结果
     */
    public ApiResult teleport(int charId, int targetWorldId, float x, float y, float z) {
        if (!isFeatureEnabled("teleport.enabled")) {
            return ApiResult.error("FEATURE_DISABLED", "传送功能已关闭");
        }

        // 获取角色信息
        var character = characterService.findById(charId);
        if (character.isEmpty()) {
            return ApiResult.error("CHAR_NOT_FOUND", "角色不存在");
        }

        GameCharacter c = character.get();

        // 计算传送费用
        double distance = calculateDistance(c.x(), c.y(), c.z(), x, y, z);
        long costPerKm = getGameRule("teleport.costPerKm", 1000L);
        long cost = (long) (distance / 1000 * costPerKm);

        // 检查金币余额
        if (c.kinah() < cost) {
            return ApiResult.error("INSUFFICIENT_KINAH",
                    String.format("金币不足，需要 %,d，当前 %,d", cost, c.kinah()));
        }

        // 扣除金币并传送
        if (characterService.addKinah(charId, -cost) &&
            characterService.teleport(charId, targetWorldId, x, y, z)) {
            log.info("玩家传送: charId={}, cost={}, from=({},{},{}), to=({},{},{})",
                    charId, cost, c.x(), c.y(), c.z(), x, y, z);
            return ApiResult.success(Map.of(
                    "cost", cost,
                    "remainingKinah", c.kinah() - cost,
                    "newPosition", Map.of("worldId", targetWorldId, "x", x, "y", y, "z", z)
            ));
        }

        return ApiResult.error("TELEPORT_FAILED", "传送失败");
    }

    /**
     * 抽奖服务
     *
     * @param charId 角色ID
     * @param lotteryType 抽奖类型
     * @return 抽奖结果（物品列表）
     */
    public ApiResult lottery(int charId, String lotteryType) {
        if (!isFeatureEnabled("lottery.enabled")) {
            return ApiResult.error("FEATURE_DISABLED", "抽奖功能已关闭");
        }

        var character = characterService.findById(charId);
        if (character.isEmpty()) {
            return ApiResult.error("CHAR_NOT_FOUND", "角色不存在");
        }

        GameCharacter c = character.get();
        long ticketPrice = getGameRule("lottery.ticketPrice", 10000L);

        if (c.kinah() < ticketPrice) {
            return ApiResult.error("INSUFFICIENT_KINAH",
                    String.format("金币不足，需要 %,d", ticketPrice));
        }

        // 扣除金币
        if (!characterService.addKinah(charId, -ticketPrice)) {
            return ApiResult.error("DEDUCT_FAILED", "扣除金币失败");
        }

        // 执行抽奖逻辑（调用存储过程或自定义逻辑）
        List<Map<String, Object>> rewards = executeLottery(charId, lotteryType);

        log.info("玩家抽奖: charId={}, type={}, cost={}, rewards={}",
                charId, lotteryType, ticketPrice, rewards.size());

        return ApiResult.success(Map.of(
                "cost", ticketPrice,
                "rewards", rewards
        ));
    }

    /**
     * 交易所挂单
     */
    public ApiResult createTradeOrder(int charId, long itemUniqueId, long price) {
        if (!isFeatureEnabled("trading.enabled")) {
            return ApiResult.error("FEATURE_DISABLED", "交易功能已关闭");
        }

        // 验证物品所有权
        List<GameItem> items = opsService.getCharacterItems(charId, GameItem.STORAGE_INVENTORY);
        boolean ownsItem = items.stream()
                .anyMatch(item -> item.itemUniqueId() == itemUniqueId);

        if (!ownsItem) {
            return ApiResult.error("ITEM_NOT_OWNED", "该物品不属于您");
        }

        // 创建挂单（调用存储过程）
        try {
            connection.executeProcedure("aion_CreateTradeOrder", Map.of(
                    "char_id", charId,
                    "item_unique_id", itemUniqueId,
                    "price", price
            ));

            log.info("创建交易挂单: charId={}, itemId={}, price={}", charId, itemUniqueId, price);
            return ApiResult.success(Map.of("orderId", itemUniqueId, "price", price));
        } catch (Exception e) {
            log.error("创建挂单失败", e);
            return ApiResult.error("ORDER_FAILED", "创建挂单失败");
        }
    }

    /**
     * 购买交易所物品
     */
    public ApiResult buyTradeOrder(int charId, long orderId) {
        if (!isFeatureEnabled("trading.enabled")) {
            return ApiResult.error("FEATURE_DISABLED", "交易功能已关闭");
        }

        try {
            // 获取订单信息
            List<Map<String, Object>> orders = connection.callProcedure(
                    "aion_GetTradeOrder",
                    Map.of("order_id", orderId)
            );

            if (orders.isEmpty()) {
                return ApiResult.error("ORDER_NOT_FOUND", "订单不存在");
            }

            Map<String, Object> order = orders.get(0);
            long price = ((Number) order.get("price")).longValue();
            double feeRate = getGameRule("trading.feeRate", 0.05);
            long fee = (long) (price * feeRate);
            long totalCost = price + fee;

            // 检查买家金币
            var buyer = characterService.findById(charId);
            if (buyer.isEmpty() || buyer.get().kinah() < totalCost) {
                return ApiResult.error("INSUFFICIENT_KINAH",
                        String.format("金币不足，需要 %,d (含手续费 %,d)", totalCost, fee));
            }

            // 执行交易
            connection.executeProcedure("aion_ExecuteTrade", Map.of(
                    "buyer_id", charId,
                    "order_id", orderId,
                    "total_cost", totalCost
            ));

            log.info("交易完成: buyer={}, orderId={}, cost={}", charId, orderId, totalCost);
            return ApiResult.success(Map.of(
                    "orderId", orderId,
                    "price", price,
                    "fee", fee,
                    "totalCost", totalCost
            ));
        } catch (Exception e) {
            log.error("购买失败", e);
            return ApiResult.error("BUY_FAILED", "购买失败");
        }
    }

    // ==================== 游戏规则执行 ====================

    /**
     * 技能保留机制
     *
     * 当玩家技能数量超过限制时，自动保留最高等级的技能
     *
     * @param charId 角色ID
     * @return 被移除的技能列表
     */
    public ApiResult enforceSkillRetention(int charId) {
        if (!isFeatureEnabled("skillRetention.enabled")) {
            return ApiResult.success(Map.of("message", "技能保留功能已关闭"));
        }

        int retentionCount = getGameRule("skill.retentionCount", 10);

        try {
            // 获取玩家技能列表
            List<Map<String, Object>> skills = connection.callProcedure(
                    "aion_GetSkillList",
                    Map.of("char_id", charId)
            );

            if (skills.size() <= retentionCount) {
                return ApiResult.success(Map.of(
                        "message", "技能数量未超限",
                        "currentCount", skills.size(),
                        "limit", retentionCount
                ));
            }

            // 按技能等级排序，保留最高的N个
            skills.sort((a, b) -> {
                int levelA = ((Number) a.getOrDefault("skill_level", 0)).intValue();
                int levelB = ((Number) b.getOrDefault("skill_level", 0)).intValue();
                return Integer.compare(levelB, levelA);
            });

            List<Map<String, Object>> toRemove = skills.subList(retentionCount, skills.size());
            List<Integer> removedSkillIds = new ArrayList<>();

            for (Map<String, Object> skill : toRemove) {
                int skillId = ((Number) skill.get("skill_id")).intValue();
                connection.executeProcedure("aion_RemoveSkill", Map.of(
                        "char_id", charId,
                        "skill_id", skillId
                ));
                removedSkillIds.add(skillId);
            }

            log.info("技能保留执行: charId={}, removed={}", charId, removedSkillIds);
            return ApiResult.success(Map.of(
                    "removedCount", removedSkillIds.size(),
                    "removedSkills", removedSkillIds,
                    "retainedCount", retentionCount
            ));
        } catch (Exception e) {
            log.error("技能保留执行失败", e);
            return ApiResult.error("SKILL_RETENTION_FAILED", "技能保留执行失败");
        }
    }

    /**
     * 死亡惩罚机制
     *
     * 玩家死亡后随机掉落物品到公共交易所
     *
     * @param charId 角色ID
     * @return 掉落的物品列表
     */
    public ApiResult executeDeathPenalty(int charId) {
        if (!isFeatureEnabled("deathPenalty.enabled")) {
            return ApiResult.success(Map.of("message", "死亡惩罚功能已关闭"));
        }

        double lossChance = getGameRule("death.itemLossChance", 0.3);
        int maxLossCount = getGameRule("death.itemLossCount", 3);

        // 随机判定是否掉落
        if (Math.random() > lossChance) {
            return ApiResult.success(Map.of(
                    "message", "未触发物品掉落",
                    "lossChance", lossChance
            ));
        }

        try {
            // 获取玩家背包物品（排除装备中的）
            List<GameItem> items = opsService.getCharacterItems(charId, GameItem.STORAGE_INVENTORY);
            List<GameItem> droppableItems = items.stream()
                    .filter(item -> !item.isEquipped() && !item.isSoulBound())
                    .toList();

            if (droppableItems.isEmpty()) {
                return ApiResult.success(Map.of("message", "没有可掉落的物品"));
            }

            // 随机选择掉落物品
            int dropCount = Math.min(maxLossCount, droppableItems.size());
            List<GameItem> toDrop = new ArrayList<>(droppableItems);
            Collections.shuffle(toDrop);
            toDrop = toDrop.subList(0, dropCount);

            List<Map<String, Object>> droppedItems = new ArrayList<>();
            for (GameItem item : toDrop) {
                // 从玩家背包移除
                opsService.deleteItem(item.itemUniqueId());

                // 添加到公共交易所（以随机价格）
                long basePrice = 1000L; // 基础价格，实际应从物品模板获取
                long price = (long) (basePrice * (0.5 + Math.random()));

                connection.executeProcedure("aion_AddToPublicExchange", Map.of(
                        "item_id", item.itemId(),
                        "item_count", item.count(),
                        "price", price,
                        "source", "death_penalty"
                ));

                droppedItems.add(Map.of(
                        "itemId", item.itemId(),
                        "itemName", item.itemName(),
                        "count", item.count(),
                        "price", price
                ));
            }

            log.info("死亡惩罚执行: charId={}, droppedItems={}", charId, droppedItems.size());
            return ApiResult.success(Map.of(
                    "droppedCount", droppedItems.size(),
                    "droppedItems", droppedItems
            ));
        } catch (Exception e) {
            log.error("死亡惩罚执行失败", e);
            return ApiResult.error("DEATH_PENALTY_FAILED", "死亡惩罚执行失败");
        }
    }

    // ==================== 辅助方法 ====================

    private double calculateDistance(float x1, float y1, float z1, float x2, float y2, float z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private List<Map<String, Object>> executeLottery(int charId, String lotteryType) {
        // 实际实现应调用存储过程或配置的抽奖逻辑
        List<Map<String, Object>> rewards = new ArrayList<>();

        // 示例：简单的随机奖励
        Random random = new Random();
        int rewardCount = 1 + random.nextInt(3);

        for (int i = 0; i < rewardCount; i++) {
            int itemId = 100000 + random.nextInt(1000);
            int count = 1 + random.nextInt(5);

            // 发送物品给玩家
            opsService.sendItem(charId, itemId, count);

            rewards.add(Map.of(
                    "itemId", itemId,
                    "count", count
            ));
        }

        return rewards;
    }

    /**
     * API 响应结果
     */
    public record ApiResult(
            boolean success,
            String errorCode,
            String errorMessage,
            Map<String, Object> data
    ) {
        public static ApiResult success(Map<String, Object> data) {
            return new ApiResult(true, null, null, data);
        }

        public static ApiResult error(String code, String message) {
            return new ApiResult(false, code, message, null);
        }
    }
}
