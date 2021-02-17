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

package com.android.systemui.plugins;

import android.os.Parcelable;

import com.android.systemui.plugins.annotations.ProvidesInterface;

import java.util.List;

/**
 * Interface to provide SmartspaceTargets to BcSmartspace.
 */
@ProvidesInterface(action = BcSmartspaceDataPlugin.ACTION, version = BcSmartspaceDataPlugin.VERSION)
public interface BcSmartspaceDataPlugin extends Plugin {
    String ACTION = "com.android.systemui.action.PLUGIN_BC_SMARTSPACE_DATA";
    int VERSION = 1;

    /** Register a listener to get Smartspace data. */
    void registerListener(SmartspaceTargetListener listener);

    /** Unregister a listener. */
    void unregisterListener(SmartspaceTargetListener listener);

    /** Provides Smartspace data to registered listeners. */
    interface SmartspaceTargetListener {
        /** Each Parcelable is a SmartspaceTarget that represents a card. */
        void onSmartspaceTargetsUpdated(List<? extends Parcelable> targets);
    }
}
