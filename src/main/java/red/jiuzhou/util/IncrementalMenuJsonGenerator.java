package red.jiuzhou.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;
import red.jiuzhou.util.JSONRecord;
import red.jiuzhou.util.YamlUtils;

import java.io.File;
import java.util.*;

class MenuNode {
    private String dbName;
    private String name;
    private String path;
    private List<MenuNode> children = new ArrayList<>();

    public MenuNode() {}
    public MenuNode(String name, String path) {
        this.name = name;
        this.path = path;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void addChild(MenuNode child) {
        this.children.add(child);
    }

    public List<MenuNode> getChildren() {
        return isChildrenEmpty() ? null : children;
    }

    public void setChildren(List<MenuNode> children) {
        this.children = children;
    }

    @JSONField(serialize = false)
    public boolean isChildrenEmpty() {
        return children == null || children.isEmpty();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof MenuNode && this.path.equals(((MenuNode) obj).getPath());
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

}

public class IncrementalMenuJsonGenerator {

    public static String createJsonIncrementally() {
        String homePath = YamlUtils.getProperty("file.homePath");

        // 读取目录管理器配置的路径（与 DirectoryManagerDialog 保持一致）
        String confPathRaw = YamlUtils.getProperty("xmlPath." + DatabaseUtil.getDbName());

        if(!StringUtils.hasLength(confPathRaw)){
            File jsonFile = new File(homePath + "LeftMenu.json");
            FileUtil.writeUtf8String("{}", jsonFile);
            return "{}";
        }

        // 支持逗号分隔的多个目录
        String[] confPaths = confPathRaw.split(",");
        List<MenuNode> allRoots = new ArrayList<>();

        for (String path : confPaths) {
            File dir = new File(path.trim());
            if (dir.exists() && dir.isDirectory()) {
                MenuNode node = directoryToJson(dir);
                if (node != null) {
                    node.setDbName(DatabaseUtil.getDbName());
                    // 使用完整路径作为显示名称，让用户清楚看到是哪个目录
                    node.setName(dir.getAbsolutePath());
                    allRoots.add(node);
                }
            }
        }

        // 如果没有任何目录，返回空JSON
        if (allRoots.isEmpty()) {
            File jsonFile = new File(homePath + "LeftMenu.json");
            FileUtil.writeUtf8String("{}", jsonFile);
            return "{}";
        }

        MenuNode newRoot = new MenuNode("Root", "ROOT");
        for (MenuNode root : allRoots) {
            newRoot.addChild(root);
        }

        Map<String, MenuNode> fsPathMap = new HashMap<>();
        buildPathMap(newRoot, fsPathMap);

        File jsonFile = new File(homePath + "LeftMenu.json");
        //JSONObject oldJsonObj = jsonFile.exists() ? JSON.parseObject(FileUtil.readUtf8String(jsonFile)) : null;

        JSONObject merged = mergeJson(null, fsPathMap);
        String output = JSON.toJSONString(merged, true);
        FileUtil.writeUtf8String(output, jsonFile);
        return output;
    }

    private static void buildPathMap(MenuNode node, Map<String, MenuNode> map) {
        if (node == null) return;
        map.put(node.getPath(), node);
        if (node.getChildren() != null) {
            for (MenuNode child : node.getChildren()) {
                buildPathMap(child, map);
            }
        }
    }

    // 递归合并，保留旧字段
    private static JSONObject mergeJson(JSONObject oldNode, Map<String, MenuNode> fsMap) {
        if (oldNode == null) return convertToJson(fsMap.get("ROOT"));
        String path = oldNode.getString("path");
        if (!fsMap.containsKey(path)) return null; // 删除了

        MenuNode newNode = fsMap.get(path);
        JSONObject result = new JSONObject();

        result.put("name", newNode.getName());
        result.put("path", newNode.getPath());
        // 拷贝非标准字段
        for (Map.Entry<String, Object> entry : oldNode.entrySet()) {
            String key = entry.getKey();
            if (!"name".equals(key) && !"path".equals(key) && !"children".equals(key)) {
                result.put(key, entry.getValue());
            }
        }

        List<MenuNode> newChildren = newNode.getChildren();
        List<JSONObject> mergedChildren = new ArrayList<>();

        Map<String, MenuNode> childFsMap = new HashMap<>();
        if (newChildren != null) {
            for (MenuNode child : newChildren) {
                childFsMap.put(child.getPath(), child);
            }
        }

        Set<String> mergedPaths = new HashSet<>();
        if (oldNode.containsKey("children")) {
            for (Object oldChildObj : oldNode.getJSONArray("children")) {
                JSONObject oldChild = (JSONObject) oldChildObj;
                JSONObject mergedChild = mergeJson(oldChild, fsMap);
                if (mergedChild != null) {
                    mergedChildren.add(mergedChild);
                    mergedPaths.add(mergedChild.getString("path"));
                }
            }
        }

        // 添加新增子节点
        if (newChildren != null) {
            for (MenuNode child : newChildren) {
                if (!mergedPaths.contains(child.getPath())) {
                    mergedChildren.add(convertToJson(child));
                }
            }
        }

        if (!mergedChildren.isEmpty()) {
            result.put("children", mergedChildren);
        }

        return result;
    }

    private static JSONObject convertToJson(MenuNode node) {
        if (node == null) return null;
        JSONObject obj = new JSONObject();
        obj.put("name", node.getName());
        obj.put("path", node.getPath());
        if (node.getChildren() != null) {
            List<JSONObject> children = new ArrayList<>();
            for (MenuNode child : node.getChildren()) {
                JSONObject c = convertToJson(child);
                if (c != null) {
                    children.add(c);
                }
            }
            obj.put("children", children);
        }
        return obj;
    }

    private static MenuNode directoryToJson(File dir) {
        MenuNode node = new MenuNode(dir.getName(), dir.getAbsolutePath());
        File[] files = dir.listFiles();
        if (files == null) return null;

        for (File file : files) {
            if (file.isDirectory()) {
                MenuNode child = directoryToJson(file);
                if (child != null) node.addChild(child);
            } else if (file.getName().endsWith(".xml")) {
                try {
                    node.addChild(new MenuNode(file.getName().split("\\.")[0], file.getAbsolutePath()));
                } catch (Exception e) {
                    System.err.println("读取失败：" + file.getAbsolutePath());
                }
            }
        }
        return node.getChildren() == null || node.getChildren().isEmpty() ? null : node;
    }
}
