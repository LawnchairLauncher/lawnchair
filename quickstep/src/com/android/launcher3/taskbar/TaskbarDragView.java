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

import android.graphics.drawable.Drawable;

import com.android.launcher3.R;
import com.android.launcher3.dragndrop.DragView;

/**
 * A DragView drawn/used by the Taskbar. Note that this is only for the internal drag-and-drop,
 * while the pre-drag is still in progress (i.e. when the long press popup is still open). After
 * that ends, we switch to a system drag and drop view instead.
 */
public class TaskbarDragView extends DragView<BaseTaskbarContext> {
    public TaskbarDragView(BaseTaskbarContext launcher, Drawable drawable, int registrationX,
            int registrationY, float initialScale, float scaleOnDrop, float finalScaleDps) {
        super(launcher, drawable, registrationX, registrationY, initialScale, scaleOnDrop,
                finalScaleDps);
    }

    @Override
    public void animateTo(int toTouchX, int toTouchY, Runnable onCompleteRunnable, int duration) {
        Runnable onAnimationEnd = () -> {
            if (onCompleteRunnable != null) {
                onCompleteRunnable.run();
            }
            mActivity.getDragLayer().removeView(this);
        };

        duration = Math.max(duration,
                getResources().getInteger(R.integer.config_dropAnimMinDuration));

        animate()
                .translationX(toTouchX - mRegistrationX)
                .translationY(toTouchY - mRegistrationY)
                .scaleX(mScaleOnDrop)
                .scaleY(mScaleOnDrop)
                .withEndAction(onAnimationEnd)
                .setDuration(duration)
                .start();
    }
}
