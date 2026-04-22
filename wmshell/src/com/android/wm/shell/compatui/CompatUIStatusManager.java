/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.compatui;

import android.annotation.NonNull;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Handle the visibility state of the Compat UI components. */
public class CompatUIStatusManager {

    private static final int COMPAT_UI_EDUCATION_UNDEFINED = -1;
    public static final int COMPAT_UI_EDUCATION_HIDDEN = 0;
    public static final int COMPAT_UI_EDUCATION_VISIBLE = 1;

    @NonNull
    private final IntConsumer mWriter;
    @NonNull
    private final IntSupplier mReader;

    private int mCurrentValue = COMPAT_UI_EDUCATION_UNDEFINED;

    public CompatUIStatusManager(@NonNull IntConsumer writer, @NonNull IntSupplier reader) {
        mWriter = writer;
        mReader = reader;
    }

    public CompatUIStatusManager() {
        this(i -> {
        }, () -> COMPAT_UI_EDUCATION_HIDDEN);
    }

    void onEducationShown() {
        if (mCurrentValue != COMPAT_UI_EDUCATION_VISIBLE) {
            mCurrentValue = COMPAT_UI_EDUCATION_VISIBLE;
            mWriter.accept(mCurrentValue);
        }
    }

    void onEducationHidden() {
        if (mCurrentValue != COMPAT_UI_EDUCATION_HIDDEN) {
            mCurrentValue = COMPAT_UI_EDUCATION_HIDDEN;
            mWriter.accept(mCurrentValue);
        }
    }

    boolean isEducationVisible() {
        return getCurrentValue() == COMPAT_UI_EDUCATION_VISIBLE;
    }

    private int getCurrentValue() {
        if (mCurrentValue == COMPAT_UI_EDUCATION_UNDEFINED) {
            mCurrentValue = mReader.getAsInt();
        }
        return mCurrentValue;
    }
}