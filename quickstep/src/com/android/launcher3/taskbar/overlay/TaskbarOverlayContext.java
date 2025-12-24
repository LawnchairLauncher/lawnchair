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
package com.android.launcher3.taskbar.overlay;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.content.Context;
import android.graphics.Point;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.InsettableFrameLayout.LayoutParams;
import com.android.launcher3.R;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.taskbar.BaseTaskbarContext;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarControllers;
import com.android.launcher3.taskbar.TaskbarDragController;
import com.android.launcher3.taskbar.TaskbarUIController;
import com.android.launcher3.taskbar.allapps.TaskbarAllAppsContainerView;
import com.android.launcher3.taskbar.allapps.TaskbarSearchSessionController;
import com.android.launcher3.taskbar.bubbles.DragToBubbleController;
import com.android.launcher3.taskbar.customization.TaskbarSpecsEvaluator;
import com.android.launcher3.util.NavigationMode;
import com.android.launcher3.util.SplitConfigurationOptions.SplitSelectSource;

import java.util.Optional;

/**
 * Window context for the taskbar overlays such as All Apps and EDU.
 * <p>
 * Overlays have their own window and need a window context. Some properties are delegated to the
 * {@link TaskbarActivityContext} such as {@link PopupDataProvider}.
 */
public class TaskbarOverlayContext extends BaseTaskbarContext {
    private final TaskbarActivityContext mTaskbarContext;

    private final TaskbarOverlayController mOverlayController;
    private final TaskbarDragController mDragController;
    private final TaskbarOverlayDragLayer mDragLayer;
    private FrameLayout mBubbleBarDropViewContainer;
    private final Optional<DragToBubbleController> mDragToBubbleController;

    private final int mStashedTaskbarHeight;
    private final TaskbarUIController mUiController;

    private @Nullable TaskbarSearchSessionController mSearchSessionController;

    public TaskbarOverlayContext(
            Context windowContext,
            TaskbarActivityContext taskbarContext,
            TaskbarControllers controllers) {
        super(windowContext, taskbarContext.getDisplayId(), taskbarContext.isPrimaryDisplay());
        mTaskbarContext = taskbarContext;
        mOverlayController = controllers.taskbarOverlayController;
        mDragToBubbleController = controllers.bubbleControllers.map(c -> c.dragToBubbleController);
        mDragController = new TaskbarDragController(this);
        // We don't query isDragging from DragController attached to TaskbarOverlayContext. Instead
        // we only query it from DragController attached to TaskbarControllers. Thus we don't pass
        // TaskbarUiState to DragController here.
        mDragController.init(controllers, null);
        mDragLayer = new TaskbarOverlayDragLayer(this);
        mStashedTaskbarHeight = controllers.taskbarStashController.getStashedHeight();
        updateBlurStyle();

        mUiController = controllers.uiController;
        onViewCreated();
    }

    /** Called when the controller is destroyed. */
    @Override
    public void onDestroy() {
        super.onDestroy();
        mDragController.onDestroy();
    }

    public @Nullable TaskbarSearchSessionController getSearchSessionController() {
        return mSearchSessionController;
    }

    public void setSearchSessionController(
            @Nullable TaskbarSearchSessionController searchSessionController) {
        mSearchSessionController = searchSessionController;
    }

    int getStashedTaskbarHeight() {
        return mStashedTaskbarHeight;
    }

    public TaskbarOverlayController getOverlayController() {
        return mOverlayController;
    }

    public TaskbarSpecsEvaluator getSpecsEvaluator() {
        return mTaskbarContext.getTaskbarSpecsEvaluator();
    }

    /** Returns {@code true} if overlay or Taskbar windows are handling a system drag. */
    boolean isAnySystemDragInProgress() {
        return mOverlayController.isAnySystemDragInProgress();
    }

    @Override
    public DeviceProfile getDeviceProfile() {
        return mOverlayController.getLauncherDeviceProfile();
    }

    @Override
    public View.AccessibilityDelegate getAccessibilityDelegate() {
        return mTaskbarContext.getAccessibilityDelegate();
    }

    @Override
    public TaskbarDragController getDragController() {
        return mDragController;
    }

    @Override
    public TaskbarOverlayDragLayer getDragLayer() {
        return mDragLayer;
    }

    @Override
    public TaskbarAllAppsContainerView getAppsView() {
        return mDragLayer.findViewById(R.id.apps_view);
    }

    @Override
    public boolean isAllAppsBackgroundBlurEnabled() {
        return Flags.allAppsBlur() && mOverlayController != null
                && mOverlayController.isBackgroundBlurEnabled();
    }

    /** Apply the blur or blur fallback style to the current theme. */
    private void updateBlurStyle() {
        if (!Flags.allAppsBlur()) {
            return;
        }
        getTheme().applyStyle(getAllAppsBlurStyleResId(), true);
    }

    @Override
    public View.OnClickListener getItemOnClickListener() {
        return mTaskbarContext.getItemOnClickListener();
    }

    @Override
    public View.OnLongClickListener getAllAppsItemLongClickListener() {
        return mDragController::startDragOnLongClick;
    }

    @Override
    public void startSplitSelection(SplitSelectSource splitSelectSource) {
        mUiController.startSplitSelection(splitSelectSource);
    }

    @Override
    public boolean isTransientTaskbar() {
        return mTaskbarContext.isTransientTaskbar();
    }

    @Override
    public boolean isPinnedTaskbar() {
        return mTaskbarContext.isPinnedTaskbar();
    }

    @Override
    public NavigationMode getNavigationMode() {
        return mTaskbarContext.getNavigationMode();
    }

    @Override
    public boolean isInDesktopMode() {
        return mTaskbarContext.isInDesktopMode();
    }

    @Override
    public boolean isTaskbarShowingDesktopTasks() {
        return mTaskbarContext.isTaskbarShowingDesktopTasks();
    }

    @Override
    public boolean showLockedTaskbarOnHome() {
        return mTaskbarContext.showLockedTaskbarOnHome();
    }

    @Override
    public boolean showDesktopTaskbarForFreeformDisplay() {
        return mTaskbarContext.showDesktopTaskbarForFreeformDisplay();
    }

    @Override
    public Point getScreenSize() {
        return mTaskbarContext.getScreenSize();
    }

    @Override
    public int getDisplayHeight() {
        return mTaskbarContext.getDisplayHeight();
    }

    @Override
    public void notifyConfigChanged() {
        mTaskbarContext.notifyConfigChanged();
    }

    @Override
    public void onDragStart() {
        mDragToBubbleController.ifPresent(c -> {
            setupBubbleBarDropViewContainer();
            c.setOverlayContainerView(mBubbleBarDropViewContainer);
        });
    }

    @Override
    public void onDragEnd() {
        mDragToBubbleController.ifPresent(c -> c.setOverlayContainerView(null));
        mOverlayController.maybeCloseWindow();
    }

    @Override
    public void onPopupVisibilityChanged(boolean isVisible) {}

    @Override
    public void onSplitScreenMenuButtonClicked() {
    }

    private void setupBubbleBarDropViewContainer() {
        if (mDragToBubbleController.isEmpty() || mBubbleBarDropViewContainer != null) {
            return;
        }
        mBubbleBarDropViewContainer = new FrameLayout(this);
        mDragLayer.addView(mBubbleBarDropViewContainer);
        LayoutParams lp = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
        lp.ignoreInsets = true;
        mBubbleBarDropViewContainer.setLayoutParams(lp);
    }
}
