package red.jiuzhou.util.game;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 游戏坐标计算工具
 *
 * 提供游戏地图中坐标点的计算功能：
 * - 两点间线性插值
 * - 多边形区域内随机点生成
 * - 点是否在多边形内检测（射线法）
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class PointCalculator {

    private static final DecimalFormat COORD_FORMAT = new DecimalFormat("0.000000");
    private static final Random RANDOM = new Random();

    /**
     * 游戏坐标点
     */
    public static class Point3D {
        private double x;
        private double y;
        private double z;

        public Point3D() {}

        public Point3D(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Point3D(String x, String y, String z) {
            this.x = Double.parseDouble(x);
            this.y = Double.parseDouble(y);
            this.z = Double.parseDouble(z);
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }

        public void setX(double x) { this.x = x; }
        public void setY(double y) { this.y = y; }
        public void setZ(double z) { this.z = z; }

        public String getXFormatted() { return COORD_FORMAT.format(x); }
        public String getYFormatted() { return COORD_FORMAT.format(y); }
        public String getZFormatted() { return COORD_FORMAT.format(z); }

        @Override
        public String toString() {
            return String.format("(%s, %s, %s)",
                getXFormatted(), getYFormatted(), getZFormatted());
        }
    }

    /**
     * 在两点之间生成均匀分布的坐标点（线性插值）
     *
     * @param p1 起点
     * @param p2 终点
     * @param pointNum 要生成的点数（包含起点和终点）
     * @return 均匀分布的坐标点列表
     */
    public static List<Point3D> interpolateLinear(Point3D p1, Point3D p2, int pointNum) {
        List<Point3D> points = new ArrayList<>();

        if (pointNum <= 0) {
            return points;
        }

        if (pointNum == 1) {
            points.add(new Point3D(p1.x, p1.y, p1.z));
            return points;
        }

        double dx = (p1.x - p2.x) / (pointNum - 1);
        double dy = (p1.y - p2.y) / (pointNum - 1);
        double dz = (p1.z - p2.z) / (pointNum - 1);

        for (int i = 0; i < pointNum; i++) {
            Point3D point = new Point3D(
                p2.x + dx * i,
                p2.y + dy * i,
                p2.z + dz * i
            );
            points.add(point);
        }

        return points;
    }

    /**
     * 在给定区域内生成随机坐标点
     *
     * 根据输入点的数量自动选择生成策略：
     * - 1个点：返回该点本身
     * - 2个点：在两点连线上随机取点
     * - 3+个点：在多边形区域内随机取点（使用射线法检测）
     *
     * @param vertices 区域顶点列表
     * @param pointNum 要生成的随机点数量
     * @return 随机坐标点列表
     */
    public static List<Point3D> generateRandomInArea(List<Point3D> vertices, int pointNum) {
        List<Point3D> points = new ArrayList<>();

        if (vertices == null || vertices.isEmpty() || pointNum <= 0) {
            return points;
        }

        // 单点：直接返回
        if (vertices.size() == 1) {
            points.add(new Point3D(vertices.get(0).x, vertices.get(0).y, vertices.get(0).z));
            return points;
        }

        // 两点：在线段上随机取点
        if (vertices.size() == 2) {
            return generateRandomOnLine(vertices.get(0), vertices.get(1), pointNum);
        }

        // 多边形：在区域内随机取点
        return generateRandomInPolygon(vertices, pointNum);
    }

    /**
     * 在两点连线上生成随机点
     */
    private static List<Point3D> generateRandomOnLine(Point3D p1, Point3D p2, int pointNum) {
        List<Point3D> points = new ArrayList<>();

        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;
        double dz = p1.z - p2.z;

        for (int i = 0; i < pointNum; i++) {
            double t = RANDOM.nextDouble();
            Point3D point = new Point3D(
                p2.x + dx * t,
                p2.y + dy * t,
                p2.z + dz * t
            );
            points.add(point);
        }

        return points;
    }

    /**
     * 在多边形区域内生成随机点
     */
    private static List<Point3D> generateRandomInPolygon(List<Point3D> vertices, int pointNum) {
        List<Point3D> points = new ArrayList<>();

        // 计算边界框
        double minX = vertices.get(0).x;
        double maxX = vertices.get(0).x;
        double minY = vertices.get(0).y;
        double maxY = vertices.get(0).y;
        double z = vertices.get(0).z;

        for (Point3D v : vertices) {
            if (v.x < minX) minX = v.x;
            if (v.x > maxX) maxX = v.x;
            if (v.y < minY) minY = v.y;
            if (v.y > maxY) maxY = v.y;
        }

        double rangeX = maxX - minX;
        double rangeY = maxY - minY;

        // 提取多边形顶点坐标
        List<Double> vertX = new ArrayList<>();
        List<Double> vertY = new ArrayList<>();
        for (Point3D v : vertices) {
            vertX.add(v.x);
            vertY.add(v.y);
        }

        // 使用拒绝采样法生成多边形内的随机点
        int generated = 0;
        int maxAttempts = pointNum * 100; // 防止无限循环
        int attempts = 0;

        while (generated < pointNum && attempts < maxAttempts) {
            double x = minX + rangeX * RANDOM.nextDouble();
            double y = minY + rangeY * RANDOM.nextDouble();

            if (isPointInPolygon(vertX, vertY, x, y)) {
                points.add(new Point3D(x, y, z));
                generated++;
            }
            attempts++;
        }

        return points;
    }

    /**
     * 判断点是否在多边形内（射线法/PNPOLY算法）
     *
     * @param vertX 多边形顶点X坐标列表
     * @param vertY 多边形顶点Y坐标列表
     * @param testX 测试点X坐标
     * @param testY 测试点Y坐标
     * @return true如果点在多边形内
     */
    public static boolean isPointInPolygon(List<Double> vertX, List<Double> vertY,
                                           double testX, double testY) {
        int nvert = vertX.size();
        boolean inside = false;

        for (int i = 0, j = nvert - 1; i < nvert; j = i++) {
            double yi = vertY.get(i);
            double yj = vertY.get(j);
            double xi = vertX.get(i);
            double xj = vertX.get(j);

            if (((yi > testY) != (yj > testY)) &&
                (testX < (xj - xi) * (testY - yi) / (yj - yi) + xi)) {
                inside = !inside;
            }
        }

        return inside;
    }

    /**
     * 判断点是否在多边形内（简化版本）
     */
    public static boolean isPointInPolygon(List<Point3D> vertices, double testX, double testY) {
        List<Double> vertX = new ArrayList<>();
        List<Double> vertY = new ArrayList<>();
        for (Point3D v : vertices) {
            vertX.add(v.x);
            vertY.add(v.y);
        }
        return isPointInPolygon(vertX, vertY, testX, testY);
    }

    /**
     * 计算两点之间的距离
     */
    public static double distance(Point3D p1, Point3D p2) {
        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;
        double dz = p1.z - p2.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * 计算两点之间的2D距离（忽略Z轴）
     */
    public static double distance2D(Point3D p1, Point3D p2) {
        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 在圆形区域内生成随机点
     *
     * @param center 圆心
     * @param radius 半径
     * @param pointNum 要生成的点数
     * @return 随机坐标点列表
     */
    public static List<Point3D> generateRandomInCircle(Point3D center, double radius, int pointNum) {
        List<Point3D> points = new ArrayList<>();

        for (int i = 0; i < pointNum; i++) {
            // 使用均匀分布在圆内生成点
            double r = radius * Math.sqrt(RANDOM.nextDouble());
            double theta = 2 * Math.PI * RANDOM.nextDouble();

            double x = center.x + r * Math.cos(theta);
            double y = center.y + r * Math.sin(theta);

            points.add(new Point3D(x, y, center.z));
        }

        return points;
    }

    /**
     * 在环形区域内生成随机点
     *
     * @param center 圆心
     * @param innerRadius 内半径
     * @param outerRadius 外半径
     * @param pointNum 要生成的点数
     * @return 随机坐标点列表
     */
    public static List<Point3D> generateRandomInRing(Point3D center,
            double innerRadius, double outerRadius, int pointNum) {
        List<Point3D> points = new ArrayList<>();

        for (int i = 0; i < pointNum; i++) {
            // 环形均匀分布
            double r = Math.sqrt(RANDOM.nextDouble() * (outerRadius * outerRadius - innerRadius * innerRadius)
                                + innerRadius * innerRadius);
            double theta = 2 * Math.PI * RANDOM.nextDouble();

            double x = center.x + r * Math.cos(theta);
            double y = center.y + r * Math.sin(theta);

            points.add(new Point3D(x, y, center.z));
        }

        return points;
    }
}
