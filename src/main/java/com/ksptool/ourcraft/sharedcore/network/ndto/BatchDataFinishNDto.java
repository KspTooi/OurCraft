package com.ksptool.ourcraft.sharedcore.network.ndto;

/**
 * 批数据确认 (Batch Data Finish Network Data Transfer Object)
 */
public record BatchDataFinishNDto() {
    public static BatchDataFinishNDto of() {
        return new BatchDataFinishNDto();
    }
}
