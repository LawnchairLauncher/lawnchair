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
package com.android.launcher3.taskbar.allapps;

import static android.view.KeyEvent.ACTION_UP;
import static android.view.KeyEvent.KEYCODE_BACK;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static com.android.systemui.shared.system.ViewTreeObserverWrapper.InsetsInfo.TOUCHABLE_INSETS_REGION;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.taskbar.BaseTaskbarContext;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarDragController;
import com.android.launcher3.taskbar.TaskbarStashController;
import com.android.launcher3.util.OnboardingPrefs;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.views.BaseDragLayer;
import com.android.systemui.shared.system.ViewTreeObserverWrapper;
import com.android.systemui.shared.system.ViewTreeObserverWrapper.InsetsInfo;
import com.android.systemui.shared.system.ViewTreeObserverWrapper.OnComputeInsetsListener;

/**
 * Window context for the taskbar all apps overlay.
 * <p>
 * All apps has its own window and needs a window context. Some properties are delegated to the
 * {@link TaskbarActivityContext} such as {@link DeviceProfile} and {@link PopupDataProvider}.
 */
class TaskbarAllAppsContext extends BaseTaskbarContext {
    private final TaskbarActivityContext mTaskbarContext;
    private final OnboardingPrefs<TaskbarAllAppsContext> mOnboardingPrefs;

    private final TaskbarAllAppsController mWindowController;
    private final TaskbarAllAppsViewController mAllAppsViewController;
    private final TaskbarDragController mDragController;
    private final TaskbarAllAppsDragLayer mDragLayer;
    private final TaskbarAllAppsContainerView mAppsView;

    TaskbarAllAppsContext(
            TaskbarActivityContext taskbarContext,
            TaskbarAllAppsController windowController,
            TaskbarStashController taskbarStashController) {
        super(taskbarContext.createWindowContext(TYPE_APPLICATION_OVERLAY, null));
        mTaskbarContext = taskbarContext;
        mDeviceProfile = taskbarContext.getDeviceProfile();
        mDragController = new TaskbarDragController(this);
        mOnboardingPrefs = new OnboardingPrefs<>(this, Utilities.getPrefs(this));

        mDragLayer = new TaskbarAllAppsDragLayer(this);
        TaskbarAllAppsSlideInView slideInView = (TaskbarAllAppsSlideInView) mLayoutInflater.inflate(
                R.layout.taskbar_all_apps, mDragLayer, false);
        mWindowController = windowController;
        mAllAppsViewController = new TaskbarAllAppsViewController(
                this,
                slideInView,
                windowController,
                taskbarStashController);
        mAppsView = slideInView.getAppsView();
    }

    TaskbarAllAppsViewController getAllAppsViewController() {
        return mAllAppsViewController;
    }

    @Override
    public TaskbarDragController getDragController() {
        return mDragController;
    }

    @Override
    public TaskbarAllAppsDragLayer getDragLayer() {
        return mDragLayer;
    }

    @Override
    public TaskbarAllAppsContainerView getAppsView() {
        return mAppsView;
    }

    @Override
    public OnboardingPrefs<TaskbarAllAppsContext> getOnboardingPrefs() {
        return mOnboardingPrefs;
    }

    @Override
    public boolean isBindingItems() {
        return mTaskbarContext.isBindingItems();
    }

    @Override
    public View.OnClickListener getItemOnClickListener() {
        return mTaskbarContext.getItemOnClickListener();
    }

    @Override
    public PopupDataProvider getPopupDataProvider() {
        return mTaskbarContext.getPopupDataProvider();
    }

    @Override
    public DotInfo getDotInfoForItem(ItemInfo info) {
        return mTaskbarContext.getDotInfoForItem(info);
    }

    @Override
    public void updateDeviceProfile(DeviceProfile dp) {
        mDeviceProfile = dp;
        dispatchDeviceProfileChanged();
    }

    @Override
    public void onDragStart() {}

    @Override
    public void onDragEnd() {
        mWindowController.maybeCloseWindow();
    }

    @Override
    public void onPopupVisibilityChanged(boolean isVisible) {}

    /** Root drag layer for this context. */
    private static class TaskbarAllAppsDragLayer extends
            BaseDragLayer<TaskbarAllAppsContext> implements OnComputeInsetsListener {

        private TaskbarAllAppsDragLayer(Context context) {
            super(context, null, 1);
            setClipChildren(false);
            recreateControllers();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            ViewTreeObserverWrapper.addOnComputeInsetsListener(
                    getViewTreeObserver(), this);
            mActivity.mAllAppsViewController.show();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            ViewTreeObserverWrapper.removeOnComputeInsetsListener(this);
        }

        @Override
        public void recreateControllers() {
            mControllers = new TouchController[]{mActivity.mDragController};
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getAction() == ACTION_UP && event.getKeyCode() == KEYCODE_BACK) {
                AbstractFloatingView topView = AbstractFloatingView.getTopOpenView(mActivity);
                if (topView != null && topView.onBackPressed()) {
                    return true;
                }
            }
            return super.dispatchKeyEvent(event);
        }

        @Override
        public void onComputeInsets(InsetsInfo inoutInfo) {
            if (mActivity.mDragController.isSystemDragInProgress()) {
                inoutInfo.touchableRegion.setEmpty();
                inoutInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION);
            }
        }
    }
}
