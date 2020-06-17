/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.quickstep;

import static com.android.launcher3.util.PackageManagerHelper.getPackageFilter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.util.Log;

import com.android.launcher3.ResourceUtils;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.util.MainThreadInitializedObject;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Observer for the resource config that specifies the navigation bar mode.
 */
public class SysUINavigationMode {

    public enum Mode {
        THREE_BUTTONS(false, 0),
        TWO_BUTTONS(true, 1),
        NO_BUTTON(true, 2);

        public final boolean hasGestures;
        public final int resValue;

        Mode(boolean hasGestures, int resValue) {
            this.hasGestures = hasGestures;
            this.resValue = resValue;
        }
    }

    public static Mode getMode(Context context) {
        return INSTANCE.get(context).getMode();
    }

    public static final MainThreadInitializedObject<SysUINavigationMode> INSTANCE =
            new MainThreadInitializedObject<>(SysUINavigationMode::new);

    private static final String TAG = "SysUINavigationMode";

    private static final String ACTION_OVERLAY_CHANGED = "android.intent.action.OVERLAY_CHANGED";
    private static final String NAV_BAR_INTERACTION_MODE_RES_NAME =
            "config_navBarInteractionMode";
    private static final String NAV_BAR_INTERACTION_MODE_OVERLAY_PACKAGE_PREFIX =
            "com.android.internal.systemui.navbar";
    private static final String ONE_HANDED_MODE_OVERLAY_PACKAGE_PREFIX =
            "com.android.internal.systemui.onehanded";
    private static final int INVALID_RESOURCE_HANDLE = -1;

    private final Context mContext;
    private Mode mMode;

    private int mNavBarGesturalHeight;

    private final List<NavigationModeChangeListener> mChangeListeners = new ArrayList<>();
    private final List<OneHandedModeChangeListener> mOneHandedOverlayChangeListeners =
            new ArrayList<>();

    public SysUINavigationMode(Context context) {
        mContext = context;
        initializeMode();

        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateMode();
            }
        }, getPackageFilter(NAV_BAR_INTERACTION_MODE_OVERLAY_PACKAGE_PREFIX,
                ACTION_OVERLAY_CHANGED));

        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateGesturalHeight();
            }
        }, getPackageFilter(ONE_HANDED_MODE_OVERLAY_PACKAGE_PREFIX, ACTION_OVERLAY_CHANGED));
    }

    /** Updates navigation mode when needed. */
    public void updateMode() {
        Mode oldMode = mMode;
        initializeMode();
        if (mMode != oldMode) {
            dispatchModeChange();
        }
    }

    private void updateGesturalHeight() {
        int newGesturalHeight = getSystemIntegerRes(mContext,
                ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE);

        if (newGesturalHeight == INVALID_RESOURCE_HANDLE) {
            return;
        }

        if (mNavBarGesturalHeight != newGesturalHeight) {
            mNavBarGesturalHeight = newGesturalHeight;
            dispatchOneHandedOverlayChange();
        }
    }

    private void initializeMode() {
        int modeInt = getSystemIntegerRes(mContext, NAV_BAR_INTERACTION_MODE_RES_NAME);
        mNavBarGesturalHeight = getSystemIntegerRes(mContext,
                ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE);
        for(Mode m : Mode.values()) {
            if (m.resValue == modeInt) {
                mMode = m;
            }
        }
    }

    private void dispatchModeChange() {
        for (NavigationModeChangeListener listener : mChangeListeners) {
            listener.onNavigationModeChanged(mMode);
        }
    }

    private void dispatchOneHandedOverlayChange() {
        for (OneHandedModeChangeListener listener : mOneHandedOverlayChangeListeners) {
            listener.onOneHandedModeChanged(mNavBarGesturalHeight);
        }
    }

    public Mode addModeChangeListener(NavigationModeChangeListener listener) {
        mChangeListeners.add(listener);
        return mMode;
    }

    public void removeModeChangeListener(NavigationModeChangeListener listener) {
        mChangeListeners.remove(listener);
    }

    public int addOneHandedOverlayChangeListener(OneHandedModeChangeListener listener) {
        mOneHandedOverlayChangeListeners.add(listener);
        return mNavBarGesturalHeight;
    }

    public void removeOneHandedOverlayChangeListener(OneHandedModeChangeListener listener) {
        mOneHandedOverlayChangeListeners.remove(listener);
    }

    public Mode getMode() {
        return mMode;
    }

    private static int getSystemIntegerRes(Context context, String resName) {
        Resources res = context.getResources();
        int resId = res.getIdentifier(resName, "integer", "android");

        if (resId != 0) {
            return res.getInteger(resId);
        } else {
            Log.e(TAG, "Failed to get system resource ID. Incompatible framework version?");
            return INVALID_RESOURCE_HANDLE;
        }
    }

    /** @return Whether we can remove the shelf from overview. */
    public static boolean removeShelfFromOverview(Context context) {
        // The shelf is core to the two-button mode model, so we need to continue supporting it.
        return getMode(context) != Mode.TWO_BUTTONS;
    }

    public static boolean hideShelfInTwoButtonLandscape(Context context,
            PagedOrientationHandler pagedOrientationHandler) {
        return  getMode(context) == Mode.TWO_BUTTONS &&
                !pagedOrientationHandler.isLayoutNaturalToLauncher();
    }

    public void dump(PrintWriter pw) {
        pw.println("SysUINavigationMode:");
        pw.println("  mode=" + mMode.name());
    }

    public interface NavigationModeChangeListener {

        void onNavigationModeChanged(Mode newMode);
    }

    public interface OneHandedModeChangeListener {

        void onOneHandedModeChanged(int newGesturalHeight);
    }
}