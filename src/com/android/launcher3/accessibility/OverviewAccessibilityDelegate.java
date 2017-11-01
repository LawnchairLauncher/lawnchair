/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.accessibility;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;

/**
 * Accessibility delegate with actions pointing to various Overview entry points.
 */
public class OverviewAccessibilityDelegate extends AccessibilityDelegate {

    private static final int OVERVIEW = R.string.accessibility_action_overview;
    private static final int WALLPAPERS = R.string.wallpaper_button_text;
    private static final int WIDGETS = R.string.widget_button_text;
    private static final int SETTINGS = R.string.settings_button_text;

    @Override
    public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(host, info);

        Context context = host.getContext();
        info.addAction(new AccessibilityAction(OVERVIEW, context.getText(OVERVIEW)));

        if (Utilities.isWallpaperAllowed(context)) {
            info.addAction(new AccessibilityAction(WALLPAPERS, context.getText(WALLPAPERS)));
        }
        info.addAction(new AccessibilityAction(WIDGETS, context.getText(WIDGETS)));
        info.addAction(new AccessibilityAction(SETTINGS, context.getText(SETTINGS)));
    }

    @Override
    public boolean performAccessibilityAction(View host, int action, Bundle args) {
        Launcher launcher = Launcher.getLauncher(host.getContext());
        if (action == OVERVIEW) {
            launcher.showOverviewMode(true);
            return true;
        } else if (action == WALLPAPERS) {
            launcher.onClickWallpaperPicker(host);
            return true;
        } else if (action == WIDGETS) {
            launcher.onClickAddWidgetButton(host);
            return true;
        } else if (action == SETTINGS) {
            launcher.onClickSettingsButton(host);
            return true;
        }
        return super.performAccessibilityAction(host, action, args);
    }
}
