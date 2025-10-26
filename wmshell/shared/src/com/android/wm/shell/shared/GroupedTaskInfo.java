/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.shared;

import static android.app.WindowConfiguration.windowingModeToString;
import static android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
import static android.view.Display.INVALID_DISPLAY;

import android.annotation.IntDef;
import android.app.ActivityManager.RecentTaskInfo;
import android.app.TaskInfo;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.wm.shell.shared.split.SplitBounds;

import kotlin.collections.CollectionsKt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Simple container for recent tasks which should be presented as a single task within the
 * Overview UI.
 */
public class GroupedTaskInfo implements Parcelable {

    public static final int TYPE_FULLSCREEN = 1;
    public static final int TYPE_SPLIT = 2;
    public static final int TYPE_DESK = 3;
    public static final int TYPE_MIXED = 4;

    @IntDef(prefix = {"TYPE_"}, value = {
            TYPE_FULLSCREEN,
            TYPE_SPLIT,
            TYPE_DESK,
            TYPE_MIXED
    })
    public @interface GroupType {}

    /**
     * The ID of the desk that this `GroupedTaskInfo` represents (when the type is `TYPE_DESK`). The
     * value is -1 if this is not a desk.
     */
    private final int mDeskId;

    /**
     * The ID of the display that desk with [mDeskId] is in.
     */
    private final int mDeskDisplayId;

    /**
     * The type of this particular task info, can be one of TYPE_FULLSCREEN, TYPE_SPLIT or
     * TYPE_DESK.
     */
    @GroupType
    protected final int mType;

    /**
     * The list of tasks associated with this single recent task info.
     * TYPE_FULLSCREEN: Contains the stack of tasks associated with a single "task" in overview
     * TYPE_SPLIT: Contains the two split roots of each side
     * TYPE_DESK: Contains the set of tasks currently in freeform mode contained in desk.
     */
    @Nullable
    protected final List<TaskInfo> mTasks;

    /**
     * Only set for TYPE_SPLIT.
     *
     * Information about the split bounds.
     */
    @Nullable
    protected final SplitBounds mSplitBounds;

    /**
     * Only set for TYPE_DESK.
     *
     * TODO(b/348332802): move isMinimized inside each Task object instead once we have a
     *  replacement for RecentTaskInfo
     */
    @Nullable
    protected final int[] mMinimizedTaskIds;

    /**
     * Only set for TYPE_MIXED.
     *
     * The mixed set of task infos in this group.
     */
    @Nullable
    protected final List<GroupedTaskInfo> mGroupedTasks;

    /**
     * Create new for a stack of fullscreen tasks
     */
    public static GroupedTaskInfo forFullscreenTasks(@NonNull TaskInfo task) {
        return new GroupedTaskInfo(/* deskId = */ -1, /* displayId = */ INVALID_DISPLAY,
                List.of(task), null,
                TYPE_FULLSCREEN, /* minimizedFreeformTaskIds = */ null);
    }

    /**
     * Create new for a pair of tasks in split screen
     */
    public static GroupedTaskInfo forSplitTasks(@NonNull TaskInfo task1,
            @NonNull TaskInfo task2, @Nullable SplitBounds splitBounds) {
        return new GroupedTaskInfo(/* deskId = */ -1, /* displayId = */ INVALID_DISPLAY,
                List.of(task1, task2),
                splitBounds, TYPE_SPLIT, /* minimizedFreeformTaskIds = */ null);
    }

    /**
     * Create new for a group of freeform tasks that belong to a single desk.
     */
    public static GroupedTaskInfo forDeskTasks(
            int deskId,
            int deskDisplayId,
            @NonNull List<TaskInfo> tasks,
            @NonNull Set<Integer> minimizedFreeformTaskIds) {
        return new GroupedTaskInfo(deskId, deskDisplayId, tasks, /* splitBounds = */ null,
                TYPE_DESK,
                minimizedFreeformTaskIds.stream().mapToInt(i -> i).toArray());
    }

    /**
     * Create new for a group of grouped task infos, those grouped task infos may not be mixed
     * themselves (ie. multiple depths of mixed grouped task infos are not allowed).
     */
    public static GroupedTaskInfo forMixed(@NonNull List<GroupedTaskInfo> groupedTasks) {
        if (groupedTasks.isEmpty()) {
            throw new IllegalArgumentException("Expected non-empty grouped task list");
        }
        if (groupedTasks.stream().anyMatch(task -> task.mType == TYPE_MIXED)) {
            throw new IllegalArgumentException("Unexpected grouped task list");
        }
        return new GroupedTaskInfo(groupedTasks);
    }

