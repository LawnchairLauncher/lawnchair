package com.android.launcher3.util;

public abstract class FlagOp {

    public static FlagOp NO_OP = new FlagOp() {};

    private FlagOp() {}

    public int apply(int flags) {
        return flags;
    }

    public static FlagOp addFlag(final int flag) {
        return new FlagOp() {
            @Override
            public int apply(int flags) {
                return flags | flag;
            }
        };
    }

    public static FlagOp removeFlag(final int flag) {
        return new FlagOp() {
            @Override
            public int apply(int flags) {
                return flags & ~flag;
            }
        };
    }
}
