package com.ksptool.ourcraft.sharedcore.blocks.inner;

import com.ksptool.ourcraft.sharedcore.StdRegName;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;
import com.ksptool.ourcraft.sharedcore.world.properties.BlockProperty;
import com.ksptool.ourcraft.sharedcore.world.Registry;
import lombok.Getter;
import java.util.*;

/**
 * 方块基类，定义方块的基本属性和状态生成逻辑
 */
@Getter
public abstract class SharedBlock {

    //标准注册名
    private final StdRegName stdRegName;

    //耐久度
    private final float durability;

    //挖掘等级
    private final int miningLevel;

    //质量(以KG计) 默认1000KG
    private final double mass = 1000;
    
    //体积(以L计)  默认1000L
    private final double volume = 1000;

    //属性列表
    private final List<BlockProperty<?>> properties;

    //默认状态
    private BlockState defaultState;
    
    //所有状态
    private List<BlockState> allStates;

    public SharedBlock(StdRegName stdRegName, float durability, int miningLevel) {

        if(stdRegName == null){
            throw new IllegalArgumentException("StdRegName is null!");
        }

        this.stdRegName = stdRegName;
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

    public abstract String getTextureName(SharedWorld world, int face, BlockState state);

    /**
     * 当一个方块被添加到世界时调用
     * @param world 世界实例
     * @param x 方块X坐标
     * @param y 方块Y坐标
     * @param z 方块Z坐标
     * @param state 方块状态
     */
    public void onBlockAdded(SharedWorld world, int x, int y, int z, BlockState state) {
    }

    /**
     * 当一个方块被移除时调用
     * @param world 世界实例
     * @param x 方块X坐标
     * @param y 方块Y坐标
     * @param z 方块Z坐标
     * @param state 方块状态
     */
    public void onBlockRemoved(SharedWorld world, int x, int y, int z, BlockState state) {
    }

    /**
     * 每个游戏刻的随机更新
     * @param world 世界实例
     * @param x 方块X坐标
     * @param y 方块Y坐标
     * @param z 方块Z坐标
     * @param state 方块状态
     */
    public void randomTick(SharedWorld world, int x, int y, int z, BlockState state) {
    }

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
        com.ksptool.ourcraft.sharedcore.BlockType.registerBlocks(registry);
    }
}
