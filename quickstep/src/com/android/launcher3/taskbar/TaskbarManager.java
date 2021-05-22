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

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static com.android.launcher3.util.DisplayController.CHANGE_ACTIVE_SCREEN;
import static com.android.launcher3.util.DisplayController.CHANGE_DENSITY;
import static com.android.launcher3.util.DisplayController.CHANGE_SUPPORTED_BOUNDS;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_IME_SHOWING;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.inputmethodservice.InputMethodService;
import android.view.Display;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.DisplayController.Info;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.TouchInteractionService;

/**
 * Class to manager taskbar lifecycle
 */
public class TaskbarManager implements DisplayController.DisplayInfoChangeListener,
        SysUINavigationMode.NavigationModeChangeListener {

    private final Context mContext;
    private final DisplayController mDisplayController;
    private final SysUINavigationMode mSysUINavigationMode;
    private final TaskbarNavButtonController mNavButtonController;

    private TaskbarActivityContext mTaskbarActivityContext;
    private BaseQuickstepLauncher mLauncher;

    private static final int CHANGE_FLAGS =
            CHANGE_ACTIVE_SCREEN | CHANGE_DENSITY | CHANGE_SUPPORTED_BOUNDS;

    private boolean mUserUnlocked = false;

    public TaskbarManager(TouchInteractionService service) {
        mDisplayController = DisplayController.INSTANCE.get(service);
        mSysUINavigationMode = SysUINavigationMode.INSTANCE.get(service);
        Display display =
                service.getSystemService(DisplayManager.class).getDisplay(DEFAULT_DISPLAY);
        mContext = service.createWindowContext(display, TYPE_APPLICATION_OVERLAY, null);
        mNavButtonController = new TaskbarNavButtonController(service);

        mDisplayController.addChangeListener(this);
        mSysUINavigationMode.addModeChangeListener(this);
        recreateTaskbar();
    }

    @Override
    public void onNavigationModeChanged(Mode newMode) {
        recreateTaskbar();
    }

    @Override
    public void onDisplayInfoChanged(Context context, Info info, int flags) {
        if ((flags & CHANGE_FLAGS) != 0) {
            recreateTaskbar();
        }
    }

    private void destroyExistingTaskbar() {
        if (mTaskbarActivityContext != null) {
            mTaskbarActivityContext.onDestroy();
            mTaskbarActivityContext = null;
        }
    }

    /**
     * Called when the user is unlocked
     */
    public void onUserUnlocked() {
        mUserUnlocked = true;
        recreateTaskbar();
    }

    /**
     * Sets or clears a launcher to act as taskbar callback
     */
    public void setLauncher(@Nullable BaseQuickstepLauncher launcher) {
        mLauncher = launcher;
        if (mTaskbarActivityContext != null) {
            mTaskbarActivityContext.setUIController(mLauncher == null
                    ? TaskbarUIController.DEFAULT
                    : new LauncherTaskbarUIController(launcher, mTaskbarActivityContext));
        }
    }

    private void recreateTaskbar() {
        destroyExistingTaskbar();
        if (!FeatureFlags.ENABLE_TASKBAR.get()) {
            return;
        }
        if (!mUserUnlocked) {
            return;
        }
        DeviceProfile dp = LauncherAppState.getIDP(mContext).getDeviceProfile(mContext);
        if (!dp.isTaskbarPresent) {
            return;
        }
        mTaskbarActivityContext = new TaskbarActivityContext(
                mContext, dp.copy(mContext), mNavButtonController);
        mTaskbarActivityContext.init();
        if (mLauncher != null) {
            mTaskbarActivityContext.setUIController(
                    new LauncherTaskbarUIController(mLauncher, mTaskbarActivityContext));
        }
    }

    /**
     * See {@link com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags}
     * @param systemUiStateFlags The latest SystemUiStateFlags
     */
    public void onSystemUiFlagsChanged(int systemUiStateFlags) {
        boolean isImeVisible = (systemUiStateFlags & SYSUI_STATE_IME_SHOWING) != 0;
        if (mTaskbarActivityContext != null) {
            mTaskbarActivityContext.setImeIsVisible(isImeVisible);
        }
    }

    /**
     * When in 3 button nav, the above doesn't get called since we prevent sysui nav bar from
     * instantiating at all, which is what's responsible for sending sysui state flags over.
     *
     * @param vis IME visibility flag
     * @param backDisposition Used to determine back button behavior for software keyboard
     *                        See BACK_DISPOSITION_* constants in {@link InputMethodService}
     */
    public void updateImeStatus(int displayId, int vis, int backDisposition,
            boolean showImeSwitcher) {
        if (mTaskbarActivityContext != null) {
            mTaskbarActivityContext.updateImeStatus(displayId, vis, showImeSwitcher);
        }
    }

    /**
     * Called when the manager is no longer needed
     */
    public void destroy() {
        destroyExistingTaskbar();
        mDisplayController.removeChangeListener(this);
        mSysUINavigationMode.removeModeChangeListener(this);
    }
}
