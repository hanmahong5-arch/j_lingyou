package red.jiuzhou.ui.form;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.pattern.model.TemplateParam;
import red.jiuzhou.pattern.model.TemplateParam.ParamType;
import red.jiuzhou.ui.components.BonusAttrSelector;
import red.jiuzhou.ui.components.ReferenceSelector;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能表单构建器
 *
 * <p>根据模板参数定义（TemplateParam）自动构建用户输入表单。
 * 智能识别字段类型，渲染对应的输入控件（文本框、数字框、下拉框、引用选择器等），
 * 按分组组织表单布局，提供用户友好的数据输入界面。
 *
 * <p><b>核心功能：</b>
 * <ul>
 *   <li>根据字段类型自动选择输入控件</li>
 *   <li>按 displayGroup 分组（使用 TitledPane）</li>
 *   <li>按 displayOrder 排序</li>
 *   <li>显示字段提示（displayHint）</li>
 *   <li>支持默认值填充</li>
 *   <li>参数验证（类型、范围、必填项）</li>
 *   <li>获取用户输入的值（Map<String, String>）</li>
 * </ul>
 *
 * <p><b>支持的字段类型：</b>
 * <table>
 *   <tr><th>类型</th><th>控件</th><th>描述</th></tr>
 *   <tr><td>STRING</td><td>TextField</td><td>文本输入</td></tr>
 *   <tr><td>INTEGER</td><td>Spinner</td><td>整数输入</td></tr>
 *   <tr><td>DECIMAL</td><td>TextField</td><td>小数输入</td></tr>
 *   <tr><td>BOOLEAN</td><td>CheckBox</td><td>布尔选择</td></tr>
 *   <tr><td>ENUM</td><td>ComboBox</td><td>枚举选择</td></tr>
 *   <tr><td>REFERENCE</td><td>ReferenceSelector</td><td>引用选择（ID下拉）</td></tr>
 *   <tr><td>BONUS_ATTR</td><td>BonusAttrSelector</td><td>属性增益选择</td></tr>
 * </table>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 构建表单
 * SmartFormBuilder builder = new SmartFormBuilder();
 * VBox formPanel = builder.buildForm("items", params);
 *
 * // 获取用户输入
 * Map<String, String> values = builder.getFormValues();
 *
 * // 验证输入
 * List<String> errors = builder.validateForm();
 * }</pre>
 *
 * @author Claude
 * @version 1.0
 * @since 2025-12-30
 */
public class SmartFormBuilder {
    private static final Logger log = LoggerFactory.getLogger(SmartFormBuilder.class);

    private String tableName;
    private List<TemplateParam> params;
    private Map<String, Node> inputControls;
    private VBox formPanel;

    // ========== 构造函数 ==========

    public SmartFormBuilder() {
        this.inputControls = new LinkedHashMap<>();
    }

    // ========== 核心方法 ==========

    /**
     * 构建表单
     *
     * @param tableName 表名（用于日志）
     * @param params 参数定义列表
     * @return 表单面板（VBox）
     */
    public VBox buildForm(String tableName, List<TemplateParam> params) {
        this.tableName = tableName;
        this.params = params;
        this.inputControls.clear();

        log.info("开始构建表单: 表={}, 参数数={}", tableName, params.size());

        // 创建主面板
        formPanel = new VBox(10);
        formPanel.setPadding(new Insets(10));

        // 按分组组织参数
        Map<String, List<TemplateParam>> groupedParams = groupParameters(params);

        // 为每个分组创建 TitledPane
        for (Map.Entry<String, List<TemplateParam>> entry : groupedParams.entrySet()) {
            String group = entry.getKey();
            List<TemplateParam> groupParams = entry.getValue();

            TitledPane groupPane = createGroupPane(group, groupParams);
            formPanel.getChildren().add(groupPane);
        }

        log.info("表单构建完成: {} 个分组, {} 个字段", groupedParams.size(), inputControls.size());
        return formPanel;
    }

