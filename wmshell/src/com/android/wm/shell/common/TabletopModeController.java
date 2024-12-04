/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.common;

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.wm.shell.common.DevicePostureController.DEVICE_POSTURE_HALF_OPENED;
import static com.android.wm.shell.common.DevicePostureController.DEVICE_POSTURE_UNKNOWN;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_FOLDABLE;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.app.WindowConfiguration;
import android.content.Context;
import android.content.res.Configuration;
import android.os.SystemProperties;
import android.util.ArraySet;
import android.view.Surface;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.sysui.ShellInit;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Wrapper class to track the tabletop (aka. flex) mode change on Fold-ables.
 * See also <a
 * href="https://developer.android.com/guide/topics/large-screens/learn-about-foldables
 * #foldable_postures">Foldable states and postures</a> for reference.
 *
 * Use the {@link DevicePostureController} for more detailed posture changes.
 */
public class TabletopModeController implements
        DevicePostureController.OnDevicePostureChangedListener,
        DisplayController.OnDisplaysChangedListener {
    /**
     * Prefer the {@link #PREFERRED_TABLETOP_HALF_TOP} if this flag is enabled,
     * {@link #PREFERRED_TABLETOP_HALF_BOTTOM} otherwise.
     * See also {@link #getPreferredHalfInTabletopMode()}.
     */
    private static final boolean PREFER_TOP_HALF_IN_TABLETOP =
            SystemProperties.getBoolean("persist.wm.debug.prefer_top_half_in_tabletop", true);

    private static final long TABLETOP_MODE_DELAY_MILLIS = 1_000;

    @IntDef(prefix = {"PREFERRED_TABLETOP_HALF_"}, value = {
            PREFERRED_TABLETOP_HALF_TOP,
            PREFERRED_TABLETOP_HALF_BOTTOM
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface PreferredTabletopHalf {}

    public static final int PREFERRED_TABLETOP_HALF_TOP = 0;
    public static final int PREFERRED_TABLETOP_HALF_BOTTOM = 1;

    private final Context mContext;

    private final DevicePostureController mDevicePostureController;

    private final DisplayController mDisplayController;

    private final ShellExecutor mMainExecutor;

    private final Set<Integer> mTabletopModeRotations = new ArraySet<>();

    private final List<OnTabletopModeChangedListener> mListeners = new ArrayList<>();

    @VisibleForTesting
    final Runnable mOnEnterTabletopModeCallback = () -> {
        if (isInTabletopMode()) {
            // We are still in tabletop mode, go ahead.
            mayBroadcastOnTabletopModeChange(true /* isInTabletopMode */);
        }
    };

    @DevicePostureController.DevicePostureInt
    private int mDevicePosture = DEVICE_POSTURE_UNKNOWN;

    @Surface.Rotation
    private int mDisplayRotation = WindowConfiguration.ROTATION_UNDEFINED;

    /**
     * Track the last callback value for {@link OnTabletopModeChangedListener}.
     * This is to avoid duplicated {@code false} callback to {@link #mListeners}.
     */
    private Boolean mLastIsInTabletopModeForCallback;

    public TabletopModeController(Context context,
            ShellInit shellInit,
            DevicePostureController postureController,
            DisplayController displayController,
            @ShellMainThread ShellExecutor mainExecutor) {
        mContext = context;
        mDevicePostureController = postureController;
        mDisplayController = displayController;
        mMainExecutor = mainExecutor;
        shellInit.addInitCallback(this::onInit, this);
    }

    @VisibleForTesting
    void onInit() {
        mDevicePostureController.registerOnDevicePostureChangedListener(this);
        mDisplayController.addDisplayWindowListener(this);
        // Aligns with what's in {@link com.android.server.wm.DisplayRotation}.
        final int[] deviceTabletopRotations = mContext.getResources().getIntArray(
                com.android.internal.R.array.config_deviceTabletopRotations);
        if (deviceTabletopRotations == null || deviceTabletopRotations.length == 0) {
            ProtoLog.e(WM_SHELL_FOLDABLE,
                    "No valid config_deviceTabletopRotations, can not tell"
                            + " tabletop mode in WMShell");
            return;
        }
        for (int angle : deviceTabletopRotations) {
            switch (angle) {
                case 0:
                    mTabletopModeRotations.add(Surface.ROTATION_0);
                    break;
                case 90:
                    mTabletopModeRotations.add(Surface.ROTATION_90);
                    break;
                case 180:
                    mTabletopModeRotations.add(Surface.ROTATION_180);
                    break;
                case 270:
                    mTabletopModeRotations.add(Surface.ROTATION_270);
                    break;
                default:
                    ProtoLog.e(WM_SHELL_FOLDABLE,
                            "Invalid surface rotation angle in "
                                    + "config_deviceTabletopRotations: %d",
                            angle);
                    break;
            }
        }
    }

    /** @return Preferred half for floating windows like PiP when in tabletop mode. */
    @PreferredTabletopHalf
    public int getPreferredHalfInTabletopMode() {
        return PREFER_TOP_HALF_IN_TABLETOP
                ? PREFERRED_TABLETOP_HALF_TOP
                : PREFERRED_TABLETOP_HALF_BOTTOM;
    }

    /** Register {@link OnTabletopModeChangedListener} to listen for tabletop mode change. */
    public void registerOnTabletopModeChangedListener(
            @NonNull OnTabletopModeChangedListener listener) {
        if (listener == null || mListeners.contains(listener)) return;
        mListeners.add(listener);
        listener.onTabletopModeChanged(isInTabletopMode());
    }

    /** Unregister {@link OnTabletopModeChangedListener} for tabletop mode change. */
    public void unregisterOnTabletopModeChangedListener(
            @NonNull OnTabletopModeChangedListener listener) {
        mListeners.remove(listener);
    }

    @Override
    public void onDevicePostureChanged(@DevicePostureController.DevicePostureInt int posture) {
        if (mDevicePosture != posture) {
            onDevicePostureOrDisplayRotationChanged(posture, mDisplayRotation);
        }
    }

    @Override
    public void onDisplayConfigurationChanged(int displayId, Configuration newConfig) {
        final int newDisplayRotation = newConfig.windowConfiguration.getDisplayRotation();
        if (displayId == DEFAULT_DISPLAY && newDisplayRotation != mDisplayRotation) {
            onDevicePostureOrDisplayRotationChanged(mDevicePosture, newDisplayRotation);
        }
    }

    private void onDevicePostureOrDisplayRotationChanged(
            @DevicePostureController.DevicePostureInt int newPosture,
            @Surface.Rotation int newDisplayRotation) {
        final boolean wasInTabletopMode = isInTabletopMode();
        mDevicePosture = newPosture;
        mDisplayRotation = newDisplayRotation;
        final boolean couldBeInTabletopMode = isInTabletopMode();
        mMainExecutor.removeCallbacks(mOnEnterTabletopModeCallback);
        if (!wasInTabletopMode && couldBeInTabletopMode) {
            // May enter tabletop mode, but we need to wait for additional time since this
            // could be an intermediate state.
            mMainExecutor.executeDelayed(mOnEnterTabletopModeCallback, TABLETOP_MODE_DELAY_MILLIS);
        } else {
            // Cancel entering tabletop mode if any condition's changed.
            mayBroadcastOnTabletopModeChange(false /* isInTabletopMode */);
        }
    }

    private boolean isHalfOpened(@DevicePostureController.DevicePostureInt int posture) {
        return posture == DEVICE_POSTURE_HALF_OPENED;
    }

    private boolean isInTabletopMode() {
        return isHalfOpened(mDevicePosture) && mTabletopModeRotations.contains(mDisplayRotation);
    }

    private void mayBroadcastOnTabletopModeChange(boolean isInTabletopMode) {
        if (mLastIsInTabletopModeForCallback == null
                || mLastIsInTabletopModeForCallback != isInTabletopMode) {
            mListeners.forEach(l -> l.onTabletopModeChanged(isInTabletopMode));
            mLastIsInTabletopModeForCallback = isInTabletopMode;
        }
    }

    /**
     * Listener interface for tabletop mode change.
     */
    public interface OnTabletopModeChangedListener {
        /**
         * Callback when tabletop mode changes. Expect duplicated callbacks with {@code false}.
         * @param isInTabletopMode {@code true} if enters tabletop mode, {@code false} otherwise.
         */
        void onTabletopModeChanged(boolean isInTabletopMode);
    }
}
