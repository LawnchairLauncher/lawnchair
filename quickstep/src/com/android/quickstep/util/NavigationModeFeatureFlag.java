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

package com.android.quickstep.util;

import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;

import android.content.Context;

import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.SysUINavigationMode;

import java.util.function.Predicate;
import java.util.function.Supplier;

/** A feature flag that listens to navigation mode changes. */
public class NavigationModeFeatureFlag implements
        SysUINavigationMode.NavigationModeChangeListener {

    public static final NavigationModeFeatureFlag LIVE_TILE = new NavigationModeFeatureFlag(
            ENABLE_QUICKSTEP_LIVE_TILE::get, mode -> mode.hasGestures);

    private final Supplier<Boolean> mBasePredicate;
    private final Predicate<SysUINavigationMode.Mode> mModePredicate;
    private boolean mSupported;
    private OverviewComponentObserver mObserver;

    private NavigationModeFeatureFlag(Supplier<Boolean> basePredicate,
            Predicate<SysUINavigationMode.Mode> modePredicate) {
        mBasePredicate = basePredicate;
        mModePredicate = modePredicate;
    }

    public boolean get() {
        return mBasePredicate.get() && mSupported && mObserver.isHomeAndOverviewSame();
    }

    public void initialize(Context context) {
        onNavigationModeChanged(SysUINavigationMode.INSTANCE.get(context).getMode());
        SysUINavigationMode.INSTANCE.get(context).addModeChangeListener(this);

        // Temporary solution to disable live tile for the fallback launcher
        RecentsAnimationDeviceState rads = new RecentsAnimationDeviceState(context);
        mObserver = new OverviewComponentObserver(context, rads);
    }

    @Override
    public void onNavigationModeChanged(SysUINavigationMode.Mode newMode) {
        mSupported = mModePredicate.test(newMode);
    }
}
