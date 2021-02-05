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

    /* Task callbacks */
    @Deprecated
    public interface TaskCallbacks {
        /* Notifies when a task has been bound */
        void onTaskDataLoaded(Task task, ThumbnailData thumbnailData);
        /* Notifies when a task has been unbound */
        void onTaskDataUnloaded();
        /* Notifies when a task's windowing mode has changed. */
        void onTaskWindowingModeChanged();
    }

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
     * The temporary sort index in the stack, used when ordering the stack.
     */
    @Deprecated
    public int temporarySortIndexInStack;

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
    @ViewDebug.ExportedProperty(category="recents")
    @Deprecated
    public boolean useLightOnPrimaryColor;

    /**
     * The task description for this task, only used to reload task icons.
     */
    public TaskDescription taskDescription;

    /**
     * The state isLaunchTarget will be set for the correct task upon launching Recents.
     */
    @ViewDebug.ExportedProperty(category="recents")
    @Deprecated
    public boolean isLaunchTarget;
    @ViewDebug.ExportedProperty(category="recents")
    @Deprecated
    public boolean isStackTask;
    @ViewDebug.ExportedProperty(category="recents")
    @Deprecated
    public boolean isSystemApp;
    @ViewDebug.ExportedProperty(category="recents")
    public boolean isDockable;

    /**
     * Resize mode. See {@link ActivityInfo#resizeMode}.
     */
    @ViewDebug.ExportedProperty(category="recents")
    @Deprecated
    public int resizeMode;

    @ViewDebug.ExportedProperty(category="recents")
    public ComponentName topActivity;

    @ViewDebug.ExportedProperty(category="recents")
    public boolean isLocked;

    @Deprecated
    private ArrayList<TaskCallbacks> mCallbacks = new ArrayList<>();

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

    @Deprecated
    public Task(TaskKey key, Drawable icon, ThumbnailData thumbnail, String title,
            String titleDescription, int colorPrimary, int colorBackground, boolean isLaunchTarget,
            boolean isStackTask, boolean isSystemApp, boolean isDockable,
            TaskDescription taskDescription, int resizeMode, ComponentName topActivity,
            boolean isLocked) {
        this.key = key;
        this.icon = icon;
        this.thumbnail = thumbnail;
        this.title = title;
        this.titleDescription = titleDescription;
        this.colorPrimary = colorPrimary;
        this.colorBackground = colorBackground;
        this.useLightOnPrimaryColor = Utilities.computeContrastBetweenColors(this.colorPrimary,
                Color.WHITE) > 3f;
        this.taskDescription = taskDescription;
        this.isLaunchTarget = isLaunchTarget;
        this.isStackTask = isStackTask;
        this.isSystemApp = isSystemApp;
        this.isDockable = isDockable;
        this.resizeMode = resizeMode;
        this.topActivity = topActivity;
        this.isLocked = isLocked;
    }

    /**
     * Copies the metadata from another task, but retains the current callbacks.
     */
    @Deprecated
    public void copyFrom(Task o) {
        this.key = o.key;
        this.icon = o.icon;
        this.thumbnail = o.thumbnail;
        this.title = o.title;
        this.titleDescription = o.titleDescription;
        this.colorPrimary = o.colorPrimary;
        this.colorBackground = o.colorBackground;
        this.useLightOnPrimaryColor = o.useLightOnPrimaryColor;
        this.taskDescription = o.taskDescription;
        this.isLaunchTarget = o.isLaunchTarget;
        this.isStackTask = o.isStackTask;
        this.isSystemApp = o.isSystemApp;
        this.isDockable = o.isDockable;
        this.resizeMode = o.resizeMode;
        this.isLocked = o.isLocked;
        this.topActivity = o.topActivity;
    }

    /**
     * Add a callback.
     */
    @Deprecated
    public void addCallback(TaskCallbacks cb) {
        if (!mCallbacks.contains(cb)) {
            mCallbacks.add(cb);
        }
    }

    /**
     * Remove a callback.
     */
    @Deprecated
    public void removeCallback(TaskCallbacks cb) {
        mCallbacks.remove(cb);
    }

    /** Updates the task's windowing mode. */
    @Deprecated
    public void setWindowingMode(int windowingMode) {
        key.setWindowingMode(windowingMode);
        int callbackCount = mCallbacks.size();
        for (int i = 0; i < callbackCount; i++) {
            mCallbacks.get(i).onTaskWindowingModeChanged();
        }
    }

    /** Notifies the callback listeners that this task has been loaded */
    @Deprecated
    public void notifyTaskDataLoaded(ThumbnailData thumbnailData, Drawable applicationIcon) {
        this.icon = applicationIcon;
        this.thumbnail = thumbnailData;
        int callbackCount = mCallbacks.size();
        for (int i = 0; i < callbackCount; i++) {
            mCallbacks.get(i).onTaskDataLoaded(this, thumbnailData);
        }
    }

    /** Notifies the callback listeners that this task has been unloaded */
    @Deprecated
    public void notifyTaskDataUnloaded(Drawable defaultApplicationIcon) {
        icon = defaultApplicationIcon;
        thumbnail = null;
        for (int i = mCallbacks.size() - 1; i >= 0; i--) {
            mCallbacks.get(i).onTaskDataUnloaded();
        }
    }

    /**
     * Returns the top activity component.
     */
    public ComponentName getTopComponent() {
        return topActivity != null
                ? topActivity
                : key.baseIntent.getComponent();
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
        if (isLaunchTarget) {
            writer.print(" launchTarget=Y");
        }
        if (isLocked) {
            writer.print(" locked=Y");
        }
        writer.print(" "); writer.print(title);
        writer.println();
    }
}
