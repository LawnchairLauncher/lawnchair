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
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View.AccessibilityDelegate;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.ViewCache;

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

    default Rect getFolderBoundingBox() {
        return getDeviceProfile().getAbsoluteOpenFolderBounds();
    }

    /**
     * After calling {@link #getFolderBoundingBox()}, we calculate a (left, top) position for a
     * Folder of size width x height to be within those bounds. However, the chosen position may
     * not be visually ideal (e.g. uncanny valley of centeredness), so here's a chance to update it.
     * @param inOutPosition A 2-size array where the first element is the left position of the open
     *     folder and the second element is the top position. Should be updated in place if desired.
     * @param bounds The bounds that the open folder should fit inside.
     * @param width The width of the open folder.
     * @param height The height of the open folder.
     */
    default void updateOpenFolderPosition(int[] inOutPosition, Rect bounds, int width, int height) {
    }

    /**
     * Returns a LayoutInflater that is cloned in this Context, so that Views inflated by it will
     * have the same Context. (i.e. {@link #lookupContext(Context)} will find this ActivityContext.)
     */
    default LayoutInflater getLayoutInflater() {
        if (this instanceof Context) {
            Context context = (Context) this;
            return LayoutInflater.from(context).cloneInContext(context);
        }
        return null;
    }

    /**
     * The root view to support drag-and-drop and popup support.
     */
    BaseDragLayer getDragLayer();

    DeviceProfile getDeviceProfile();

    default ViewCache getViewCache() {
        return new ViewCache();
    }

    /**
     * Controller for supporting item drag-and-drop
     */
    default <T extends DragController> T getDragController() {
        return null;
    }

    /**
     * Returns the ActivityContext associated with the given Context.
     */
    static <T extends Context & ActivityContext> T lookupContext(Context context) {
        if (context instanceof ActivityContext) {
            return (T) context;
        } else if (context instanceof ContextWrapper) {
            return lookupContext(((ContextWrapper) context).getBaseContext());
        } else {
            throw new IllegalArgumentException("Cannot find ActivityContext in parent tree");
        }
    }
}
