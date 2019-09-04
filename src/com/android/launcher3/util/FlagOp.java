package com.android.launcher3.util;

public interface FlagOp {

    FlagOp NO_OP = i -> i;

    int apply(int flags);

    static FlagOp addFlag(int flag) {
        return i -> i | flag;
    }

    static FlagOp removeFlag(int flag) {
        return i -> i & ~flag;
    }
}
