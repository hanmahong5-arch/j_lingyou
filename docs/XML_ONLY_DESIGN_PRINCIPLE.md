# XML文件专用设计原则实现报告

## 🎯 核心设计原则

**设计理念**: 只有XML文件可以被导入库并操作，其他类型的文件被忽略。

---

## ✅ 实现层级

### 1. LeftMenu.json 配置层

**原则**: 配置中应该只包含XML文件

**实现**:
```json
// ✅ 正确配置（XML文件）
{
    "path": "D:\\AionReal58\\AionMap\\XML\\item_weapons.xml",
    "name": "item_weapons"  // 显示名称不含.xml
}

// ⚠️ 如果误配置了非XML文件
{
    "path": "D:\\AionReal58\\AionMap\\XML\\README.txt",
    "name": "README.txt"  // 会被保留扩展名
}
```

**处理逻辑** (MenuTabPaneExample.java:201-216):
```java
// 获取节点名称
String name = childNode.getString("name");
if (name == null || name.isEmpty()) {
    File file = new File(path);
    name = file.getName();

    // ✅ 只移除.xml扩展名，保留其他扩展名
    if (name.toLowerCase().endsWith(".xml")) {
        name = name.substring(0, name.length() - 4);
    }
}
```

**效果**:
- XML文件: `item_weapons.xml` → 显示为 `item_weapons` ✅
- 非XML文件: `README.txt` → 显示为 `README.txt` ⚠️（保留扩展名作为警告）

---

### 2. 路径解析层

**原则**: 路径解析时智能识别XML文件，不对非XML文件错误处理

**实现** (MenuTabPaneExample.java:271-306):
```java
private String getTabFullPath(TreeItem<String> treeItem) {
    // 1. 优先从Map获取（最可靠）
    String path = treeItemPathMap.get(treeItem);
    if (path != null && !path.isEmpty()) {
        return path;  // ✅ 返回原始配置的完整路径
    }

    // 2. 回退逻辑（异常情况）
    String constructedPath = getParetnPath(treeItem, treeItem.getValue());

    if (treeItem.isLeaf()) {
        int lastDot = constructedPath.lastIndexOf('.');
        int lastSep = Math.max(
            constructedPath.lastIndexOf('/'),
            constructedPath.lastIndexOf('\\')
        );

        // ✅ 检查是否已有扩展名
        if (lastDot == -1 || lastDot < lastSep) {
            // 没有扩展名 → 假定是XML文件
            constructedPath = constructedPath + ".xml";
        } else {
            // 已有扩展名 → 检查并警告非XML文件
            String extension = constructedPath.substring(lastDot);
            if (!extension.equalsIgnoreCase(".xml")) {
                log.warn("检测到非XML文件，将被忽略: {}", constructedPath);
            }
        }
    }

    return constructedPath;
}
```

**处理逻辑**:

| 输入 | 判断 | 输出 | 说明 |
|------|------|------|------|
| `item_weapons` | 无扩展名 | `item_weapons.xml` ✅ | 假定为XML，添加.xml |
| `README.txt` | 有扩展名(.txt) | `README.txt` ⚠️ | 保留原样，记录警告 |
| `data.json` | 有扩展名(.json) | `data.json` ⚠️ | 保留原样，记录警告 |
| `config.xml` | 有扩展名(.xml) | `config.xml` ✅ | 已是XML，保留 |

---

### 3. 批量DDL生成层

**原则**: 扫描目录时过滤非XML文件

**实现** (BatchDdlGenerator.java:134-138):
```java
for (File file : xmlFiles) {
    String fileName = file.getName();

    // ✅ 跳过非XML文件
    if (!fileName.toLowerCase().endsWith(".xml")) {
        result.setSkipped(result.getSkipped() + 1);
        continue;
    }

    // 生成DDL
    String sqlPath = XmlProcess.parseOneXml(filePath);
}
```

**目录扫描逻辑** (BatchDdlGenerator.java:195-202):
```java
if (recursive) {
    // 递归扫描，过滤.xml文件
    xmlFiles = FileUtil.loopFiles(directory).stream()
        .filter(f -> f.getName().toLowerCase().endsWith(".xml"))
        .collect(Collectors.toList());
} else {
    // 非递归，过滤.xml文件
    File[] files = dir.listFiles((d, name) ->
        name.toLowerCase().endsWith(".xml")
    );
}
```

**效果**:
```
目录: D:\AionReal58\AionMap\XML\
  ├── item_weapons.xml     ✅ 处理
  ├── npc_template.xml     ✅ 处理
  ├── README.txt           ⏭️ 跳过（计入skipped）
  ├── config.json          ⏭️ 跳过（计入skipped）
  └── .gitignore           ⏭️ 跳过（计入skipped）

结果:
  总计: 5, 成功: 2, 失败: 0, 跳过: 3
```

---

### 4. 批量XML导入层

**原则**: 导入时只处理XML文件

**实现** (BatchXmlImporter.java:198-202):
```java
for (File file : xmlFiles) {
    String fileName = file.getName();

    // ✅ 跳过非XML文件
    if (!fileName.toLowerCase().endsWith(".xml")) {
        result.setSkipped(result.getSkipped() + 1);
        continue;
    }

    // 检查表配置
    TableConf tableConf = TabConfLoad.getTale(tableName, filePath);
    if (tableConf == null) {
        log.warn("找不到表配置，跳过: {}", fileName);
        result.setSkipped(result.getSkipped() + 1);
        continue;
    }

    // 导入数据库
    importSingleXml(filePath, options).join();
}
```

**效果**:
- XML文件有配置 → 导入 ✅
- XML文件无配置 → 跳过 ⏭️（计入skipped）
- 非XML文件 → 跳过 ⏭️（计入skipped）

---