    /**
     * 按分组组织参数
     */
    private Map<String, List<TemplateParam>> groupParameters(List<TemplateParam> params) {
        // 按 displayGroup 分组，按 displayOrder 排序
        Map<String, List<TemplateParam>> grouped = new LinkedHashMap<>();

        for (TemplateParam param : params) {
            String group = param.getDisplayGroup() != null ? param.getDisplayGroup() : "基本信息";

            grouped.computeIfAbsent(group, k -> new ArrayList<>()).add(param);
        }

        // 排序每个分组内的参数
        for (List<TemplateParam> groupParams : grouped.values()) {
            groupParams.sort(Comparator.comparing(p ->
                    p.getDisplayOrder() != null ? p.getDisplayOrder() : 999
            ));
        }

        return grouped;
    }

    /**
     * 创建分组面板
     */
    private TitledPane createGroupPane(String groupName, List<TemplateParam> params) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        // 设置列约束
        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setMinWidth(120);
        labelCol.setHgrow(Priority.NEVER);

        ColumnConstraints inputCol = new ColumnConstraints();
        inputCol.setHgrow(Priority.ALWAYS);

        grid.getColumnConstraints().addAll(labelCol, inputCol);

        // 添加字段
        int row = 0;
        for (TemplateParam param : params) {
            // 标签
            Label label = new Label(param.getParamName() + ":");
            if (param.getIsRequired()) {
                label.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            }
            GridPane.setConstraints(label, 0, row);

            // 输入控件
            Node inputControl = createInputControl(param);
            GridPane.setConstraints(inputControl, 1, row);
            GridPane.setHgrow(inputControl, Priority.ALWAYS);

            // 保存控件引用
            inputControls.put(param.getParamCode(), inputControl);

            grid.getChildren().addAll(label, inputControl);

            // 提示信息（如果有）
            if (param.getDisplayHint() != null && !param.getDisplayHint().isEmpty()) {
                Label hint = new Label(param.getDisplayHint());
                hint.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");
                GridPane.setConstraints(hint, 1, row + 1);
                GridPane.setColumnSpan(hint, 2);
                grid.getChildren().add(hint);
                row++;
            }

            row++;
        }

