/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.shared.system;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.RemoteAnimationTarget;

import com.android.systemui.shared.recents.model.ThumbnailData;

import java.util.HashMap;

public interface RecentsAnimationListener {
    /**
     * Called when the animation into Recents can start. This call is made on the binder thread.
     */
    void onAnimationStart(RecentsAnimationControllerCompat controller,
            RemoteAnimationTarget[] apps, RemoteAnimationTarget[] wallpapers,
            Rect homeContentInsets, Rect minimizedHomeBounds, Bundle extras);

    /**
     * Called when the animation into Recents was canceled. This call is made on the binder thread.
     */
    void onAnimationCanceled(HashMap<Integer, ThumbnailData> thumbnailDatas);

    /**
     * Called when the task of an activity that has been started while the recents animation
     * was running becomes ready for control.
     */
    void onTasksAppeared(RemoteAnimationTarget[] app);

    /**
     * Called to request that the current task tile be switched out for a screenshot (if not
     * already). Once complete, onFinished should be called.
     * @return true if this impl will call onFinished. No other onSwitchToScreenshot impls will
     *         be called afterwards (to avoid multiple calls to onFinished).
     */
    default boolean onSwitchToScreenshot(Runnable onFinished) {
        return false;
    }
}
