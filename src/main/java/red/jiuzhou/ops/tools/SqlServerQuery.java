package red.jiuzhou.ops.tools;

import java.sql.*;

/**
 * 快速 SQL Server 查询工具
 */
public class SqlServerQuery {

    public static void main(String[] args) {
        String url = "jdbc:sqlserver://localhost:1433;database=AionWorldLive;encrypt=false;trustServerCertificate=true";
        String user = "sa";
        String password = "aion.5201314";

        String query = args.length > 0 ? args[0] :
            "SELECT name FROM sys.procedures WHERE name LIKE '%death%' OR name LIKE '%die%' OR name LIKE '%kill%' ORDER BY name";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();

            // Print header
            for (int i = 1; i <= cols; i++) {
                System.out.print(meta.getColumnName(i) + "\t");
            }
            System.out.println();
            System.out.println("-".repeat(80));

            // Print rows
            while (rs.next()) {
                for (int i = 1; i <= cols; i++) {
                    System.out.print(rs.getString(i) + "\t");
                }
                System.out.println();
            }

        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
