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
package com.android.quickstep.util;

import android.content.Context;
import android.view.Display;

import com.android.launcher3.util.window.WindowManagerProxy;

/**
 * Extension of {@link WindowManagerProxy} with some assumption for the default system Launcher
 */
public class SystemWindowManagerProxy extends WindowManagerProxy {

    public SystemWindowManagerProxy(Context context) {
        super(true);
    }

    @Override
    protected String getDisplayId(Display display) {
        return display.getUniqueId();
    }

    @Override
    public boolean isInternalDisplay(Display display) {
        return display.getType() == Display.TYPE_INTERNAL;
    }

    @Override
    public int getRotation(Context context) {
        return context.getResources().getConfiguration().windowConfiguration.getRotation();
    }
}
