package com.ksptool.ourcraft.sharedcore.world;

import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.world.properties.BlockProperty;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 方块状态类，表示具有特定属性值的方块实例
 */
public class BlockState {

    //方块
    @Getter
    private final SharedBlock sharedBlock;

    //方块属性
    private final Map<BlockProperty<?>, Comparable<?>> properties;

    //哈希码
    private final int hashCode;

    public BlockState(SharedBlock sharedBlock, Map<BlockProperty<?>, Comparable<?>> properties) {
        this.sharedBlock = Objects.requireNonNull(sharedBlock);
        this.properties = new HashMap<>(Objects.requireNonNull(properties));
        this.hashCode = computeHashCode();
    }

    @SuppressWarnings("unchecked")
    public <T extends Comparable<T>> T get(BlockProperty<T> property) {
        Comparable<?> value = properties.get(property);
        if (value == null) {
            return property.getDefaultValue();
        }
        return (T) value;
    }

    public <T extends Comparable<T>> BlockState with(BlockProperty<T> property, T value) {
        if (!property.getAllowedValues().contains(value)) {
            throw new IllegalArgumentException("Value " + value + " is not allowed for property " + property.getName());
        }
        Map<BlockProperty<?>, Comparable<?>> newProperties = new HashMap<>(this.properties);
        newProperties.put(property, value);
        return new BlockState(this.sharedBlock, newProperties);
    }

    public boolean hasProperty(BlockProperty<?> property) {
        return properties.containsKey(property);
    }

    public Map<BlockProperty<?>, Comparable<?>> getProperties() {
        return new HashMap<>(properties);
    }

    private int computeHashCode() {
        int result = sharedBlock.hashCode();
        result = 31 * result + properties.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        BlockState that = (BlockState) obj;
        return sharedBlock.equals(that.sharedBlock) && properties.equals(that.properties);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(sharedBlock.getStdRegName());
        if (!properties.isEmpty()) {
            sb.append("[");
            boolean first = true;
            for (Map.Entry<BlockProperty<?>, Comparable<?>> entry : properties.entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                sb.append(entry.getKey().getName()).append("=").append(entry.getValue());
                first = false;
            }
            sb.append("]");
        }
        return sb.toString();
    }
}

