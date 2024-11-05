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
import static android.view.InsetsFrameProvider.SOURCE_DISPLAY;
import static android.view.WindowInsets.Type.mandatorySystemGestures;
import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.systemGestures;
import static android.view.WindowInsets.Type.tappableElement;

import static com.android.launcher3.taskbar.LauncherTaskbarUIController.DISPLAY_PROGRESS_COUNT;

import android.app.PendingIntent;
import android.os.Binder;
import android.os.IBinder;
import android.view.InsetsFrameProvider;

import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags;

/**
 * State shared across different taskbar instance
 */
public class TaskbarSharedState {

    private final IBinder mInsetsOwner = new Binder();
    private static int INDEX_LEFT = 0;
    private static int INDEX_RIGHT = 1;

    // TaskbarManager#onSystemUiFlagsChanged
    @SystemUiStateFlags
    public long sysuiStateFlags;

    // TaskbarManager#disableNavBarElements()
    public int disableNavBarDisplayId;
    public int disableNavBarState1;
    public int disableNavBarState2;

    // TaskbarManager#onSystemBarAttributesChanged()
    public int systemBarAttrsDisplayId;
    public int systemBarAttrsBehavior;

    // TaskbarManager#onNavButtonsDarkIntensityChanged()
    public float navButtonsDarkIntensity;

    // TaskbarManager#onNavigationBarLumaSamplingEnabled()
    public int mLumaSamplingDisplayId = DEFAULT_DISPLAY;
    public boolean mIsLumaSamplingEnabled = true;

    public boolean setupUIVisible = false;

    public boolean allAppsVisible = false;

    // LauncherTaskbarUIController#mTaskbarInAppDisplayProgressMultiProp
    public float[] inAppDisplayProgressMultiPropValues = new float[DISPLAY_PROGRESS_COUNT];

    // Taskbar System Action
    public PendingIntent taskbarSystemActionPendingIntent;

    public final InsetsFrameProvider[] insetsFrameProviders = new InsetsFrameProvider[] {
            new InsetsFrameProvider(mInsetsOwner, 0, navigationBars()),
            new InsetsFrameProvider(mInsetsOwner, 0, tappableElement()),
            new InsetsFrameProvider(mInsetsOwner, 0, mandatorySystemGestures()),
            new InsetsFrameProvider(mInsetsOwner, INDEX_LEFT, systemGestures())
                    .setSource(SOURCE_DISPLAY),
            new InsetsFrameProvider(mInsetsOwner, INDEX_RIGHT, systemGestures())
                    .setSource(SOURCE_DISPLAY)
    };

    // Allows us to shift translation logic when doing taskbar pinning animation.
    public boolean startTaskbarVariantIsTransient = true;

    // To track if taskbar was pinned using taskbar pinning feature at the time of recreate,
    // so we can unstash transient taskbar when we un-pinning taskbar.
    private boolean mTaskbarWasPinned = false;

    public boolean getTaskbarWasPinned() {
        return mTaskbarWasPinned;
    }

    public void setTaskbarWasPinned(boolean taskbarWasPinned) {
        mTaskbarWasPinned = taskbarWasPinned;
    }

    // To track if taskbar was stashed / unstashed between configuration changes (which recreates
    // the task bar).
    public Boolean taskbarWasStashedAuto = true;
}
