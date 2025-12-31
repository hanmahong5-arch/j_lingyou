package red.jiuzhou.agent.context;

import java.util.ArrayList;
import java.util.List;

/**
 * 表结构元数据
 */
public class TableMetadata {

    private String tableName;
    private String tableComment;
    private long rowCount;
    private List<ColumnMetadata> columns = new ArrayList<>();

    public TableMetadata() {
    }

    public TableMetadata(String tableName) {
        this.tableName = tableName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getTableComment() {
        return tableComment;
    }

    public void setTableComment(String tableComment) {
        this.tableComment = tableComment;
    }

    public long getRowCount() {
        return rowCount;
    }

    public void setRowCount(long rowCount) {
        this.rowCount = rowCount;
    }

    public List<ColumnMetadata> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnMetadata> columns) {
        this.columns = columns;
    }

    public void addColumn(ColumnMetadata column) {
        this.columns.add(column);
    }

    /**
     * 查找主键列
     */
    public ColumnMetadata getPrimaryKey() {
        return columns.stream()
                .filter(ColumnMetadata::isPrimaryKey)
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取列名列表
     */
    public List<String> getColumnNames() {
        return columns.stream()
                .map(ColumnMetadata::getName)
                .toList();
    }

    @Override
    public String toString() {
        return "TableMetadata{" +
                "tableName='" + tableName + '\'' +
                ", columns=" + columns.size() +
                ", rowCount=" + rowCount +
                '}';
    }
}
