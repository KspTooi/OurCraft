package com.ksptool.ourcraft.sharedcore;

import com.ksptool.ourcraft.sharedcore.blocks.inner.SharedBlock;
import com.ksptool.ourcraft.sharedcore.enums.BlockEnums;
import com.ksptool.ourcraft.sharedcore.world.BlockState;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 全局方块状态调色板类，管理所有方块状态到ID的映射
 */
@Getter
@Slf4j
public class GlobalPalette {

    //实例
    private static final GlobalPalette INSTANCE = new GlobalPalette();

    //方块状态列表
    private final List<BlockState> stateList;

    //方块状态到ID的映射
    private final Map<BlockState, Integer> stateToId;

    //是否已烘焙
    @Setter
    private boolean baked = false;

    private GlobalPalette() {
        this.stateList = new CopyOnWriteArrayList<>();
        this.stateToId = new ConcurrentHashMap<>();
    }

    public static GlobalPalette getInstance() {
        return INSTANCE;
    }

    public void reBake(){
        this.baked = false;
        this.bake();
    }

    public void bake() {
        if (baked) {
            return;
        }

        Registry registry = Registry.getInstance();
        Map<StdRegName, SharedBlock> blocks = registry.getAllBlocks();

        SharedBlock airSharedBlock = registry.getBlock(BlockEnums.AIR.getStdRegName());
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
        
        otherStates.sort(Comparator.comparing(a -> a.getSharedBlock().getStdRegName().getValue()));
        
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
            return stateList.getFirst();
        }
        return stateList.get(id);
    }

    public int getStateCount() {
        return stateList.size();
    }

    public void clear() {
        stateList.clear();
        stateToId.clear();
        baked = false;
        log.info("全局调色板已清空");
    }

}

