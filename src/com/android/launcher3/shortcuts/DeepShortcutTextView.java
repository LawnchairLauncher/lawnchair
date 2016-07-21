/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.shortcuts;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;

/**
 * A {@link BubbleTextView} that has the shortcut icon on the left and drag handle on the right.
 */
public class DeepShortcutTextView extends BubbleTextView {

    public DeepShortcutTextView(Context context) {
        this(context, null, 0);
    }

    public DeepShortcutTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeepShortcutTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    /** Use the BubbleTextView icon for the start and the drag handle for the end. */
    protected void applyCompoundDrawables(Drawable icon) {
        Drawable dragHandle = getResources().getDrawable(R.drawable.deep_shortcuts_drag_handle);
        dragHandle.setBounds(0, 0, dragHandle.getIntrinsicWidth(), dragHandle.getIntrinsicHeight());
        setCompoundDrawablesRelative(icon, null, dragHandle, null);
    }
}
