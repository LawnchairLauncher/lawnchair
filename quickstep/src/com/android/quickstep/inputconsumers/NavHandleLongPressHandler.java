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

package com.android.quickstep.inputconsumers;

import android.content.Context;

import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.util.ResourceBasedOverride;

/**
 * Class for extending nav handle long press behavior
 */
public class NavHandleLongPressHandler implements ResourceBasedOverride {

    /** Creates NavHandleLongPressHandler as specified by overrides */
    public static NavHandleLongPressHandler newInstance(Context context) {
        return Overrides.getObject(NavHandleLongPressHandler.class, context,
                R.string.nav_handle_long_press_handler_class);
    }

    /**
     * Called when nav handle is long pressed to get the Runnable that should be executed by the
     * caller to invoke long press behavior. If null is returned that means long press couldn't be
     * handled.
     * <p>
     * A Runnable is returned here to ensure the InputConsumer can call
     * {@link android.view.InputMonitor#pilferPointers()} before invoking the long press behavior
     * since pilfering can break the long press behavior.
     */
    public @Nullable Runnable getLongPressRunnable() {
        return null;
    }
}
