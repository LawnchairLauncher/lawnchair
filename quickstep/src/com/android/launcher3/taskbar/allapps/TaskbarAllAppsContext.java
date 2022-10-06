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
import static android.view.ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import android.content.Context;
import android.graphics.Insets;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnComputeInternalInsetsListener;
import android.view.WindowInsets;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.allapps.search.DefaultSearchAdapterProvider;
import com.android.launcher3.allapps.search.SearchAdapterProvider;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.taskbar.BaseTaskbarContext;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarControllers;
import com.android.launcher3.taskbar.TaskbarDragController;
import com.android.launcher3.taskbar.TaskbarStashController;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.OnboardingPrefs;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.views.BaseDragLayer;

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

    // We automatically stash taskbar when all apps is opened in gesture navigation mode.
    private final boolean mWillTaskbarBeVisuallyStashed;
    private final int mStashedTaskbarHeight;

    TaskbarAllAppsContext(
            TaskbarActivityContext taskbarContext,
            TaskbarAllAppsController windowController,
            TaskbarControllers taskbarControllers) {
        super(taskbarContext.createWindowContext(TYPE_APPLICATION_OVERLAY, null));
        mTaskbarContext = taskbarContext;
        mWindowController = windowController;
        mDragController = new TaskbarDragController(this);
        mOnboardingPrefs = new OnboardingPrefs<>(this, Utilities.getPrefs(this));

        mDragLayer = new TaskbarAllAppsDragLayer(this);
        TaskbarAllAppsSlideInView slideInView = (TaskbarAllAppsSlideInView) mLayoutInflater.inflate(
                R.layout.taskbar_all_apps, mDragLayer, false);
        mAllAppsViewController = new TaskbarAllAppsViewController(
                this,
                slideInView,
                windowController,
                taskbarControllers);
        mAppsView = slideInView.getAppsView();

        TaskbarStashController taskbarStashController = taskbarControllers.taskbarStashController;
        mWillTaskbarBeVisuallyStashed = taskbarStashController.supportsVisualStashing();
        mStashedTaskbarHeight = taskbarStashController.getStashedHeight();
    }

    TaskbarAllAppsViewController getAllAppsViewController() {
        return mAllAppsViewController;
    }

    @Override
    public DeviceProfile getDeviceProfile() {
        return mWindowController.getDeviceProfile();
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
    public void onDragStart() {}

    @Override
    public void onDragEnd() {
        mWindowController.maybeCloseWindow();
    }

    @Override
    public void onPopupVisibilityChanged(boolean isVisible) {}

    @Override
    public SearchAdapterProvider<?> createSearchAdapterProvider(
            ActivityAllAppsContainerView<?> appsView) {
        return new DefaultSearchAdapterProvider(this);
    }

    /** Root drag layer for this context. */
    private static class TaskbarAllAppsDragLayer extends
            BaseDragLayer<TaskbarAllAppsContext> implements OnComputeInternalInsetsListener {

        private TaskbarAllAppsDragLayer(Context context) {
            super(context, null, 1);
            setClipChildren(false);
            recreateControllers();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            getViewTreeObserver().addOnComputeInternalInsetsListener(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            getViewTreeObserver().removeOnComputeInternalInsetsListener(this);
        }

        @Override
        public void recreateControllers() {
            mControllers = new TouchController[]{mActivity.mDragController};
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            TestLogging.recordMotionEvent(TestProtocol.SEQUENCE_MAIN, "Touch event", ev);
            return super.dispatchTouchEvent(ev);
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
        public void onComputeInternalInsets(ViewTreeObserver.InternalInsetsInfo inoutInfo) {
            if (mActivity.mDragController.isSystemDragInProgress()) {
                inoutInfo.touchableRegion.setEmpty();
                inoutInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION);
            }
        }

        @Override
        public WindowInsets onApplyWindowInsets(WindowInsets insets) {
            return updateInsetsDueToStashing(insets);
        }

        /**
         * Taskbar automatically stashes when opening all apps, but we don't report the insets as
         * changing to avoid moving the underlying app. But internally, the apps view should still
         * layout according to the stashed insets rather than the unstashed insets. So this method
         * does two things:
         * 1) Sets navigationBars bottom inset to stashedHeight.
         * 2) Sets tappableInsets bottom inset to 0.
         */
        private WindowInsets updateInsetsDueToStashing(WindowInsets oldInsets) {
            if (!mActivity.mWillTaskbarBeVisuallyStashed) {
                return oldInsets;
            }
            WindowInsets.Builder updatedInsetsBuilder = new WindowInsets.Builder(oldInsets);

            Insets oldNavInsets = oldInsets.getInsets(WindowInsets.Type.navigationBars());
            Insets newNavInsets = Insets.of(oldNavInsets.left, oldNavInsets.top, oldNavInsets.right,
                    mActivity.mStashedTaskbarHeight);
            updatedInsetsBuilder.setInsets(WindowInsets.Type.navigationBars(), newNavInsets);

            Insets oldTappableInsets = oldInsets.getInsets(WindowInsets.Type.tappableElement());
            Insets newTappableInsets = Insets.of(oldTappableInsets.left, oldTappableInsets.top,
                    oldTappableInsets.right, 0);
            updatedInsetsBuilder.setInsets(WindowInsets.Type.tappableElement(), newTappableInsets);

            return updatedInsetsBuilder.build();
        }
    }
}