    private GroupedTaskInfo(
            int deskId,
            int deskDisplayId,
            @NonNull List<TaskInfo> tasks,
            @Nullable SplitBounds splitBounds,
            @GroupType int type,
            @Nullable int[] minimizedFreeformTaskIds) {
        mDeskId = deskId;
        mDeskDisplayId = deskDisplayId;
        mTasks = tasks;
        mGroupedTasks = null;
        mSplitBounds = splitBounds;
        mType = type;
        mMinimizedTaskIds = minimizedFreeformTaskIds;
        ensureAllMinimizedIdsPresent(tasks, minimizedFreeformTaskIds);
    }

    private GroupedTaskInfo(@NonNull List<GroupedTaskInfo> groupedTasks) {
        mDeskId = -1;
        mDeskDisplayId = INVALID_DISPLAY;
        mTasks = null;
        mGroupedTasks = groupedTasks;
        mSplitBounds = null;
        mType = TYPE_MIXED;
        mMinimizedTaskIds = null;
    }

    private void ensureAllMinimizedIdsPresent(
            @NonNull List<TaskInfo> tasks,
            @Nullable int[] minimizedFreeformTaskIds) {
        if (minimizedFreeformTaskIds == null) {
            return;
        }
        if (!Arrays.stream(minimizedFreeformTaskIds).allMatch(
                taskId -> tasks.stream().anyMatch(task -> task.taskId == taskId))) {
            throw new IllegalArgumentException("Minimized task IDs contain non-existent Task ID.");
        }
    }

    protected GroupedTaskInfo(@NonNull Parcel parcel) {
        mDeskId = parcel.readInt();
        mDeskDisplayId = parcel.readInt();
        mTasks = new ArrayList();
        final int numTasks = parcel.readInt();
        for (int i = 0; i < numTasks; i++) {
            mTasks.add(new TaskInfo(parcel));
        }
        mGroupedTasks = parcel.createTypedArrayList(GroupedTaskInfo.CREATOR);
        mSplitBounds = parcel.readTypedObject(SplitBounds.CREATOR);
        mType = parcel.readInt();
        mMinimizedTaskIds = parcel.createIntArray();
    }

    /**
     * If TYPE_MIXED, returns the root of the grouped tasks
     * For all other types, returns this task itself
     */
    @NonNull
    public GroupedTaskInfo getBaseGroupedTask() {
        if (mType == TYPE_MIXED) {
            return mGroupedTasks.getFirst();
        }
        return this;
    }

    /**
     * Get primary {@link TaskInfo}.
     *
     * @throws IllegalStateException if the group is TYPE_MIXED.
     */
    @NonNull
    public TaskInfo getTaskInfo1() {
        if (mType == TYPE_MIXED) {
            throw new IllegalStateException("No indexed tasks for a mixed task");
        }
        return mTasks.getFirst();
    }

    /**
     * Get secondary {@link TaskInfo}, used primarily for TYPE_SPLIT.
     *
     * @throws IllegalStateException if the group is TYPE_MIXED.
     */
    @Nullable
    public TaskInfo getTaskInfo2() {
        if (mType == TYPE_MIXED) {
            throw new IllegalStateException("No indexed tasks for a mixed task");
        }
        if (mTasks.size() > 1) {
            return mTasks.get(1);
        }
        return null;
    }

    /**
     * @return The task info for the task in this group with the given {@code taskId}.
     */
    @Nullable
    public TaskInfo getTaskById(int taskId) {
        return CollectionsKt.firstOrNull(getTaskInfoList(), taskInfo -> taskInfo.taskId == taskId);
    }

    /**
     * Get all {@link RecentTaskInfo}s grouped together.
     */
    @NonNull
    public List<TaskInfo> getTaskInfoList() {
        if (mType == TYPE_MIXED) {
            return CollectionsKt.flatMap(mGroupedTasks, groupedTaskInfo -> groupedTaskInfo.mTasks);
        } else {
            return mTasks;
        }
    }

    /**
     * @return Whether this grouped task contains a task with the given {@code taskId}.
     */
    public boolean containsTask(int taskId) {
        return getTaskById(taskId) != null;
    }

    /**
     * Returns whether the group is of the given type, if this is a TYPE_MIXED group, then returns
     * whether the root task info is of the given type.
     */
    public boolean isBaseType(@GroupType int type) {
        return getBaseGroupedTask().mType == type;
    }

    /**
     * Return {@link SplitBounds} if this is a split screen entry or {@code null}. Only valid for
     * TYPE_SPLIT.
     */
    @Nullable
    public SplitBounds getSplitBounds() {
        if (mType == TYPE_MIXED) {
            throw new IllegalStateException("No split bounds for a mixed task");
        }
        return mSplitBounds;
    }

    /**
     * Returns the ID of the desk represented by `this` if the type is `TYPE_DESK`, or -1 otherwise.
     */
    public int getDeskId() {
        if (mType == TYPE_MIXED) {
            throw new IllegalStateException("No desk ID for a mixed task");
        }
        return mDeskId;
    }

