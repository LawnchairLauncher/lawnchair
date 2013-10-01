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

import android.view.View;

public class TransparentBars {
    private static final int SYSTEM_UI_FLAG_TRANSPARENT_STATUS = 0x00001000;
    private static final int SYSTEM_UI_FLAG_TRANSPARENT_NAVIGATION = 0x00002000;

    // Behave properly on early K builds.  Replace with api check once sdk is baked.
    public static final boolean SUPPORTED = !hasSystemUiFlag("ALLOW_TRANSIENT")
            && hasSystemUiFlag("TRANSPARENT_STATUS")
            && hasSystemUiFlag("TRANSPARENT_NAVIGATION");

    private final View mTarget;

    public TransparentBars(View target) {
        mTarget = target;
    }

    public void requestTransparentBars(boolean transparent) {
        if (!SUPPORTED) return;
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                  | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (transparent) {
            flags |= SYSTEM_UI_FLAG_TRANSPARENT_STATUS | SYSTEM_UI_FLAG_TRANSPARENT_NAVIGATION;
        }
        mTarget.setSystemUiVisibility(flags);
    }

    private static boolean hasSystemUiFlag(String name) {
        try {
            return View.class.getField("SYSTEM_UI_FLAG_" + name) != null;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }
}
