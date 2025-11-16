package com.ksptool.ourcraft.sharedcore.block;

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

    //方块命名空间ID
    private final String namespacedID;

    //方块耐久度
    private final float durability;

    //方块挖掘等级
    private final int miningLevel;

    //方块质量(以KG计) 默认1000KG
    private final float mass = 1000;
    
    //方块体积(以L计)  默认1000L
    private final float volume = 1000;

    //方块属性列表
    private final List<BlockProperty<?>> properties;

    //方块默认状态
    private BlockState defaultState;
    
    //方块所有状态
    private List<BlockState> allStates;

    public SharedBlock(String namespacedID, float durability, int miningLevel) {
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
