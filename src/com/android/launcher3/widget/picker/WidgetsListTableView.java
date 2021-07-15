/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.widget.picker;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TableLayout;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

/**
 * Extension of {@link TableLayout} to support the drawable states used by
 * {@link WidgetsListDrawableState}.
 */
public class WidgetsListTableView extends TableLayout {

    @Nullable private WidgetsListDrawableState mListDrawableState;

    public WidgetsListTableView(Context context) {
        super(context);
    }

    public WidgetsListTableView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** Sets the {@link WidgetsListDrawableState} and refreshes the background drawable. */
    @UiThread
    public void setListDrawableState(WidgetsListDrawableState state) {
        if (state == mListDrawableState) return;
        mListDrawableState = state;
        refreshDrawableState();
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        if (mListDrawableState == null) return super.onCreateDrawableState(extraSpace);
        // Augment the state set from the super implementation with the custom states from
        // mListDrawableState.
        int[] drawableState =
                super.onCreateDrawableState(extraSpace + mListDrawableState.mStateSet.length);
        mergeDrawableStates(drawableState, mListDrawableState.mStateSet);
        return drawableState;
    }
}
