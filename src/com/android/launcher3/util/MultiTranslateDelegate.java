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
package com.android.launcher3.util;

import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;

import android.view.View;

import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;

/**
 * A utility class to split translation components for various workspace items
 */
public class MultiTranslateDelegate {

    // offset related to reorder hint and bounce animations
    public static final int INDEX_REORDER_BOUNCE_OFFSET = 0;
    // offset related to previewing the new reordered position
    public static final int INDEX_REORDER_PREVIEW_OFFSET = 1;
    public static final int INDEX_MOVE_FROM_CENTER_ANIM = 2;

    // Specific for items in taskbar (icons, folders, qsb)
    public static final int INDEX_TASKBAR_ALIGNMENT_ANIM = 3;
    public static final int INDEX_TASKBAR_REVEAL_ANIM = 4;
    public static final int INDEX_TASKBAR_PINNING_ANIM = 5;

    // Affect all items inside of a MultipageCellLayout
    public static final int INDEX_CELLAYOUT_MULTIPAGE_SPACING = 3;

    // Specific for widgets
    public static final int INDEX_WIDGET_CENTERING = 4;

    // Specific for hotseat items when adjusting for bubbles
    public static final int INDEX_BUBBLE_ADJUSTMENT_ANIM = 3;

    public static final int COUNT = 6;

    private final MultiPropertyFactory<View> mTranslationX;
    private final MultiPropertyFactory<View> mTranslationY;

    public MultiTranslateDelegate(View target) {
        this(target, COUNT, COUNT);
    }

    public MultiTranslateDelegate(View target, int countX, int countY) {
        mTranslationX = new MultiPropertyFactory<>(target, VIEW_TRANSLATE_X, countX, Float::sum);
        mTranslationY = new MultiPropertyFactory<>(target, VIEW_TRANSLATE_Y, countY, Float::sum);
    }

    /**
     * Helper method to set both translations, x and y at a given index
     */
    public void setTranslation(int index, float x, float y) {
        getTranslationX(index).setValue(x);
        getTranslationY(index).setValue(y);
    }

    /**
     * Returns the translation x for the provided index
     */
    public MultiProperty getTranslationX(int index) {
        return mTranslationX.get(index);
    }

    /**
     * Returns the translation y for the provided index
     */
    public MultiProperty getTranslationY(int index) {
        return mTranslationY.get(index);
    }
}
