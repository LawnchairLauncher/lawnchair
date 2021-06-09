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

import static com.android.launcher3.AbstractFloatingView.TYPE_ALL;
import static com.android.systemui.shared.system.ViewTreeObserverWrapper.InsetsInfo.TOUCHABLE_INSETS_FRAME;
import static com.android.systemui.shared.system.ViewTreeObserverWrapper.InsetsInfo.TOUCHABLE_INSETS_REGION;

import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import androidx.annotation.NonNull;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.R;
import com.android.launcher3.anim.AlphaUpdateListener;
import com.android.systemui.shared.system.ViewTreeObserverWrapper.InsetsInfo;

/**
 * Controller for taskbar icon UI
 */
public class TaskbarIconController {

    private final TaskbarActivityContext mActivity;
    private final TaskbarDragLayer mDragLayer;
    private final NavbarButtonUIController mNavbarButtonUIController;

    private final TaskbarView mTaskbarView;

    @NonNull
    private TaskbarUIController mUIController = TaskbarUIController.DEFAULT;

    TaskbarIconController(TaskbarActivityContext activity, TaskbarDragLayer dragLayer,
            NavbarButtonUIController navbarButtonUIController) {
        mActivity = activity;
        mDragLayer = dragLayer;
        mNavbarButtonUIController = navbarButtonUIController;
        mTaskbarView = mDragLayer.findViewById(R.id.taskbar_view);
    }

    public void init(OnClickListener clickListener, OnLongClickListener longClickListener) {
        mTaskbarView.init(clickListener, longClickListener);
        mTaskbarView.getLayoutParams().height = mActivity.getDeviceProfile().taskbarSize;

        mDragLayer.init(new TaskbarDragLayerCallbacks(), mTaskbarView);
    }

    public void onDestroy() {
        mDragLayer.onDestroy();
    }

    public void setUIController(@NonNull TaskbarUIController uiController) {
        mUIController = uiController;
    }

    /**
     * Should be called when the IME visibility changes, so we can hide/show Taskbar accordingly.
     */
    public void setImeIsVisible(boolean isImeVisible) {
        mTaskbarView.setTouchesEnabled(!isImeVisible);
    }

    /**
     * Callbacks for {@link TaskbarDragLayer} to interact with the icon controller
     */
    public class TaskbarDragLayerCallbacks {

        /**
         * Called to update the touchable insets
         */
        public void updateInsetsTouchability(InsetsInfo insetsInfo) {
            insetsInfo.touchableRegion.setEmpty();
            if (mDragLayer.getAlpha() < AlphaUpdateListener.ALPHA_CUTOFF_THRESHOLD) {
                // Let touches pass through us.
                insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION);
            } else if (mNavbarButtonUIController.isImeVisible()) {
                insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_FRAME);
            } else if (!mUIController.isTaskbarTouchable()) {
                // Let touches pass through us.
                insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION);
            } else if (mTaskbarView.areIconsVisible()) {
                // Buttons are visible, take over the full taskbar area
                insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_FRAME);
            } else {
                mNavbarButtonUIController.addVisibleButtonsRegion(
                        mDragLayer, insetsInfo.touchableRegion);
                insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION);
            }

            // TaskbarContainerView provides insets to other apps based on contentInsets. These
            // insets should stay consistent even if we expand TaskbarContainerView's bounds, e.g.
            // to show a floating view like Folder. Thus, we set the contentInsets to be where
            // mTaskbarView is, since its position never changes and insets rather than overlays.
            insetsInfo.contentInsets.left = mTaskbarView.getLeft();
            insetsInfo.contentInsets.top = mTaskbarView.getTop();
            insetsInfo.contentInsets.right = mDragLayer.getWidth() - mTaskbarView.getRight();
            insetsInfo.contentInsets.bottom = mDragLayer.getHeight() - mTaskbarView.getBottom();
        }

        public void onDragLayerViewRemoved() {
            if (AbstractFloatingView.getOpenView(mActivity, TYPE_ALL) == null) {
                mActivity.setTaskbarWindowFullscreen(false);
            }
        }
    }
}
