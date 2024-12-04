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
package com.android.launcher3.taskbar;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.views.ActivityContext;

/**
 * View that handles scrimming the taskbar and the inverted corners it draws. The scrim is used
 * when bubbles is expanded.
 */
public class TaskbarScrimView extends View {
    private final TaskbarBackgroundRenderer mRenderer;

    private boolean mShowScrim;

    public TaskbarScrimView(Context context) {
        this(context, null);
    }

    public TaskbarScrimView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskbarScrimView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TaskbarScrimView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mRenderer = new TaskbarBackgroundRenderer(ActivityContext.lookupContext(context));
        mRenderer.getPaint().setColor(getResources().getColor(
                android.R.color.system_neutral1_1000));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mShowScrim) {
            mRenderer.draw(canvas);
        }
    }

    /**
     * Sets the alpha of the taskbar scrim.
     * @param alpha the alpha of the scrim.
     */
    protected void setScrimAlpha(float alpha) {
        mShowScrim = alpha > 0f;
        mRenderer.getPaint().setAlpha((int) (alpha * 255));
        invalidate();
    }

    protected float getScrimAlpha() {
        return mRenderer.getPaint().getAlpha() / 255f;
    }

    /**
     * Sets the roundness of the round corner above Taskbar.
     * @param cornerRoundness 0 has no round corner, 1 has complete round corner.
     */
    protected void setCornerRoundness(float cornerRoundness) {
        mRenderer.setCornerRoundness(cornerRoundness);
        invalidate();
    }
}
