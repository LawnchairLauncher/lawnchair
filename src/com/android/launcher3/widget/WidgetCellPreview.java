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
package com.android.launcher3.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

/**
 * View group managing the widget preview: either using a {@link WidgetImageView} or an actual
 * {@link LauncherAppWidgetHostView}.
 */
public class WidgetCellPreview extends FrameLayout {
    public WidgetCellPreview(Context context) {
        this(context, null);
    }

    public WidgetCellPreview(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetCellPreview(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        super.onInterceptTouchEvent(ev);
        return true;
    }

    /** Returns {@code true} if this container has a preview layout. */
    public boolean hasPreviewLayout() {
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof LauncherAppWidgetHostView) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@link LauncherAppWidgetHostView} if this container has a preview layout. Otherwise,
     * returns null.
     */
    @Nullable
    public LauncherAppWidgetHostView getPreviewLayout() {
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof LauncherAppWidgetHostView) {
                return (LauncherAppWidgetHostView) getChildAt(i);
            }
        }
        return null;
    }
}
