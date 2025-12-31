package red.jiuzhou.agent.context;

/**
 * 列元数据
 */
public class ColumnMetadata {

    private String name;
    private String type;
    private String comment;
    private boolean primaryKey;
    private boolean nullable;
    private String defaultValue;

    public ColumnMetadata() {
    }

    public ColumnMetadata(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public ColumnMetadata(String name, String type, String comment) {
        this.name = name;
        this.type = type;
        this.comment = comment;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public String toString() {
        return name + " (" + type + ")";
    }
}
