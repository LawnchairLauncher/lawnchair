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
     * Called when nav handle is long pressed.
     *
     * @return if the long press was consumed, meaning other input consumers should receive a
     * cancel event
     */
    public boolean onLongPress() {
        return false;
    }
}
