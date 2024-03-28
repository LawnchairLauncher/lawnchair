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
package com.android.launcher3.util;

import java.util.ArrayList;

/**
 * Utility class to hold a list of runnable
 */
public class RunnableList {

    private ArrayList<Runnable> mList = null;
    private boolean mDestroyed = false;

    /** Adds a runnable to this list */
    public void add(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (mDestroyed) {
            runnable.run();
            return;
        }
        if (mList == null) {
            mList = new ArrayList<>();
        }
        mList.add(runnable);
    }

    /** Removes a previously added runnable */
    public void remove(Runnable runnable) {
        if (mList != null) {
            mList.remove(runnable);
        }
    }

    /**
     * Destroys the list, executing any pending callbacks. All new callbacks are
     * immediately executed
     */
    public void executeAllAndDestroy() {
        mDestroyed = true;
        executeAllAndClear();
    }

    /**
     * Executes all previously added runnable and clears the list
     */
    public void executeAllAndClear() {
        if (mList != null) {
            ArrayList<Runnable> list = mList;
            mList = null;
            int count = list.size();
            for (int i = 0; i < count; i++) {
                list.get(i).run();
            }
        }
    }
}
