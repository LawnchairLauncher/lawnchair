/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.launcher3;

import android.app.Activity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class TranslucentDecor {
    private static final int SYSTEM_UI_FLAG_TRANSPARENT_STATUS = 0x00001000;
    private static final int SYSTEM_UI_FLAG_TRANSPARENT_NAVIGATION = 0x00002000;

    // Replace with SDK constants when available.
    public static final int FLAG_TRANSLUCENT_STATUS = 0x04000000;
    public static final int FLAG_TRANSLUCENT_NAVIGATION = 0x08000000;

    // Behave properly on early K builds.
    public static final boolean SYSUI_SUPPORTED = !hasSystemUiFlag("ALLOW_TRANSIENT") &&
            hasSystemUiFlag("TRANSPARENT_STATUS") &&
            hasSystemUiFlag("TRANSPARENT_NAVIGATION");

    public static final boolean WM_SUPPORTED =
            hasWindowManagerFlag("TRANSLUCENT_STATUS") &&
            hasWindowManagerFlag("TRANSLUCENT_NAVIGATION");

    private final View mTarget;

    public TranslucentDecor(View target) {
        mTarget = target;
    }

    public void requestTranslucentDecor(boolean translucent) {
        int sysui = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (WM_SUPPORTED && mTarget.getContext() instanceof Activity) {
            Window w = ((Activity) mTarget.getContext()).getWindow();
            int wmFlags = FLAG_TRANSLUCENT_STATUS | FLAG_TRANSLUCENT_NAVIGATION;
            if (translucent) {
                w.addFlags(wmFlags);
            } else {
               w.clearFlags(wmFlags);
            }
        } else if (SYSUI_SUPPORTED) {  // Remove when droidfood platform is updated
            if (translucent) {
                sysui |= SYSTEM_UI_FLAG_TRANSPARENT_STATUS | SYSTEM_UI_FLAG_TRANSPARENT_NAVIGATION;
            }
        }
        mTarget.setSystemUiVisibility(sysui);
    }

    private static boolean hasWindowManagerFlag(String name) {
        try {
            return WindowManager.LayoutParams.class.getField("FLAG_" + name) != null;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    private static boolean hasSystemUiFlag(String name) {
        try {
            return View.class.getField("SYSTEM_UI_FLAG_" + name) != null;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }
}
