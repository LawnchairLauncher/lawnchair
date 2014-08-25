/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ThemeChangedReceiver extends BroadcastReceiver {
    private static final String EXTRA_COMPONENTS = "components";

    public static final String MODIFIES_ICONS = "mods_icons";
    public static final String MODIFIES_FONTS = "mods_fonts";
    public static final String MODIFIES_OVERLAYS = "mods_overlays";

    public void onReceive(Context context, Intent intent) {
        // components is a '|' delimited string of the components that changed
        // due to a theme change.
        String components = intent.getStringExtra(EXTRA_COMPONENTS);
        if (components != null) {
            LauncherAppState.setApplicationContext(context.getApplicationContext());
            LauncherAppState app = LauncherAppState.getInstance();
            if (isInterestingThemeChange(components)) {
                app.getIconCache().flush();
                app.getModel().forceReload();
            }
        }
    }

    /**
     * We consider this an "interesting" theme change if it modifies icons, overlays, or fonts.
     * @param components
     * @return
     */
    private boolean isInterestingThemeChange(String components) {
        return components.contains(MODIFIES_ICONS) || components.contains(MODIFIES_FONTS) ||
                components.contains(MODIFIES_OVERLAYS);
    }
}
