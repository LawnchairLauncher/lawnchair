/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.views;

import android.content.Context;
import android.content.ContextWrapper;
import android.view.ContextThemeWrapper;
import android.view.View.AccessibilityDelegate;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.graphics.RotationMode;
import com.android.launcher3.dot.DotInfo;

/**
 * An interface to be used along with a context for various activities in Launcher. This allows a
 * generic class to depend on Context subclass instead of an Activity.
 */
public interface ActivityContext {

    default boolean finishAutoCancelActionMode() {
        return false;
    }

    default DotInfo getDotInfoForItem(ItemInfo info) {
        return null;
    }

    /**
     * For items with tree hierarchy, notifies the activity to invalidate the parent when a root
     * is invalidated
     * @param info info associated with a root node.
     */
    default void invalidateParent(ItemInfo info) { }

    default AccessibilityDelegate getAccessibilityDelegate() {
        return null;
    }

    /**
     * The root view to support drag-and-drop and popup support.
     */
    BaseDragLayer getDragLayer();

    DeviceProfile getDeviceProfile();

    /**
     * Device profile to be used by UI elements which are shown directly on top of the wallpaper
     * and whose presentation is tied to the wallpaper (and physical device) and not the activity
     * configuration.
     */
    default DeviceProfile getWallpaperDeviceProfile() {
        return getDeviceProfile();
    }

    default RotationMode getRotationMode() {
        return RotationMode.NORMAL;
    }

    static ActivityContext lookupContext(Context context) {
        if (context instanceof ActivityContext) {
            return (ActivityContext) context;
        } else if (context instanceof ContextThemeWrapper) {
            return lookupContext(((ContextWrapper) context).getBaseContext());
        } else {
            throw new IllegalArgumentException("Cannot find ActivityContext in parent tree");
        }
    }
}
