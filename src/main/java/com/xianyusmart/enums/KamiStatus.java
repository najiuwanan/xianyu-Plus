package com.xianyusmart.enums;

import lombok.Getter;

@Getter
public enum KamiStatus {
    AVAILABLE(0),
    DELIVERED(1),
    RESERVED(2),
    REVIEW_REQUIRED(3);

    private final int code;

    KamiStatus(int code) {
        this.code = code;
    }
}
