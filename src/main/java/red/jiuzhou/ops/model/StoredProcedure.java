package red.jiuzhou.ops.model;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 存储过程元数据模型
 *
 * 表示 SQL Server 数据库中的存储过程信息，
 * 包括名称、参数、分类等
 *
 * @author yanxq
 * @date 2026-01-16
 */
public record StoredProcedure(
        String name,
        String schema,
        String description,
        ProcedureCategory category
) {

    /**
     * 存储过程参数
     */
    public record Parameter(
            String name,
            String type,
            boolean isOutput,
            boolean hasDefault
    ) {
        /**
         * Get display string for parameter
         */
        public String toDisplayString() {
            StringBuilder sb = new StringBuilder();
            sb.append("@").append(name);
            sb.append(" ").append(type);
            if (isOutput) {
                sb.append(" OUTPUT");
            }
            if (hasDefault) {
                sb.append(" = DEFAULT");
            }
            return sb.toString();
        }

        /**
         * Check if this is an input parameter
         */
        public boolean isInput() {
            return !isOutput;
        }
    }

    /**
     * Get full qualified name (schema.name)
     */
    public String getFullName() {
        return schema + "." + name;
    }

    /**
     * Get display name with category icon
     */
    public String getDisplayName() {
        return category.getIcon() + " " + name;
    }

    /**
     * Infer category from procedure name
     *
     * Based on naming conventions in Aion database:
     * - aion_ChangeChar* -> CHARACTER
     * - aion_GetGuild* -> GUILD
     * - aion_*Buddy*, aion_*Block* -> SOCIAL
     * - etc.
     */
    public static ProcedureCategory inferCategory(String procName) {
        String lowerName = procName.toLowerCase();

        // Character Management
        if (matchesAny(lowerName,
                "char", "character", "charname", "charshape", "charrace",
                "getcharid", "deletechar", "createchar")) {
            return ProcedureCategory.CHARACTER;
        }

        // Social System
        if (matchesAny(lowerName,
                "buddy", "block", "friend", "memo", "whisper")) {
            return ProcedureCategory.SOCIAL;
        }

        // Guild System
        if (matchesAny(lowerName,
                "guild", "legion", "guildmember", "guildwarehouse")) {
            return ProcedureCategory.GUILD;
        }

        // Item & Economy
        if (matchesAny(lowerName,
                "item", "itemlist", "inventory", "warehouse", "kina", "vendor", "kinah")) {
            return ProcedureCategory.ITEM;
        }

        // Auction House
        if (matchesAny(lowerName,
                "auction", "bid", "broker")) {
            return ProcedureCategory.AUCTION;
        }

        // Combat & PVP
        if (matchesAny(lowerName,
                "abyss", "rank", "pvp", "battle", "siege", "combat")) {
            return ProcedureCategory.COMBAT;
        }

        // Quest & Progress
        if (matchesAny(lowerName,
                "quest", "skill", "recipe", "title", "progress")) {
            return ProcedureCategory.QUEST;
        }

        // Housing System
        if (matchesAny(lowerName,
                "house", "housing", "furniture", "home")) {
            return ProcedureCategory.HOUSING;
        }

        // Account Management
        if (matchesAny(lowerName,
                "account", "login", "password", "ban", "user")) {
            return ProcedureCategory.ACCOUNT;
        }

        // Mail System
        if (matchesAny(lowerName,
                "mail", "letter", "postbox")) {
            return ProcedureCategory.MAIL;
        }

        // System Maintenance
        if (matchesAny(lowerName,
                "reindex", "cleanup", "delete", "clear", "backup", "profile", "legacy", "dbprofile")) {
            return ProcedureCategory.SYSTEM;
        }

        return ProcedureCategory.OTHER;
    }

    /**
     * Check if name contains any of the keywords
     */
    private static boolean matchesAny(String name, String... keywords) {
        for (String keyword : keywords) {
            if (name.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if this procedure is likely a query (GET/SELECT)
     */
    public boolean isQuery() {
        String lowerName = name.toLowerCase();
        return lowerName.startsWith("aion_get") ||
               lowerName.contains("list") ||
               lowerName.contains("select") ||
               lowerName.contains("find") ||
               lowerName.contains("search");
    }

    /**
     * Check if this procedure is likely a modification (ADD/UPDATE/DELETE)
     */
    public boolean isModification() {
        String lowerName = name.toLowerCase();
        return lowerName.startsWith("aion_add") ||
               lowerName.startsWith("aion_update") ||
               lowerName.startsWith("aion_delete") ||
               lowerName.startsWith("aion_change") ||
               lowerName.startsWith("aion_set") ||
               lowerName.startsWith("aion_insert");
    }

    /**
     * Check if this procedure is potentially dangerous (DELETE/DROP/CLEAR)
     */
    public boolean isDangerous() {
        String lowerName = name.toLowerCase();
        return lowerName.contains("delete") ||
               lowerName.contains("drop") ||
               lowerName.contains("clear") ||
               lowerName.contains("remove") ||
               lowerName.contains("truncate");
    }

    /**
     * Get action type for UI display
     */
    public String getActionType() {
        if (isQuery()) return "查询";
        if (isDangerous()) return "危险";
        if (isModification()) return "修改";
        return "执行";
    }

    /**
     * Get action color for UI
     */
    public String getActionColor() {
        if (isDangerous()) return "#e74c3c";  // Red
        if (isModification()) return "#f39c12";  // Orange
        if (isQuery()) return "#27ae60";  // Green
        return "#3498db";  // Blue
    }
}
