/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.util.AttributeSet;

import com.android.launcher3.dragndrop.DragController;

public class AppInfoDropTargetBar extends BaseDropTargetBar {
    private ButtonDropTarget mAppInfoDropTarget;

    public AppInfoDropTargetBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppInfoDropTargetBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // Get the individual components
        mAppInfoDropTarget = (ButtonDropTarget) mDropTargetBar.findViewById(R.id.info_target_text);

        mAppInfoDropTarget.setDropTargetBar(this);
    }

    @Override
    public void setup(Launcher launcher, DragController dragController) {
        dragController.addDragListener(this);

        dragController.addDragListener(mAppInfoDropTarget);

        dragController.addDropTarget(mAppInfoDropTarget);

        mAppInfoDropTarget.setLauncher(launcher);
    }

    @Override
    public void showDropTargets() {
        animateDropTargetBarToAlpha(1f, DEFAULT_DRAG_FADE_DURATION);
    }

    @Override
    public void hideDropTargets() {
        animateDropTargetBarToAlpha(0f, DEFAULT_DRAG_FADE_DURATION);
    }

    private void animateDropTargetBarToAlpha(float alpha, int duration) {
        animateViewAlpha(mDropTargetBarAnimator, mDropTargetBar, alpha,duration);
    }

    @Override
    public void enableAccessibleDrag(boolean enable) {
        mAppInfoDropTarget.enableAccessibleDrag(enable);
    }
}