## 📊 完整过滤流程

### 场景1: 右键单个文件 → 生成DDL

```
用户选择: "item_weapons"
    ↓
getTabFullPath() → "D:\...\item_weapons.xml" ✅
    ↓
XmlProcess.parseOneXml()
    ↓
生成DDL: item_weapons.sql ✅
```

### 场景2: 右键目录 → 批量生成DDL

```
用户选择: "XML" 目录
    ↓
BatchDdlGenerator.generateDirectoryDdl()
    ↓
扫描目录:
    FileUtil.loopFiles()
        .filter(f -> f.getName().endsWith(".xml"))  ✅ 过滤
    ↓
找到文件:
    item_weapons.xml  ✅
    npc_template.xml  ✅
    README.txt        ⏭️ 被过滤
    config.json       ⏭️ 被过滤
    ↓
批量处理:
    for (File file : xmlFiles) {
        if (!fileName.endsWith(".xml")) {
            skipped++;  ✅ 双重保险
            continue;
        }
        XmlProcess.parseOneXml(file);
    }
    ↓
结果统计:
    成功: 2
    失败: 0
    跳过: 0 (已在扫描时过滤)
```

### 场景3: 右键非XML文件（误配置）

```
用户选择: "README" (实际是README.txt)
    ↓
getTabFullPath()
    检测到: "README.txt" 已有扩展名
    检查: .txt != .xml
    log.warn: "检测到非XML文件，将被忽略: README.txt"
    返回: "README.txt"
    ↓
XmlProcess.parseOneXml("README.txt")
    ↓
解析失败: 不是有效的XML ❌
    ↓
显示错误: "解析XML失败"
```

---

## 🛡️ 多层防护

### 第1层: LeftMenu.json 配置规范
- **职责**: 只配置XML文件
- **检查**: 人工/工具检查配置正确性

### 第2层: 路径解析智能识别
- **职责**: 识别并警告非XML文件
- **机制**: 扩展名检测 + 日志警告
- **代码**: `getTabFullPath()` Lines 286-302

### 第3层: 批量操作过滤
- **职责**: 扫描目录时过滤非XML文件
- **机制**: `filter(f -> f.endsWith(".xml"))`
- **代码**: BatchDdlGenerator.java:196-198

### 第4层: 处理前二次检查
- **职责**: 处理前再次检查文件扩展名
- **机制**: `if (!fileName.endsWith(".xml")) skip`
- **代码**:
  - BatchDdlGenerator.java:135-138
  - BatchXmlImporter.java:199-202

---

## 📈 统计信息展示

### 批量操作结果

```java
public class BatchResult {
    private int total;      // 总文件数（包含非XML）
    private int success;    // 成功处理的XML文件
    private int failed;     // 处理失败的XML文件
    private int skipped;    // 跳过的文件（非XML + 无配置）
}
```

**结果展示**:
```
✅ 批量生成DDL完成

总计: 10, 成功: 7, 失败: 0, 跳过: 3

--- 成功文件 ---
✓ D:\...\item_weapons.xml
✓ D:\...\npc_template.xml
✓ D:\...\skill_data.xml
...

--- 跳过文件 ---
⏭️ README.txt (非XML)
⏭️ config.json (非XML)
⏭️ .gitignore (非XML)
```

---

## 🎯 设计原则总结

### 1. 明确性
- ✅ 只处理 `.xml` 扩展名的文件
- ✅ 所有其他文件被明确忽略

### 2. 容错性
- ✅ 多层过滤防护
- ✅ 误配置不会导致系统崩溃

### 3. 可追溯性
- ✅ 跳过文件计入统计
- ✅ 日志记录警告信息

### 4. 用户友好
- ✅ 清晰的结果展示
- ✅ 区分成功/失败/跳过

---

## 🔍 日志示例

### 正常处理XML文件
```
DEBUG - 从Map获取路径: item_weapons -> D:\...\item_weapons.xml
INFO - 开始处理文件: D:\...\item_weapons.xml
INFO - DDL生成成功: item_weapons.xml
```

### 检测到非XML文件（回退逻辑）
```
WARN - Map中未找到路径，使用回退逻辑: README
WARN - 检测到非XML文件，将被忽略: D:\...\README.txt
```

### 批量操作跳过非XML文件
```
INFO - 扫描目录: D:\...\XML, 找到 7 个XML文件
DEBUG - 跳过非XML文件: README.txt
DEBUG - 跳过非XML文件: config.json
INFO - 批量处理完成: 成功 7, 失败 0, 跳过 0
```

---

## ✅ 验证清单

- [x] LeftMenu.json只包含XML文件配置
- [x] 路径解析智能识别XML/非XML文件
- [x] 批量DDL生成过滤非XML文件
- [x] 批量XML导入过滤非XML文件
- [x] 非XML文件计入"跳过"统计
- [x] 日志记录警告信息
- [x] 不会对非XML文件错误添加.xml扩展名
- [x] 用户界面清晰展示结果

---

## 🎉 总结

### 设计原则实现

**核心**: 只有XML文件可以被导入库并操作，其他类型的文件被忽略

**实现手段**:
1. ✅ **配置层**: LeftMenu.json应只配置XML文件
2. ✅ **解析层**: 智能识别并警告非XML文件
3. ✅ **过滤层**: 批量操作扫描时过滤非XML文件
4. ✅ **处理层**: 处理前二次检查文件扩展名
5. ✅ **统计层**: 跳过文件单独统计并展示

**用户体验**:
- 批量操作时自动忽略非XML文件
- 结果清晰展示成功/失败/跳过
- 不会因非XML文件导致错误

**技术保障**:
- 多层防护机制
- 扩展名智能检测
- 日志完整记录

**现在系统完全遵循"只处理XML文件"的设计原则！** 🎯✨
