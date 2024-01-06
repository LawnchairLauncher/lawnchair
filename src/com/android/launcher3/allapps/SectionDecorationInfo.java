/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.allapps;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;

public class SectionDecorationInfo {

    public static final int ROUND_NOTHING = 0;
    public static final int ROUND_TOP_LEFT = 1 << 1;
    public static final int ROUND_TOP_RIGHT = 1 << 2;
    public static final int ROUND_BOTTOM_LEFT = 1 << 3;
    public static final int ROUND_BOTTOM_RIGHT = 1 << 4;
    public static final int DECORATOR_ALPHA = 255;

    protected boolean mShouldDecorateItemsTogether;
    private SectionDecorationHandler mDecorationHandler;
    protected boolean mIsTopRound;
    protected boolean mIsBottomRound;

    public SectionDecorationInfo(Context context, int roundRegions, boolean decorateTogether) {
        mDecorationHandler =
                new SectionDecorationHandler(context, DECORATOR_ALPHA,
                        isFlagEnabled(roundRegions, ROUND_TOP_LEFT),
                        isFlagEnabled(roundRegions, ROUND_TOP_RIGHT),
                        isFlagEnabled(roundRegions, ROUND_BOTTOM_LEFT),
                        isFlagEnabled(roundRegions, ROUND_BOTTOM_RIGHT));
        mShouldDecorateItemsTogether = decorateTogether;
        mIsTopRound = isFlagEnabled(roundRegions, ROUND_TOP_LEFT) &&
                isFlagEnabled(roundRegions, ROUND_TOP_RIGHT);
        mIsBottomRound = isFlagEnabled(roundRegions, ROUND_BOTTOM_LEFT) &&
                isFlagEnabled(roundRegions, ROUND_BOTTOM_RIGHT);
    }

    public SectionDecorationInfo(Context context, @NonNull Bundle target,
            String targetLayoutType, @NonNull Bundle prevTarget, @NonNull Bundle nextTarget) {}

    public SectionDecorationHandler getDecorationHandler() {
        return mDecorationHandler;
    }

    private boolean isFlagEnabled(int canonicalFlag, int comparison) {
        return (canonicalFlag & comparison) != 0;
    }

    /**
     * Returns whether multiple {@link SectionDecorationInfo}s with the same sectionId should
     * be grouped together.
     */
    public boolean shouldDecorateItemsTogether() {
        return mShouldDecorateItemsTogether;
    }

    public boolean isTopRound() {
        return mIsTopRound;
    }

    public boolean isBottomRound() {
        return mIsBottomRound;
    }
}
