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
 * limitations under the License
 */

package com.android.launcher3.taskbar.navbutton;

import android.annotation.DimenRes;
import android.annotation.DrawableRes;
import android.annotation.IdRes;

import com.android.launcher3.R;

/**
 * A class for retrieving resources in Kotlin.
 *
 * This class should be removed once the build system supports resources loading in Kotlin.
 */
public final class LayoutResourceHelper {

    // --------------------------
    // Kids Nav Layout
    @DimenRes
    public static final int DIMEN_TASKBAR_ICON_SIZE_KIDS = R.dimen.taskbar_icon_size_kids;
    @DrawableRes
    public static final int DRAWABLE_SYSBAR_BACK_KIDS = R.drawable.ic_sysbar_back_kids;
    @DrawableRes
    public static final int DRAWABLE_SYSBAR_HOME_KIDS = R.drawable.ic_sysbar_home_kids;
    @DimenRes
    public static final int DIMEN_TASKBAR_HOME_BUTTON_LEFT_MARGIN_KIDS =
            R.dimen.taskbar_home_button_left_margin_kids;
    @DimenRes
    public static final int DIMEN_TASKBAR_BACK_BUTTON_LEFT_MARGIN_KIDS =
            R.dimen.taskbar_back_button_left_margin_kids;
    @DimenRes
    public static final int DIMEN_TASKBAR_NAV_BUTTONS_WIDTH_KIDS =
            R.dimen.taskbar_nav_buttons_width_kids;
    @DimenRes
    public static final int DIMEN_TASKBAR_NAV_BUTTONS_HEIGHT_KIDS =
            R.dimen.taskbar_nav_buttons_height_kids;
    @DimenRes
    public static final int DIMEN_TASKBAR_NAV_BUTTONS_CORNER_RADIUS_KIDS =
            R.dimen.taskbar_nav_buttons_corner_radius_kids;

    // --------------------------
    // Nav Layout Factory
    @IdRes
    public static final int ID_START_CONTEXTUAL_BUTTONS = R.id.start_contextual_buttons;
    @IdRes
    public static final int ID_END_CONTEXTUAL_BUTTONS = R.id.end_contextual_buttons;
    @IdRes
    public static final int ID_END_NAV_BUTTONS = R.id.end_nav_buttons;

    private LayoutResourceHelper() {

    }
}
