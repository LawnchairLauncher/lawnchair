/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.launcher3;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

public class WallpaperRootView extends RelativeLayout {
    private final WallpaperPickerActivity a;
    public WallpaperRootView(Context context, AttributeSet attrs) {
        super(context, attrs);
        a = (WallpaperPickerActivity) context;
    }
    public WallpaperRootView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        a = (WallpaperPickerActivity) context;
    }

    protected boolean fitSystemWindows(Rect insets) {
        a.setWallpaperStripYOffset(insets.bottom);
        return true;
    }
}