        TitledPane pane = new TitledPane(groupName, grid);
        pane.setExpanded(true);
        return pane;
    }

    /**
     * 根据参数类型创建输入控件
     */
    private Node createInputControl(TemplateParam param) {
        ParamType type = param.getParamType();

        return switch (type) {
            case INTEGER -> createIntegerInput(param);
            case DECIMAL -> createDecimalInput(param);
            case BOOLEAN -> createBooleanInput(param);
            case ENUM -> createEnumInput(param);
            case REFERENCE -> createReferenceInput(param);
            case BONUS_ATTR -> createBonusAttrInput(param);
            default -> createStringInput(param);
        };
    }

    /**
     * 创建字符串输入框
     */
    private Node createStringInput(TemplateParam param) {
        TextField textField = new TextField();
        textField.setPromptText(param.getParamName());

        if (param.getDefaultValue() != null) {
            textField.setText(param.getDefaultValue());
        }

        textField.setMaxWidth(Double.MAX_VALUE);
        return textField;
    }

    /**
     * 创建整数输入框
     */
    private Node createIntegerInput(TemplateParam param) {
        int min = param.getMinValue() != null ? Integer.parseInt(param.getMinValue()) : Integer.MIN_VALUE;
        int max = param.getMaxValue() != null ? Integer.parseInt(param.getMaxValue()) : Integer.MAX_VALUE;
        int initial = param.getDefaultValue() != null ? Integer.parseInt(param.getDefaultValue()) : 0;

        Spinner<Integer> spinner = new Spinner<>(min, max, initial);
        spinner.setEditable(true);
        spinner.setMaxWidth(Double.MAX_VALUE);

        return spinner;
    }

    /**
     * 创建小数输入框
     */
    private Node createDecimalInput(TemplateParam param) {
        TextField textField = new TextField();
        textField.setPromptText("0.0");

        if (param.getDefaultValue() != null) {
            textField.setText(param.getDefaultValue());
        }

        // 限制只能输入数字和小数点
        textField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("-?\\d*\\.?\\d*")) {
                textField.setText(oldVal);
            }
        });

        textField.setMaxWidth(Double.MAX_VALUE);
        return textField;
    }

    /**
     * 创建布尔输入框
     */
    private Node createBooleanInput(TemplateParam param) {
        CheckBox checkBox = new CheckBox();

        if (param.getDefaultValue() != null) {
            boolean defaultValue = "true".equalsIgnoreCase(param.getDefaultValue()) ||
                                  "1".equals(param.getDefaultValue());
            checkBox.setSelected(defaultValue);
        }

        return checkBox;
    }

    /**
     * 创建枚举下拉框
     */
    private Node createEnumInput(TemplateParam param) {
        ComboBox<String> comboBox = new ComboBox<>();

        if (param.getEnumValues() != null && !param.getEnumValues().isEmpty()) {
            // 解析枚举值（JSON数组）
            String[] values = param.getEnumValues()
                    .replaceAll("[\\[\\]\"]", "")
                    .split(",");
            comboBox.getItems().addAll(values);
        }

        if (param.getDefaultValue() != null) {
            comboBox.setValue(param.getDefaultValue());
        }

        comboBox.setMaxWidth(Double.MAX_VALUE);
        return comboBox;
    }

    /**
     * 创建引用选择器
     */
    private Node createReferenceInput(TemplateParam param) {
        // 从生成器配置中提取目标表
        String targetTable = extractTargetTableFromConfig(param);

        if (targetTable == null || targetTable.isEmpty()) {
            // 降级为文本输入
            log.warn("无法确定引用目标表: {}, 降级为文本输入", param.getParamCode());
            return createStringInput(param);
        }

        ReferenceSelector selector = new ReferenceSelector(targetTable, param.getParamName());

        if (param.getDefaultValue() != null) {
            selector.setSelectedId(param.getDefaultValue());
        }

        return selector;
    }

    /**
     * 创建属性增益选择器
     */
    private Node createBonusAttrInput(TemplateParam param) {
        BonusAttrSelector selector = new BonusAttrSelector();
        selector.setPrefHeight(400);

        // TODO: 从默认值加载已选属性
        // 默认值格式：{"physical_attack": "100", "max_hp": "500"}

        return selector;
    }

    /**
     * 从生成器配置中提取目标表
     */
    private String extractTargetTableFromConfig(TemplateParam param) {
        if (param.getGeneratorConfig() == null || param.getGeneratorConfig().isEmpty()) {
            return null;
        }

        try {
            // 解析 JSON 配置：{"target": "items.id"}
            String config = param.getGeneratorConfig();
            int start = config.indexOf("\"target\"") + 10;
            int end = config.indexOf("\"", start);
            String target = config.substring(start, end);

            // 提取表名
            if (target.contains(".")) {
                return target.substring(0, target.indexOf('.'));
            }
            return target;

        } catch (Exception e) {
            log.error("解析生成器配置失败: {}", param.getParamCode(), e);
            return null;
        }
    }

    // ========== 值获取和验证 ==========

    /**
     * 获取表单值
     *
     * @return 参数代码 → 值映射
     */
    public Map<String, String> getFormValues() {
        Map<String, String> values = new LinkedHashMap<>();

        for (Map.Entry<String, Node> entry : inputControls.entrySet()) {
            String paramCode = entry.getKey();
            Node control = entry.getValue();

            String value = extractValueFromControl(control);
            if (value != null && !value.isEmpty()) {
                values.put(paramCode, value);
            }
        }

        return values;
    }

    /**
     * 从控件提取值
     */
    private String extractValueFromControl(Node control) {
        if (control instanceof TextField textField) {
            return textField.getText();
        } else if (control instanceof Spinner<?> spinner) {
            return spinner.getValue().toString();
        } else if (control instanceof CheckBox checkBox) {
            return String.valueOf(checkBox.isSelected());
        } else if (control instanceof ComboBox<?> comboBox) {
            Object value = comboBox.getValue();
            return value != null ? value.toString() : "";
        } else if (control instanceof ReferenceSelector selector) {
            return selector.getSelectedId();
        } else if (control instanceof BonusAttrSelector selector) {
            // 属性选择器返回JSON格式
            Map<String, String> attrs = selector.getSelectedAttributes();
            if (attrs.isEmpty()) {
                return "";
            }
            // 简化：只返回第一个属性的值
            return attrs.values().iterator().next();
        }

        return "";
    }

    /**
     * 验证表单
     *
     * @return 错误列表（空列表表示验证通过）
     */
    public List<String> validateForm() {
        List<String> errors = new ArrayList<>();

        for (TemplateParam param : params) {
            String paramCode = param.getParamCode();
            Node control = inputControls.get(paramCode);

            if (control == null) {
                continue;
            }

            String value = extractValueFromControl(control);

            // 验证必填项
            if (param.getIsRequired() && (value == null || value.isEmpty())) {
                errors.add(String.format("字段 '%s' 是必填项", param.getParamName()));
                continue;
            }

            if (value == null || value.isEmpty()) {
                continue;
            }

            // 验证类型
            try {
                switch (param.getParamType()) {
                    case INTEGER -> Integer.parseInt(value);
                    case DECIMAL -> Double.parseDouble(value);
                }
            } catch (NumberFormatException e) {
                errors.add(String.format("字段 '%s' 的值 '%s' 格式不正确", param.getParamName(), value));
            }

            // 验证范围
            if (param.getParamType() == ParamType.INTEGER || param.getParamType() == ParamType.DECIMAL) {
                try {
                    double numValue = Double.parseDouble(value);

                    if (param.getMinValue() != null) {
                        double minValue = Double.parseDouble(param.getMinValue());
                        if (numValue < minValue) {
                            errors.add(String.format("字段 '%s' 的值 %s 小于最小值 %s",
                                    param.getParamName(), value, param.getMinValue()));
                        }
                    }

                    if (param.getMaxValue() != null) {
                        double maxValue = Double.parseDouble(param.getMaxValue());
                        if (numValue > maxValue) {
                            errors.add(String.format("字段 '%s' 的值 %s 大于最大值 %s",
                                    param.getParamName(), value, param.getMaxValue()));
                        }
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return errors;
    }

    /**
     * 设置表单值
     *
     * @param values 参数代码 → 值映射
     */
    public void setFormValues(Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String paramCode = entry.getKey();
            String value = entry.getValue();

            Node control = inputControls.get(paramCode);
            if (control != null) {
                setControlValue(control, value);
            }
        }
    }

    /**
     * 设置控件值
     */
    private void setControlValue(Node control, String value) {
        if (control instanceof TextField textField) {
            textField.setText(value);
        } else if (control instanceof Spinner<?> spinner) {
            try {
                @SuppressWarnings("unchecked")
                Spinner<Integer> intSpinner = (Spinner<Integer>) spinner;
                intSpinner.getValueFactory().setValue(Integer.parseInt(value));
            } catch (Exception e) {
                log.warn("设置Spinner值失败: {}", value, e);
            }
        } else if (control instanceof CheckBox checkBox) {
            checkBox.setSelected("true".equalsIgnoreCase(value) || "1".equals(value));
        } else if (control instanceof ComboBox<?> comboBox) {
            @SuppressWarnings("unchecked")
            ComboBox<String> stringComboBox = (ComboBox<String>) comboBox;
            stringComboBox.setValue(value);
        } else if (control instanceof ReferenceSelector selector) {
            selector.setSelectedId(value);
        }
    }

    /**
     * 清空表单
     */
    public void clearForm() {
        for (Node control : inputControls.values()) {
            if (control instanceof TextField textField) {
                textField.clear();
            } else if (control instanceof Spinner spinner) {
                // 重置Spinner值（使用原始类型避免泛型问题）
                resetSpinnerValue(spinner);
            } else if (control instanceof CheckBox checkBox) {
                checkBox.setSelected(false);
            } else if (control instanceof ComboBox<?> comboBox) {
                comboBox.setValue(null);
            } else if (control instanceof ReferenceSelector selector) {
                selector.setSelectedId(null);
            } else if (control instanceof BonusAttrSelector selector) {
                selector.clear();
            }
        }
    }

    /**
     * 重置Spinner的值（处理泛型类型问题）
     */
    @SuppressWarnings("unchecked")
    private void resetSpinnerValue(Spinner spinner) {
        var valueFactory = spinner.getValueFactory();
        if (valueFactory != null) {
            Object currentValue = valueFactory.getValue();
            if (currentValue instanceof Integer) {
                ((Spinner<Integer>) spinner).getValueFactory().setValue(0);
            } else if (currentValue instanceof Double) {
                ((Spinner<Double>) spinner).getValueFactory().setValue(0.0);
            }
        }
    }

    // ========== Getter 方法 ==========

    public VBox getFormPanel() {
        return formPanel;
    }

    public List<TemplateParam> getParams() {
        return params;
    }

    public Map<String, Node> getInputControls() {
        return inputControls;
    }
}
