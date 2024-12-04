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

package com.android.wm.shell.startingsurface;

import android.app.TaskInfo;
import android.graphics.Color;
/**
 * Interface to engage starting window feature.
 */
public interface StartingSurface {
    /**
     * Returns the background color for a starting window if existing.
     */
    default int getBackgroundColor(TaskInfo taskInfo) {
        return Color.BLACK;
    }

    /** Set the proxy to communicate with SysUi side components. */
    void setSysuiProxy(SysuiProxy proxy);

    /** Callback to tell SysUi components execute some methods. */
    interface SysuiProxy {
        void requestTopUi(boolean requestTopUi, String componentTag);
    }
}
