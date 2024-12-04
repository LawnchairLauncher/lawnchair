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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.content.Context;
import android.hardware.devicestate.DeviceStateManager;
import android.util.SparseIntArray;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.wm.shell.sysui.ShellInit;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper class to track the device posture change on Fold-ables.
 * See also <a
 * href="https://developer.android.com/guide/topics/large-screens/learn-about-foldables
 * #foldable_postures">Foldable states and postures</a> for reference.
 *
 * Note that most of the implementation here inherits from
 * {@link com.android.systemui.statusbar.policy.DevicePostureController}.
 *
 * Use the {@link TabletopModeController} if you are interested in tabletop mode change only,
 * which is more common.
 */
public class DevicePostureController {
    @IntDef(prefix = {"DEVICE_POSTURE_"}, value = {
            DEVICE_POSTURE_UNKNOWN,
            DEVICE_POSTURE_CLOSED,
            DEVICE_POSTURE_HALF_OPENED,
            DEVICE_POSTURE_OPENED,
            DEVICE_POSTURE_FLIPPED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface DevicePostureInt {}

    // NOTE: These constants **must** match those defined for Jetpack Sidecar. This is because we
    // use the Device State -> Jetpack Posture map to translate between the two.
    public static final int DEVICE_POSTURE_UNKNOWN = 0;
    public static final int DEVICE_POSTURE_CLOSED = 1;
    public static final int DEVICE_POSTURE_HALF_OPENED = 2;
    public static final int DEVICE_POSTURE_OPENED = 3;
    public static final int DEVICE_POSTURE_FLIPPED = 4;

    private final Context mContext;
    private final ShellExecutor mMainExecutor;
    private final List<OnDevicePostureChangedListener> mListeners = new ArrayList<>();
    private final SparseIntArray mDeviceStateToPostureMap = new SparseIntArray();

    private int mDevicePosture = DEVICE_POSTURE_UNKNOWN;

    public DevicePostureController(
            Context context, ShellInit shellInit, ShellExecutor mainExecutor) {
        mContext = context;
        mMainExecutor = mainExecutor;
        shellInit.addInitCallback(this::onInit, this);
    }

    private void onInit() {
        // Most of this is borrowed from WindowManager/Jetpack/DeviceStateManagerPostureProducer.
        // Using the sidecar/extension libraries directly brings in a new dependency that it'd be
        // good to avoid (along with the fact that sidecar is deprecated, and extensions isn't fully
        // ready yet), and we'd have to make our own layer over the sidecar library anyway to easily
        // allow the implementation to change, so it was easier to just interface with
        // DeviceStateManager directly.
        String[] deviceStatePosturePairs = mContext.getResources()
                .getStringArray(R.array.config_device_state_postures);
        for (String deviceStatePosturePair : deviceStatePosturePairs) {
            String[] deviceStatePostureMapping = deviceStatePosturePair.split(":");
            if (deviceStatePostureMapping.length != 2) {
                continue;
            }

            int deviceState;
            int posture;
            try {
                deviceState = Integer.parseInt(deviceStatePostureMapping[0]);
                posture = Integer.parseInt(deviceStatePostureMapping[1]);
            } catch (NumberFormatException e) {
                continue;
            }

            mDeviceStateToPostureMap.put(deviceState, posture);
        }

        final DeviceStateManager deviceStateManager = mContext.getSystemService(
                DeviceStateManager.class);
        if (deviceStateManager != null) {
            deviceStateManager.registerCallback(mMainExecutor, state -> onDevicePostureChanged(
                    mDeviceStateToPostureMap.get(state.getIdentifier(), DEVICE_POSTURE_UNKNOWN)));
        }
    }

    @VisibleForTesting
    void onDevicePostureChanged(int devicePosture) {
        if (devicePosture == mDevicePosture) return;
        mDevicePosture = devicePosture;
        mListeners.forEach(l -> l.onDevicePostureChanged(mDevicePosture));
    }

    /**
     * Register {@link OnDevicePostureChangedListener} for device posture changes.
     * The listener will receive callback with current device posture upon registration.
     */
    public void registerOnDevicePostureChangedListener(
            @NonNull OnDevicePostureChangedListener listener) {
        if (mListeners.contains(listener)) return;
        mListeners.add(listener);
        listener.onDevicePostureChanged(mDevicePosture);
    }

    /**
     * Unregister {@link OnDevicePostureChangedListener} for device posture changes.
     */
    public void unregisterOnDevicePostureChangedListener(
            @NonNull OnDevicePostureChangedListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Listener interface for device posture change.
     */
    public interface OnDevicePostureChangedListener {
        /**
         * Callback when device posture changes.
         * See {@link DevicePostureInt} for callback values.
         */
        void onDevicePostureChanged(@DevicePostureInt int posture);
    }
}
