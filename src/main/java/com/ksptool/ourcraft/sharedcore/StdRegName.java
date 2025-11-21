package com.ksptool.ourcraft.sharedcore;

import java.util.regex.Pattern;

/**
 * 标准化注册表名
 * 用于规范化注册表名的格式 namespace:itemName
 * 例如：core:stone
 */
public class StdRegName {

    /**
     * 预编译正则表达式，提高性能
     * 允许字符：a-z, A-Z, 0-9, 下划线(_), 点(.), 减号(-)
     * 必须包含且仅包含一个冒号(:)
     */
    private static final Pattern STD_REG_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+$");

    private final String value;

    public StdRegName(String value) {

        if (!isValid(value)) {
            throw new IllegalArgumentException("Invalid registry name: " + value);
        }

        this.value = value;
    }

    public static StdRegName of(String value) {
        return new StdRegName(value);
    }

    public static StdRegName of(String namespace, String item) {
        return new StdRegName(namespace + ":" + item);
    }

    public String getValue() {
        return value;
    }

    public String getNamespace() {
        return value.split(":")[0];
    }

    public String getItem() {
        return value.split(":")[1];
    }

    /**
     * 验证注册表名是否合法
     * 必须符合 namespace:itemName 的格式 不能有空格、特殊字符、中文 长度不能大于1024个字符
     * @return 是否合法
     */
    private boolean isValid(String name) {
        //空值校验
        if (name == null || name.isEmpty()) {
            return false;
        }

        //长度校验 (最大1024字符)
        if (name.length() > 1024) {
            return false;
        }

        //格式校验 (正则匹配 namespace:path)
        //使用预编译的 Pattern 避免重复编译开销
        return STD_REG_NAME_PATTERN.matcher(name).matches();
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        StdRegName other = (StdRegName) obj;
        return value.equals(other.value);
    }

}
