/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.draganddrop;

import static android.content.ClipDescription.MIMETYPE_APPLICATION_ACTIVITY;
import static android.content.ClipDescription.MIMETYPE_APPLICATION_SHORTCUT;
import static android.content.ClipDescription.MIMETYPE_APPLICATION_TASK;

import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipDescription;
import android.view.DragEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Collection of utility classes for handling drag and drop. */
public class DragUtils {
    private static final String TAG = "DragUtils";

    /**
     * Returns whether we can handle this particular drag.
     */
    public static boolean canHandleDrag(DragEvent event) {
        if (event.getClipData().getItemCount() <= 0) {
            // No clip data, ignore this drag
            return false;
        }
        if (isAppDrag(event.getClipDescription())) {
            // Clip data contains an app drag initiated from SysUI, handle it
            return true;
        }
        if (com.android.window.flags.Flags.delegateUnhandledDrags()
                && getLaunchIntent(event) != null) {
            // Clip data contains a launchable intent drag, handle it
            return true;
        }
        // Otherwise ignore
        return false;
    }

    /**
     * Returns whether this clip data description represents an app drag.
     */
    public static boolean isAppDrag(ClipDescription description) {
        return description.hasMimeType(MIMETYPE_APPLICATION_ACTIVITY)
                || description.hasMimeType(MIMETYPE_APPLICATION_SHORTCUT)
                || description.hasMimeType(MIMETYPE_APPLICATION_TASK);
    }

    /**
     * Returns a launchable intent in the given `DragEvent` or `null` if there is none.
     */
    @Nullable
    public static PendingIntent getLaunchIntent(@NonNull DragEvent dragEvent) {
        return getLaunchIntent(dragEvent.getClipData(), dragEvent.getDragFlags());
    }

    /**
     * Returns a launchable intent in the given `ClipData` or `null` if there is none.
     */
    @Nullable
    public static PendingIntent getLaunchIntent(@NonNull ClipData data, int dragFlags) {
        if ((dragFlags & View.DRAG_FLAG_START_INTENT_SENDER_ON_UNHANDLED_DRAG) == 0) {
            // Disallow launching the intent if the app does not want to delegate it to the system
            return null;
        }
        for (int i = 0; i < data.getItemCount(); i++) {
            final ClipData.Item item = data.getItemAt(i);
            if (item.getIntentSender() != null) {
                final PendingIntent intent = new PendingIntent(item.getIntentSender().getTarget());
                if (intent != null && intent.isActivity()) {
                    return intent;
                }
            }
        }
        return null;
    }

    /**
     * Returns a list of the mime types provided in the clip description.
     */
    public static String getMimeTypesConcatenated(ClipDescription description) {
        String mimeTypes = "";
        for (int i = 0; i < description.getMimeTypeCount(); i++) {
            if (i > 0) {
                mimeTypes += ", ";
            }
            mimeTypes += description.getMimeType(i);
        }
        return mimeTypes;
    }
}
