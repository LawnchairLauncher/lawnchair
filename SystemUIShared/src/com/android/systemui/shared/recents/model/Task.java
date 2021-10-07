/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.shared.recents.model;

import static android.view.Display.DEFAULT_DISPLAY;

import android.app.ActivityManager;
import android.app.ActivityManager.TaskDescription;
import android.app.TaskInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.ViewDebug;

import com.android.systemui.shared.recents.utilities.Utilities;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Objects;

/**
 * A task in the recent tasks list.
 */
public class Task {

    public static final String TAG = "Task";

    /**
     * The Task Key represents the unique primary key for the task
     */
    public static class TaskKey implements Parcelable {
        @ViewDebug.ExportedProperty(category="recents")
        public final int id;
        @ViewDebug.ExportedProperty(category="recents")
        public int windowingMode;
        @ViewDebug.ExportedProperty(category="recents")
        public final Intent baseIntent;
        @ViewDebug.ExportedProperty(category="recents")
        public final int userId;
        @ViewDebug.ExportedProperty(category="recents")
        public long lastActiveTime;

        /**
         * The id of the task was running from which display.
         */
        @ViewDebug.ExportedProperty(category = "recents")
        public final int displayId;

        // The source component name which started this task
        public final ComponentName sourceComponent;

        private int mHashCode;

        public TaskKey(TaskInfo t) {
            ComponentName sourceComponent = t.origActivity != null
                    // Activity alias if there is one
                    ? t.origActivity
                    // The real activity if there is no alias (or the target if there is one)
                    : t.realActivity;
            this.id = t.taskId;
            this.windowingMode = t.configuration.windowConfiguration.getWindowingMode();
            this.baseIntent = t.baseIntent;
            this.sourceComponent = sourceComponent;
            this.userId = t.userId;
            this.lastActiveTime = t.lastActiveTime;
            this.displayId = t.displayId;
            updateHashCode();
        }

        public TaskKey(int id, int windowingMode, Intent intent,
                ComponentName sourceComponent, int userId, long lastActiveTime) {
            this.id = id;
            this.windowingMode = windowingMode;
            this.baseIntent = intent;
            this.sourceComponent = sourceComponent;
            this.userId = userId;
            this.lastActiveTime = lastActiveTime;
            this.displayId = DEFAULT_DISPLAY;
            updateHashCode();
        }

        public TaskKey(int id, int windowingMode, Intent intent,
                ComponentName sourceComponent, int userId, long lastActiveTime, int displayId) {
            this.id = id;
            this.windowingMode = windowingMode;
            this.baseIntent = intent;
            this.sourceComponent = sourceComponent;
            this.userId = userId;
            this.lastActiveTime = lastActiveTime;
            this.displayId = displayId;
            updateHashCode();
        }

        public void setWindowingMode(int windowingMode) {
            this.windowingMode = windowingMode;
            updateHashCode();
        }

        public ComponentName getComponent() {
            return this.baseIntent.getComponent();
        }

