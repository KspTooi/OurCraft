package com.ksptool.ourcraft.sharedcore.world;

import com.ksptool.ourcraft.sharedcore.BlockType;
import com.ksptool.ourcraft.sharedcore.block.SharedBlock;
import com.ksptool.ourcraft.sharedcore.world.BlockState;

import java.util.*;

/**
 * 全局方块状态调色板类，管理所有方块状态到ID的映射
 */
public class GlobalPalette {

    //实例
    private static final GlobalPalette INSTANCE = new GlobalPalette();

    //方块状态列表
    private final List<BlockState> stateList;

    //方块状态到ID的映射
    private final Map<BlockState, Integer> stateToId;

    //是否已烘焙
    private boolean baked = false;

    private GlobalPalette() {
        this.stateList = new ArrayList<>();
        this.stateToId = new HashMap<>();
    }

    public static GlobalPalette getInstance() {
        return INSTANCE;
    }

    public void bake() {
        if (baked) {
            return;
        }

        Registry registry = Registry.getInstance();
        Map<String, SharedBlock> blocks = registry.getAllBlocks();

        SharedBlock airSharedBlock = registry.get(BlockType.AIR.getNamespacedId());
        if (airSharedBlock == null) {
            throw new IllegalStateException("Air block must be registered before baking palette!");
        }

        List<BlockState> allStates = new ArrayList<>();

        for (SharedBlock sharedBlock : blocks.values()) {
            allStates.addAll(sharedBlock.getAllStates());
        }

        List<BlockState> sortedStates = new ArrayList<>();
        List<BlockState> airStates = new ArrayList<>();
        List<BlockState> otherStates = new ArrayList<>();
        
        for (BlockState state : allStates) {
            if (state.getSharedBlock() == airSharedBlock) {
                airStates.add(state);
            } else {
                otherStates.add(state);
            }
        }
        
        Collections.sort(otherStates, (a, b) -> 
            a.getSharedBlock().getNamespacedID().compareTo(b.getSharedBlock().getNamespacedID()));
        
        sortedStates.addAll(airStates);
        sortedStates.addAll(otherStates);

        int id = 0;
        for (BlockState state : sortedStates) {
            stateList.add(state);
            stateToId.put(state, id);
            id++;
        }

        baked = true;
    }

    public int getStateId(BlockState state) {
        if (!baked) {
            throw new IllegalStateException("Palette must be baked before use!");
        }
        Integer id = stateToId.get(state);
        if (id == null) {
            throw new IllegalArgumentException("BlockState not found in palette: " + state);
        }
        return id;
    }

    public BlockState getState(int id) {
        if (!baked) {
            throw new IllegalStateException("Palette must be baked before use!");
        }
        if (id < 0 || id >= stateList.size()) {
            return stateList.get(0);
        }
        return stateList.get(id);
    }

    public int getStateCount() {
        return stateList.size();
    }

    public boolean isBaked() {
        return baked;
    }

    public void clear() {
        stateList.clear();
        stateToId.clear();
        baked = false;
    }
    
    public List<BlockState> getStateList() {
        return stateList;
    }
    
    public Map<BlockState, Integer> getStateToId() {
        return stateToId;
    }
    
    public void setBaked(boolean baked) {
        this.baked = baked;
    }
    
}

