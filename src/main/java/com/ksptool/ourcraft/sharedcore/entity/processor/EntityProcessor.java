package com.ksptool.ourcraft.sharedcore.entity.processor;

import com.ksptool.ourcraft.sharedcore.entity.SharedEntity;

import java.util.List;

public interface EntityProcessor {

    void process(SharedEntity entity, double delta);

    void process(List<SharedEntity> entity, double delta);

}
