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

package com.android.wm.shell.common;

import static android.content.Intent.EXTRA_DOCK_STATE;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.android.wm.shell.dagger.WMSingleton;

import javax.inject.Inject;

/**
 * Provides information about the docked state of the device.
 */
@WMSingleton
public class DockStateReader {

    private static final IntentFilter DOCK_INTENT_FILTER = new IntentFilter(
            Intent.ACTION_DOCK_EVENT);

    private final Context mContext;

    @Inject
    public DockStateReader(Context context) {
        mContext = context;
    }

    /**
     * @return True if the device is docked and false otherwise.
     */
    public boolean isDocked() {
        Intent dockStatus = mContext.registerReceiver(/* receiver */ null, DOCK_INTENT_FILTER);
        if (dockStatus != null) {
            int dockState = dockStatus.getIntExtra(EXTRA_DOCK_STATE,
                    Intent.EXTRA_DOCK_STATE_UNDOCKED);
            return dockState != Intent.EXTRA_DOCK_STATE_UNDOCKED;
        }
        return false;
    }
}
