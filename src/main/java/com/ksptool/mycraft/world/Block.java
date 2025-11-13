package com.ksptool.mycraft.world;

import com.ksptool.mycraft.world.properties.BlockProperty;
import lombok.Getter;

import java.util.*;

/**
 * 方块基类，定义方块的基本属性和状态生成逻辑
 */
@Getter
public abstract class Block {

    //方块命名空间ID
    private final String namespacedID;

    //方块耐久度
    private final float durability;

    //方块挖掘等级
    private final int miningLevel;

    //方块属性列表
    private final List<BlockProperty<?>> properties;

    //方块默认状态
    private BlockState defaultState;
    
    //方块所有状态
    private List<BlockState> allStates;

    public Block(String namespacedID, float durability, int miningLevel) {
        this.namespacedID = Objects.requireNonNull(namespacedID);
        this.durability = durability;
        this.miningLevel = miningLevel;
        this.properties = new ArrayList<>();
        defineProperties();
        generateStates();
    }

    protected abstract void defineProperties();

    protected <T extends Comparable<T>> void addProperty(BlockProperty<T> property) {
        properties.add(property);
    }

    public List<BlockProperty<?>> getProperties() {
        return Collections.unmodifiableList(properties);
    }

    public boolean isSolid() {
        return true;
    }

    public boolean isFluid() {
        return false;
    }

    public abstract String getTextureName(int face, BlockState state);

    private void generateStates() {
        if (properties.isEmpty()) {
            Map<BlockProperty<?>, Comparable<?>> emptyProps = new HashMap<>();
            defaultState = new BlockState(this, emptyProps);
            allStates = Collections.singletonList(defaultState);
            return;
        }

        List<Map<BlockProperty<?>, Comparable<?>>> combinations = generateCombinations(0, new HashMap<>());
        allStates = new ArrayList<>();
        
        for (Map<BlockProperty<?>, Comparable<?>> props : combinations) {
            allStates.add(new BlockState(this, props));
        }
        
        defaultState = allStates.get(0);
    }

    @SuppressWarnings("unchecked")
    private List<Map<BlockProperty<?>, Comparable<?>>> generateCombinations(
            int index, 
            Map<BlockProperty<?>, Comparable<?>> current) {
        
        if (index >= properties.size()) {
            return Collections.singletonList(new HashMap<>(current));
        }

        List<Map<BlockProperty<?>, Comparable<?>>> results = new ArrayList<>();
        BlockProperty<?> property = properties.get(index);
        
        for (Comparable<?> value : property.getAllowedValues()) {
            Map<BlockProperty<?>, Comparable<?>> newCurrent = new HashMap<>(current);
            newCurrent.put(property, value);
            results.addAll(generateCombinations(index + 1, newCurrent));
        }
        
        return results;
    }

    public static void registerBlocks() {
        Registry registry = Registry.getInstance();
        registry.register(new com.ksptool.mycraft.world.blocks.AirBlock());
        registry.register(new com.ksptool.mycraft.world.blocks.GrassBlock());
        registry.register(new com.ksptool.mycraft.world.blocks.DirtBlock());
        registry.register(new com.ksptool.mycraft.world.blocks.StoneBlock());
        registry.register(new com.ksptool.mycraft.world.blocks.WoodBlock());
        registry.register(new com.ksptool.mycraft.world.blocks.LeavesBlock());
        registry.register(new com.ksptool.mycraft.world.blocks.WaterBlock());
    }
}