        public String getPackageName() {
            if (this.baseIntent.getComponent() != null) {
                return this.baseIntent.getComponent().getPackageName();
            }
            return this.baseIntent.getPackage();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TaskKey)) {
                return false;
            }
            TaskKey otherKey = (TaskKey) o;
            return id == otherKey.id
                    && windowingMode == otherKey.windowingMode
                    && userId == otherKey.userId;
        }

        @Override
        public int hashCode() {
            return mHashCode;
        }

        @Override
        public String toString() {
            return "id=" + id + " windowingMode=" + windowingMode + " user=" + userId
                    + " lastActiveTime=" + lastActiveTime;
        }

        private void updateHashCode() {
            mHashCode = Objects.hash(id, windowingMode, userId);
        }

        public static final Parcelable.Creator<TaskKey> CREATOR =
                new Parcelable.Creator<TaskKey>() {
                    @Override
                    public TaskKey createFromParcel(Parcel source) {
                        return TaskKey.readFromParcel(source);
                    }

                    @Override
                    public TaskKey[] newArray(int size) {
                        return new TaskKey[size];
                    }
                };

        @Override
        public final void writeToParcel(Parcel parcel, int flags) {
            parcel.writeInt(id);
            parcel.writeInt(windowingMode);
            parcel.writeTypedObject(baseIntent, flags);
            parcel.writeInt(userId);
            parcel.writeLong(lastActiveTime);
            parcel.writeInt(displayId);
            parcel.writeTypedObject(sourceComponent, flags);
        }

        private static TaskKey readFromParcel(Parcel parcel) {
            int id = parcel.readInt();
            int windowingMode = parcel.readInt();
            Intent baseIntent = parcel.readTypedObject(Intent.CREATOR);
            int userId = parcel.readInt();
            long lastActiveTime = parcel.readLong();
            int displayId = parcel.readInt();
            ComponentName sourceComponent = parcel.readTypedObject(ComponentName.CREATOR);

            return new TaskKey(id, windowingMode, baseIntent, sourceComponent, userId,
                    lastActiveTime, displayId);
        }

        @Override
        public int describeContents() {
            return 0;
        }
    }

    @ViewDebug.ExportedProperty(deepExport=true, prefix="key_")
    public TaskKey key;

    /**
     * The icon is the task description icon (if provided), which falls back to the activity icon,
     * which can then fall back to the application icon.
     */
    public Drawable icon;
    public ThumbnailData thumbnail;
    @ViewDebug.ExportedProperty(category="recents")
    @Deprecated
    public String title;
    @ViewDebug.ExportedProperty(category="recents")
    public String titleDescription;
    @ViewDebug.ExportedProperty(category="recents")
    public int colorPrimary;
    @ViewDebug.ExportedProperty(category="recents")
    public int colorBackground;

    /**
     * The task description for this task, only used to reload task icons.
     */
    public TaskDescription taskDescription;

    @ViewDebug.ExportedProperty(category="recents")
    public boolean isDockable;

    @ViewDebug.ExportedProperty(category="recents")
    public ComponentName topActivity;

    @ViewDebug.ExportedProperty(category="recents")
    public boolean isLocked;

    // Last snapshot data, only used for recent tasks
    public ActivityManager.RecentTaskInfo.PersistedTaskSnapshotData lastSnapshotData =
            new ActivityManager.RecentTaskInfo.PersistedTaskSnapshotData();

    public Task() {
        // Do nothing
    }

    /**
     * Creates a task object from the provided task info
     */
    public static Task from(TaskKey taskKey, TaskInfo taskInfo, boolean isLocked) {
        ActivityManager.TaskDescription td = taskInfo.taskDescription;
        return new Task(taskKey,
                td != null ? td.getPrimaryColor() : 0,
                td != null ? td.getBackgroundColor() : 0,
                taskInfo.supportsSplitScreenMultiWindow, isLocked, td, taskInfo.topActivity);
    }

    public Task(TaskKey key) {
        this.key = key;
        this.taskDescription = new TaskDescription();
    }

    public Task(Task other) {
        this(other.key, other.colorPrimary, other.colorBackground, other.isDockable,
                other.isLocked, other.taskDescription, other.topActivity);
        lastSnapshotData.set(other.lastSnapshotData);
    }

    /**
     * Use {@link Task#Task(Task)}.
     */
    @Deprecated
    public Task(TaskKey key, int colorPrimary, int colorBackground,
            boolean isDockable, boolean isLocked, TaskDescription taskDescription,
            ComponentName topActivity) {
        this.key = key;
        this.colorPrimary = colorPrimary;
        this.colorBackground = colorBackground;
        this.taskDescription = taskDescription;
        this.isDockable = isDockable;
        this.isLocked = isLocked;
        this.topActivity = topActivity;
    }

    /**
     * Returns the top activity component.
     */
    public ComponentName getTopComponent() {
        return topActivity != null
                ? topActivity
                : key.baseIntent.getComponent();
    }

    public void setLastSnapshotData(ActivityManager.RecentTaskInfo rawTask) {
        lastSnapshotData.set(rawTask.lastSnapshotData);
    }

    /**
     * Returns the visible width to height ratio. Returns 0f if snapshot data is not available.
     */
    public float getVisibleThumbnailRatio(boolean clipInsets) {
        if (lastSnapshotData.taskSize == null || lastSnapshotData.contentInsets == null) {
            return 0f;
        }

        float availableWidth = lastSnapshotData.taskSize.x;
        float availableHeight = lastSnapshotData.taskSize.y;
        if (clipInsets) {
            availableWidth -=
                    (lastSnapshotData.contentInsets.left + lastSnapshotData.contentInsets.right);
            availableHeight -=
                    (lastSnapshotData.contentInsets.top + lastSnapshotData.contentInsets.bottom);
        }
        return availableWidth / availableHeight;
    }

    @Override
    public boolean equals(Object o) {
        // Check that the id matches
        Task t = (Task) o;
        return key.equals(t.key);
    }

    @Override
    public String toString() {
        return "[" + key.toString() + "] " + title;
    }

    public void dump(String prefix, PrintWriter writer) {
        writer.print(prefix); writer.print(key);
        if (!isDockable) {
            writer.print(" dockable=N");
        }
        if (isLocked) {
            writer.print(" locked=Y");
        }
        writer.print(" "); writer.print(title);
        writer.println();
    }
}
