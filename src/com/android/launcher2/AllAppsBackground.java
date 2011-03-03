/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.launcher2;

import com.android.launcher.R;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

/**
 * An implementation of PagedView that populates the pages of the workspace
 * with all of the user's applications.
 */
public class AllAppsBackground extends View {
    private Drawable mBackground;

    public AllAppsBackground(Context context) {
        this(context, null);
    }

    public AllAppsBackground(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsBackground(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mBackground = getResources().getDrawable(R.drawable.all_apps_bg_gradient);
    }

    @Override
    public void onDraw(Canvas canvas) {
        mBackground.setBounds(mScrollX, 0, mScrollX + getMeasuredWidth(),
                getMeasuredHeight());
        mBackground.draw(canvas);
    }
}
