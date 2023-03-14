/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.quickstep;

import androidx.annotation.Nullable;

import com.android.quickstep.util.GroupTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Keeps track of the state of {@code RecentsView}.
 *
 * <p> More specifically, used for keeping track of the state of filters applied on tasks
 * in {@code RecentsView} for multi-instance management.
 */
public class RecentsFilterState {
    // the minimum number of tasks per package present to allow filtering
    public static final int MIN_FILTERING_TASK_COUNT = 2;

    // default filter that returns true for any input
    public static final Predicate<GroupTask> DEFAULT_FILTER = (groupTask -> true);

    // the package name to filter recent tasks by
    @Nullable
    private String mPackageNameToFilter = null;

    // the callback that gets executed upon filter change
    @Nullable
    private Runnable mOnFilterUpdatedListener = null;

    // map maintaining the count for each unique base activity package name currently in the recents
    @Nullable
    private Map<String, Integer> mInstanceCountMap;

    /**
     * Returns {@code true} if {@code RecentsView} filters tasks by some package name.
     */
    public boolean isFiltered() {
        return mPackageNameToFilter != null;
    }

    /**
     * Returns the package name that tasks are filtered by.
     */
    @Nullable
    public String getPackageNameToFilter() {
        return mPackageNameToFilter;
    }


    /**
     * Sets a listener on any changes to the filter.
     *
     * @param callback listener to be executed upon filter updates
     */
    public void setOnFilterUpdatedListener(@Nullable Runnable callback) {
        mOnFilterUpdatedListener = callback;
    }

    /**
     * Updates the filter such that tasks are filtered by a certain package name.
     *
     * @param packageName package name of the base activity to filter tasks by;
     *                    if null, filter is turned off
     */
    public void setFilterBy(@Nullable String packageName) {
        if (Objects.equals(packageName, mPackageNameToFilter)) {
            return;
        }

        mPackageNameToFilter = packageName;

        if (mOnFilterUpdatedListener != null) {
            mOnFilterUpdatedListener.run();
        }
    }

    /**
     * Updates the map of package names to their count in the most recent list of tasks.
     *
     * @param groupTaskList the list of tasks that map update is be based on
     */
    public void updateInstanceCountMap(List<GroupTask> groupTaskList) {
        mInstanceCountMap = getInstanceCountMap(groupTaskList);
    }

    /**
     * Returns the map of package names to their count in the most recent list of tasks.
     */
    @Nullable
    public Map<String, Integer> getInstanceCountMap() {
        return mInstanceCountMap;
    }

    /**
     * Returns a predicate for filtering out GroupTasks by package name.
     *
     * @param packageName package name to filter GroupTasks by
     *                    if null, Predicate always returns true.
     */
    public static Predicate<GroupTask> getFilter(@Nullable String packageName) {
        if (packageName == null) {
            return DEFAULT_FILTER;
        }

        return (groupTask) -> (groupTask.task2 != null
                && groupTask.task2.key.getPackageName().equals(packageName))
                || groupTask.task1.key.getPackageName().equals(packageName);
    }

    /**
     * Returns a map of package names to their frequencies in a list of GroupTasks.
     *
     * @param groupTasks the list to go through to create the map
     */
    public static Map<String, Integer> getInstanceCountMap(List<GroupTask> groupTasks) {
        Map<String, Integer> instanceCountMap = new HashMap<>();

        for (GroupTask groupTask : groupTasks) {
            final String firstTaskPkgName = groupTask.task1.key.getPackageName();
            final String secondTaskPkgName =
                    groupTask.task2 == null ? null : groupTask.task2.key.getPackageName();

            // increment the instance count for the first task's base activity package name
            incrementOrAddIfNotExists(instanceCountMap, firstTaskPkgName);

            // check if second task is non existent
            if (secondTaskPkgName != null) {
                // increment the instance count for the second task's base activity package name
                incrementOrAddIfNotExists(instanceCountMap, secondTaskPkgName);
            }
        }

        return instanceCountMap;
    }

    /**
     * Returns true if tasks of provided package name should show filter UI.
     *
     * @param taskPackageName package name of the task in question
     */
    public boolean shouldShowFilterUI(String taskPackageName) {
        // number of occurrences in recents overview with the package name of this task
        int instanceCount = getInstanceCountMap().get(taskPackageName);

        // if the number of occurrences isn't enough make sure tasks can't be filtered by
        // the package name of this task
        return !(isFiltered() || instanceCount < MIN_FILTERING_TASK_COUNT);
    }

    private static void incrementOrAddIfNotExists(Map<String, Integer> map, String pkgName) {
        if (!map.containsKey(pkgName)) {
            map.put(pkgName, 0);
        }
        map.put(pkgName, map.get(pkgName) + 1);
    }
}
