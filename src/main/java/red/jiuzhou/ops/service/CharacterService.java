package red.jiuzhou.ops.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.ops.db.SqlServerConnection;
import red.jiuzhou.ops.model.GameCharacter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 角色管理服务
 *
 * 提供角色相关的运维操作：
 * - 角色查询（按ID/名称/账号）
 * - 角色修改（名称/种族/外观/职业）
 * - 角色删除与恢复
 * - 角色属性修改（等级/金币/经验等）
 *
 * @author yanxq
 * @date 2026-01-16
 */
public class CharacterService {

    private static final Logger log = LoggerFactory.getLogger(CharacterService.class);

    private final SqlServerConnection connection;

    public CharacterService(SqlServerConnection connection) {
        this.connection = connection;
    }

    // ==================== 查询操作 ====================

    /**
     * 根据角色ID查询
     */
    public Optional<GameCharacter> findById(int charId) {
        try {
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_GetCharInfo",
                    Map.of("char_id", charId)
            );
            if (!results.isEmpty()) {
                return Optional.of(GameCharacter.fromMap(results.get(0)));
            }
        } catch (Exception e) {
            log.error("查询角色失败: charId={}", charId, e);
        }
        return Optional.empty();
    }

    /**
     * 根据角色名称查询
     */
    public Optional<GameCharacter> findByName(String name) {
        try {
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_GetCharInfoByName",
                    Map.of("name", name)
            );
            if (!results.isEmpty()) {
                return Optional.of(GameCharacter.fromMap(results.get(0)));
            }
        } catch (Exception e) {
            log.error("查询角色失败: name={}", name, e);
        }
        return Optional.empty();
    }

    /**
     * 模糊搜索角色
     */
    public List<GameCharacter> search(String keyword, int limit) {
        try {
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_SearchCharacters",
                    Map.of("keyword", "%" + keyword + "%", "limit", limit)
            );
            return results.stream()
                    .map(GameCharacter::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("搜索角色失败: keyword={}", keyword, e);
            return List.of();
        }
    }

    /**
     * 查询账号下所有角色
     */
    public List<GameCharacter> findByAccountId(int accountId) {
        try {
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_GetCharIdList",
                    Map.of("account_id", accountId)
            );
            return results.stream()
                    .map(GameCharacter::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("查询账号角色失败: accountId={}", accountId, e);
            return List.of();
        }
    }

    /**
     * 查询公会所有成员
     */
    public List<GameCharacter> findByGuildId(int guildId) {
        try {
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_GetGuildMemberCharacters",
                    Map.of("guild_id", guildId)
            );
            return results.stream()
                    .map(GameCharacter::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("查询公会成员失败: guildId={}", guildId, e);
            return List.of();
        }
    }

    /**
     * 获取在线角色列表
     */
    public List<GameCharacter> getOnlinePlayers() {
        try {
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_GetOnlinePlayers",
                    null
            );
            return results.stream()
                    .map(GameCharacter::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("获取在线角色失败", e);
            return List.of();
        }
    }

    // ==================== 修改操作 ====================

    /**
     * 修改角色名称
     */
    public boolean changeName(int charId, String newName) {
        try {
            // Check if name is available
            if (findByName(newName).isPresent()) {
                log.warn("角色名已被使用: {}", newName);
                return false;
            }

            log.info("修改角色名称: charId={}, newName={}", charId, newName);
            connection.executeProcedure(
                    "aion_ChangeCharName",
                    Map.of("char_id", charId, "new_name", newName)
            );
            return true;
        } catch (Exception e) {
            log.error("修改角色名称失败", e);
            return false;
        }
    }

    /**
     * 修改角色种族
     */
    public boolean changeRace(int charId, String newRace) {
        try {
            if (!isValidRace(newRace)) {
                log.warn("无效的种族: {}", newRace);
                return false;
            }

            log.info("修改角色种族: charId={}, newRace={}", charId, newRace);
            connection.executeProcedure(
                    "aion_ChangeCharRace",
                    Map.of("char_id", charId, "race", newRace)
            );
            return true;
        } catch (Exception e) {
            log.error("修改角色种族失败", e);
            return false;
        }
    }

    /**
     * 修改角色职业
     */
    public boolean changeClass(int charId, String newClass) {
        try {
            if (!isValidClass(newClass)) {
                log.warn("无效的职业: {}", newClass);
                return false;
            }

            log.info("修改角色职业: charId={}, newClass={}", charId, newClass);
            connection.executeProcedure(
                    "aion_ChangeCharClass",
                    Map.of("char_id", charId, "player_class", newClass)
            );
            return true;
        } catch (Exception e) {
            log.error("修改角色职业失败", e);
            return false;
        }
    }

    /**
     * 修改角色外观
     */
    public boolean changeAppearance(int charId, String appearanceData) {
        try {
            log.info("修改角色外观: charId={}", charId);
            connection.executeProcedure(
                    "aion_ChangeCharShape",
                    Map.of("char_id", charId, "appearance", appearanceData)
            );
            return true;
        } catch (Exception e) {
            log.error("修改角色外观失败", e);
            return false;
        }
    }

    /**
     * 设置角色等级
     */
    public boolean setLevel(int charId, int newLevel) {
        try {
            if (newLevel < 1 || newLevel > 85) {
                log.warn("无效的等级: {}", newLevel);
                return false;
            }

            log.info("设置角色等级: charId={}, level={}", charId, newLevel);
            connection.executeProcedure(
                    "aion_SetCharLevel",
                    Map.of("char_id", charId, "level", newLevel)
            );
            return true;
        } catch (Exception e) {
            log.error("设置角色等级失败", e);
            return false;
        }
    }

    /**
     * 增加角色金币
     */
    public boolean addKinah(int charId, long amount) {
        try {
            log.info("增加角色金币: charId={}, amount={}", charId, amount);
            connection.executeProcedure(
                    "aion_AddKinah",
                    Map.of("char_id", charId, "amount", amount)
            );
            return true;
        } catch (Exception e) {
            log.error("增加角色金币失败", e);
            return false;
        }
    }

    /**
     * 设置角色金币
     */
    public boolean setKinah(int charId, long amount) {
        try {
            if (amount < 0) {
                log.warn("金币数量不能为负: {}", amount);
                return false;
            }

            log.info("设置角色金币: charId={}, amount={}", charId, amount);
            connection.executeProcedure(
                    "aion_SetKinah",
                    Map.of("char_id", charId, "amount", amount)
            );
            return true;
        } catch (Exception e) {
            log.error("设置角色金币失败", e);
            return false;
        }
    }

    /**
     * 传送角色到指定位置
     */
    public boolean teleport(int charId, int worldId, float x, float y, float z) {
        try {
            log.info("传送角色: charId={}, worldId={}, pos=({}, {}, {})",
                    charId, worldId, x, y, z);
            connection.executeProcedure(
                    "aion_TeleportChar",
                    Map.of(
                            "char_id", charId,
                            "world_id", worldId,
                            "x", x, "y", y, "z", z
                    )
            );
            return true;
        } catch (Exception e) {
            log.error("传送角色失败", e);
            return false;
        }
    }

    // ==================== 删除操作 ====================

    /**
     * 删除角色（软删除）
     */
    public boolean delete(int charId) {
        try {
            log.warn("删除角色: charId={}", charId);
            connection.executeProcedure(
                    "aion_DeleteChar",
                    Map.of("char_id", charId)
            );
            return true;
        } catch (Exception e) {
            log.error("删除角色失败", e);
            return false;
        }
    }

    /**
     * 恢复已删除的角色
     */
    public boolean restore(int charId) {
        try {
            log.info("恢复角色: charId={}", charId);
            connection.executeProcedure(
                    "aion_RestoreChar",
                    Map.of("char_id", charId)
            );
            return true;
        } catch (Exception e) {
            log.error("恢复角色失败", e);
            return false;
        }
    }

    /**
     * 永久删除角色（硬删除，不可恢复）
     */
    public boolean permanentDelete(int charId) {
        try {
            log.warn("永久删除角色: charId={} - 此操作不可恢复!", charId);
            connection.executeProcedure(
                    "aion_PermanentDeleteChar",
                    Map.of("char_id", charId)
            );
            return true;
        } catch (Exception e) {
            log.error("永久删除角色失败", e);
            return false;
        }
    }

    // ==================== 辅助方法 ====================

    private boolean isValidRace(String race) {
        return race != null && (
                race.equals("ELYOS") || race.equals("ASMODIAN")
        );
    }

    private boolean isValidClass(String charClass) {
        return charClass != null && List.of(
                "WARRIOR", "GLADIATOR", "TEMPLAR",
                "SCOUT", "ASSASSIN", "RANGER",
                "MAGE", "SORCERER", "SPIRIT_MASTER",
                "PRIEST", "CLERIC", "CHANTER",
                "ENGINEER", "GUNNER", "RIDER",
                "ARTIST", "BARD", "PAINTER"
        ).contains(charClass);
    }

    /**
     * 获取可用种族列表
     */
    public static List<String> getAvailableRaces() {
        return List.of("ELYOS", "ASMODIAN");
    }

    /**
     * 获取可用职业列表
     */
    public static List<String> getAvailableClasses() {
        return List.of(
                "WARRIOR", "GLADIATOR", "TEMPLAR",
                "SCOUT", "ASSASSIN", "RANGER",
                "MAGE", "SORCERER", "SPIRIT_MASTER",
                "PRIEST", "CLERIC", "CHANTER",
                "ENGINEER", "GUNNER", "RIDER",
                "ARTIST", "BARD", "PAINTER"
        );
    }
}
