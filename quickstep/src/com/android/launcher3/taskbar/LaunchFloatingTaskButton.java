/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.launcher3.taskbar;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.icons.FastBitmapDrawable;

/**
 * Button in Taskbar that opens something in a floating task.
 */
public class LaunchFloatingTaskButton extends BubbleTextView {

    public LaunchFloatingTaskButton(Context context) {
        this(context, null);
    }

    public LaunchFloatingTaskButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LaunchFloatingTaskButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        Context theme = new ContextThemeWrapper(context, R.style.AllAppsButtonTheme);
        Bitmap bitmap = LauncherAppState.getInstance(context).getIconCache().getIconFactory()
                .createScaledBitmapWithShadow(
                        theme.getDrawable(R.drawable.ic_floating_task_button));
        setIcon(new FastBitmapDrawable(bitmap));
    }
}
