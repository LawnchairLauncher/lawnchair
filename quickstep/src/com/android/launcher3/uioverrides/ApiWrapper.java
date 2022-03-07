/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.uioverrides;

import android.app.Person;
import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.content.res.Resources;
import android.view.Display;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.DisplayController.NavigationMode;

public class ApiWrapper {

    public static final boolean TASKBAR_DRAWN_IN_PROCESS = true;

    public static Person[] getPersons(ShortcutInfo si) {
        Person[] persons = si.getPersons();
        return persons == null ? Utilities.EMPTY_PERSON_ARRAY : persons;
    }

    /**
     * Returns true if the display is an internal displays
     */
    public static boolean isInternalDisplay(Display display) {
        return display.getType() == Display.TYPE_INTERNAL;
    }

    /**
     * Returns a unique ID representing the display
     */
    public static String getUniqueId(Display display) {
        return display.getUniqueId();
    }

    /**
     * Returns the minimum space that should be left empty at the end of hotseat
     */
    public static int getHotseatEndOffset(Context context) {
        if (DisplayController.getNavigationMode(context) == NavigationMode.THREE_BUTTONS) {
            Resources res = context.getResources();
            /*
            * 3 nav buttons +
            * Little space at the end for contextual buttons +
            * Little space between icons and nav buttons
            */
            return 3 * res.getDimensionPixelSize(R.dimen.taskbar_nav_buttons_size)
                    + res.getDimensionPixelSize(R.dimen.taskbar_contextual_button_margin)
                    + res.getDimensionPixelSize(R.dimen.taskbar_hotseat_nav_spacing);
        } else {
            return 0;
        }

    }
}
