package com.ksptool.ourcraft.clientjme.commons;

import lombok.Getter;


@Getter
public enum FontSize {

    SMALL(12),
    NORMAL(16),
    LARGE(20),
    XLARGE(40);

    private final int size;

    FontSize(int size) {
        this.size = size;
    }

}
