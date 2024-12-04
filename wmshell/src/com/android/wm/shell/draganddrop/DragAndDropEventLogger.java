/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.pm.ActivityInfo;
import android.view.DragEvent;

import androidx.annotation.Nullable;

import com.android.internal.logging.InstanceId;
import com.android.internal.logging.InstanceIdSequence;
import com.android.internal.logging.UiEvent;
import com.android.internal.logging.UiEventLogger;

/**
 * Helper class that to log Drag & Drop UIEvents for a single session, see also go/uievent
 */
public class DragAndDropEventLogger {

    private final UiEventLogger mUiEventLogger;
    // Used to generate instance ids for this drag if one is not provided
    private final InstanceIdSequence mIdSequence;

    // Tracks the current drag session
    private ActivityInfo mActivityInfo;
    private InstanceId mInstanceId;

    public DragAndDropEventLogger(UiEventLogger uiEventLogger) {
        mUiEventLogger = uiEventLogger;
        mIdSequence = new InstanceIdSequence(Integer.MAX_VALUE);
    }

    /**
     * Logs the start of a drag.
     */
    public InstanceId logStart(DragSession session) {
        mInstanceId = session.appData != null
                ? session.appData.getParcelableExtra(ClipDescription.EXTRA_LOGGING_INSTANCE_ID,
                        InstanceId.class)
                : null;
        if (mInstanceId == null) {
            mInstanceId = mIdSequence.newInstanceId();
        }
        mActivityInfo = session.activityInfo;
        if (session.appData != null) {
            log(getStartEnum(session.getClipDescription()), mActivityInfo);
        } else {
            // TODO(b/255649902): Update this once we have a new enum
            log(DragAndDropUiEventEnum.GLOBAL_APP_DRAG_START_ACTIVITY, mActivityInfo);
        }
        return mInstanceId;
    }

    /**
     * Logs a successful drop.
     */
    public void logDrop() {
        log(DragAndDropUiEventEnum.GLOBAL_APP_DRAG_DROPPED, mActivityInfo);
    }

    /**
     * Logs the end of a drag.
     */
    public void logEnd() {
        log(DragAndDropUiEventEnum.GLOBAL_APP_DRAG_END, mActivityInfo);
    }

    private void log(UiEventLogger.UiEventEnum event, @Nullable ActivityInfo activityInfo) {
        mUiEventLogger.logWithInstanceId(event,
                activityInfo == null ? 0 : activityInfo.applicationInfo.uid,
                activityInfo == null ? null : activityInfo.applicationInfo.packageName,
                mInstanceId);
    }

    /**
     * Returns the start logging enum for the given drag description.
     */
    private DragAndDropUiEventEnum getStartEnum(ClipDescription description) {
        if (description.hasMimeType(MIMETYPE_APPLICATION_ACTIVITY)) {
            return DragAndDropUiEventEnum.GLOBAL_APP_DRAG_START_ACTIVITY;
        } else if (description.hasMimeType(MIMETYPE_APPLICATION_SHORTCUT)) {
            return DragAndDropUiEventEnum.GLOBAL_APP_DRAG_START_SHORTCUT;
        } else if (description.hasMimeType(MIMETYPE_APPLICATION_TASK)) {
            return DragAndDropUiEventEnum.GLOBAL_APP_DRAG_START_TASK;
        }
        throw new IllegalArgumentException("Not an app drag");
    }

    /**
     * Enums for logging Drag & Drop UiEvents
     */
    public enum DragAndDropUiEventEnum implements UiEventLogger.UiEventEnum {
        @UiEvent(doc = "Starting a global drag and drop of an activity")
        GLOBAL_APP_DRAG_START_ACTIVITY(884),

        @UiEvent(doc = "Starting a global drag and drop of a shortcut")
        GLOBAL_APP_DRAG_START_SHORTCUT(885),

        @UiEvent(doc = "Starting a global drag and drop of a task")
        GLOBAL_APP_DRAG_START_TASK(888),

        @UiEvent(doc = "A global app drag was successfully dropped")
        GLOBAL_APP_DRAG_DROPPED(887),

        @UiEvent(doc = "Ending a global app drag and drop")
        GLOBAL_APP_DRAG_END(886);

        private final int mId;

        DragAndDropUiEventEnum(int id) {
            mId = id;
        }

        @Override
        public int getId() {
            return mId;
        }
    }
}
