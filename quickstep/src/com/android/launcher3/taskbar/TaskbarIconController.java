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

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import static com.android.systemui.shared.system.ViewTreeObserverWrapper.InsetsInfo.TOUCHABLE_INSETS_FRAME;
import static com.android.systemui.shared.system.ViewTreeObserverWrapper.InsetsInfo.TOUCHABLE_INSETS_REGION;

import android.graphics.Rect;
import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import androidx.annotation.NonNull;

import com.android.launcher3.R;
import com.android.launcher3.anim.AlphaUpdateListener;
import com.android.systemui.shared.system.ViewTreeObserverWrapper.InsetsInfo;

/**
 * Controller for taskbar icon UI
 */
public class TaskbarIconController {

    private final Rect mTempRect = new Rect();

    private final TaskbarActivityContext mActivity;
    private final TaskbarDragLayer mDragLayer;

    private final TaskbarView mTaskbarView;
    private final ImeBarView mImeBarView;

    @NonNull
    private TaskbarUIController mUIController = TaskbarUIController.DEFAULT;

    TaskbarIconController(TaskbarActivityContext activity, TaskbarDragLayer dragLayer) {
        mActivity = activity;
        mDragLayer = dragLayer;
        mTaskbarView = mDragLayer.findViewById(R.id.taskbar_view);
        mImeBarView = mDragLayer.findViewById(R.id.ime_bar_view);
    }

    public void init(OnClickListener clickListener, OnLongClickListener longClickListener) {
        mDragLayer.addOnLayoutChangeListener((v, a, b, c, d, e, f, g, h) ->
                mUIController.alignRealHotseatWithTaskbar());

        ButtonProvider buttonProvider = new ButtonProvider(mActivity);
        mImeBarView.init(buttonProvider);
        mTaskbarView.construct(clickListener, longClickListener, buttonProvider);
        mTaskbarView.getLayoutParams().height = mActivity.getDeviceProfile().taskbarSize;

        mDragLayer.init(new Callbacks(), mTaskbarView);
    }

    public void onDestroy() {
        mDragLayer.onDestroy();
    }

    public void setUIController(@NonNull TaskbarUIController uiController) {
        mUIController = uiController;
    }

    /**
     * When in 3 button nav, the above doesn't get called since we prevent sysui nav bar from
     * instantiating at all, which is what's responsible for sending sysui state flags over.
     *
     * @param vis IME visibility flag
     */
    public void updateImeStatus(int displayId, int vis, boolean showImeSwitcher) {
        if (displayId != mActivity.getDisplayId() || !mActivity.canShowNavButtons()) {
            return;
        }

        mImeBarView.setImeSwitcherVisibility(showImeSwitcher);
        setImeIsVisible((vis & InputMethodService.IME_VISIBLE) != 0);
    }

    /**
     * Should be called when the IME visibility changes, so we can hide/show Taskbar accordingly.
     */
    public void setImeIsVisible(boolean isImeVisible) {
        mTaskbarView.setTouchesEnabled(!isImeVisible);
        mUIController.onImeVisible(mDragLayer, isImeVisible);
    }

    /**
     * Callbacks for {@link TaskbarDragLayer} to interact with the icon controller
     */
    public class Callbacks {

        /**
         * Called to update the touchable insets
         */
        public void updateInsetsTouchability(InsetsInfo insetsInfo) {
            insetsInfo.touchableRegion.setEmpty();
            if (mDragLayer.getAlpha() < AlphaUpdateListener.ALPHA_CUTOFF_THRESHOLD) {
                // Let touches pass through us.
                insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION);
            } else if (mImeBarView.getVisibility() == VISIBLE) {
                insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_FRAME);
            } else if (!mUIController.isTaskbarTouchable()) {
                // Let touches pass through us.
                insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION);
            } else if (mTaskbarView.areIconsVisible()) {
                // Buttons are visible, take over the full taskbar area
                insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_FRAME);
            } else {
                if (mTaskbarView.mSystemButtonContainer.getVisibility() == VISIBLE) {
                    mDragLayer.getDescendantRectRelativeToSelf(
                            mTaskbarView.mSystemButtonContainer, mTempRect);
                    insetsInfo.touchableRegion.set(mTempRect);
                }
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
            int count = mDragLayer.getChildCount();
            // Ensure no other children present (like Folders, etc)
            for (int i = 0; i < count; i++) {
                View v = mDragLayer.getChildAt(i);
                if (!((v instanceof TaskbarView) || (v instanceof ImeBarView))) {
                    return;
                }
            }
            mActivity.setTaskbarWindowFullscreen(false);
        }

        public void updateImeBarVisibilityAlpha(float alpha) {
            if (!mActivity.canShowNavButtons()) {
                // TODO Remove sysui IME bar for gesture nav as well
                return;
            }
            mImeBarView.setAlpha(alpha);
            mImeBarView.setVisibility(alpha == 0 ? GONE : VISIBLE);
        }
    }
}