    /**
     * Returns the ID of the display that hosts the desk represented by [mDeskId].
     */
    public int getDeskDisplayId() {
        if (mType != TYPE_DESK) {
            throw new IllegalStateException("No display ID for non desktop task");
        }
        return mDeskDisplayId;
    }

    /**
     * Get type of this recents entry. One of {@link GroupType}.
     * Note: This is deprecated, callers should use `isBaseType()` and not make assumptions about
     *       specific group types
     */
    @Deprecated
    @GroupType
    public int getType() {
        return mType;
    }

    /**
     * Returns the set of minimized task ids, only valid for TYPE_DESK.
     */
    @Nullable
    public int[] getMinimizedTaskIds() {
        if (mType == TYPE_MIXED) {
            throw new IllegalStateException("No minimized task ids for a mixed task");
        }
        return mMinimizedTaskIds;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GroupedTaskInfo)) {
            return false;
        }
        GroupedTaskInfo other = (GroupedTaskInfo) obj;
        return mDeskId == other.mDeskId
                && mDeskDisplayId == other.mDeskDisplayId
                && mType == other.mType
                && Objects.equals(mTasks, other.mTasks)
                && Objects.equals(mGroupedTasks, other.mGroupedTasks)
                && Objects.equals(mSplitBounds, other.mSplitBounds)
                && Arrays.equals(mMinimizedTaskIds, other.mMinimizedTaskIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDeskId, mDeskDisplayId, mType, mTasks, mGroupedTasks, mSplitBounds,
                Arrays.hashCode(mMinimizedTaskIds));
    }

    @Override
    public String toString() {
        StringBuilder taskString = new StringBuilder();
        if (mType == TYPE_MIXED) {
            taskString.append("GroupedTasks=" + mGroupedTasks.stream()
                    .map(GroupedTaskInfo::toString)
                    .collect(Collectors.joining(",\n\t", "[\n\t", "\n]")));
        } else {
            taskString.append("Desk ID= ").append(mDeskId).append(", ");
            taskString.append("Desk Display ID=").append(mDeskDisplayId).append(", ");
            taskString.append("Tasks=" + mTasks.stream()
                    .map(taskInfo -> getTaskInfoDumpString(taskInfo))
                    .collect(Collectors.joining(", ", "[", "]")));
            if (mSplitBounds != null) {
                taskString.append(", SplitBounds=").append(mSplitBounds);
            }
            taskString.append(", Type=" + typeToString(mType));
            taskString.append(", Minimized Task IDs=" + Arrays.toString(mMinimizedTaskIds));
        }
        return taskString.toString();
    }

    private String getTaskInfoDumpString(TaskInfo taskInfo) {
        if (taskInfo == null) {
            return null;
        }
        final boolean isExcluded = (taskInfo.baseIntent.getFlags()
                & FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) != 0;
        return "id=" + taskInfo.taskId
                + " winMode=" + windowingModeToString(taskInfo.getWindowingMode())
                + " visReq=" + taskInfo.isVisibleRequested
                + " vis=" + taskInfo.isVisible
                + " excluded=" + isExcluded
                + " baseIntent="
                + (taskInfo.baseIntent != null && taskInfo.baseIntent.getComponent() != null
                        ? taskInfo.baseIntent.getComponent().flattenToShortString()
                        : "null");
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mDeskId);
        parcel.writeInt(mDeskDisplayId);
        // We don't use the parcel list methods because we want to only write the TaskInfo state
        // and not the subclasses (Recents/RunningTaskInfo) whose fields are all deprecated
        final int tasksSize = mTasks != null ? mTasks.size() : 0;
        parcel.writeInt(tasksSize);
        for (int i = 0; i < tasksSize; i++) {
            mTasks.get(i).writeTaskToParcel(parcel, flags);
        }
        parcel.writeTypedList(mGroupedTasks);
        parcel.writeTypedObject(mSplitBounds, flags);
        parcel.writeInt(mType);
        parcel.writeIntArray(mMinimizedTaskIds);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private String typeToString(@GroupType int type) {
        return switch (type) {
            case TYPE_FULLSCREEN -> "FULLSCREEN";
            case TYPE_SPLIT -> "SPLIT";
            case TYPE_DESK -> "DESK";
            case TYPE_MIXED -> "MIXED";
            default -> "UNKNOWN";
        };
    }

    public static final Creator<GroupedTaskInfo> CREATOR = new Creator() {
        @Override
        public GroupedTaskInfo createFromParcel(Parcel in) {
            return new GroupedTaskInfo(in);
        }

        @Override
        public GroupedTaskInfo[] newArray(int size) {
            return new GroupedTaskInfo[size];
        }
    };
}
