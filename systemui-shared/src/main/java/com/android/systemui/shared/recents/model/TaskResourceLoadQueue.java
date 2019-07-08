/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.shared.recents.model;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A Task load queue
 */
class TaskResourceLoadQueue {

    private final ConcurrentLinkedQueue<Task> mQueue = new ConcurrentLinkedQueue<>();

    /** Adds a new task to the load queue */
    void addTask(Task t) {
        if (!mQueue.contains(t)) {
            mQueue.add(t);
        }
        synchronized(this) {
            notifyAll();
        }
    }

    /**
     * Retrieves the next task from the load queue, as well as whether we want that task to be
     * force reloaded.
     */
    Task nextTask() {
        return mQueue.poll();
    }

    /** Removes a task from the load queue */
    void removeTask(Task t) {
        mQueue.remove(t);
    }

    /** Clears all the tasks from the load queue */
    void clearTasks() {
        mQueue.clear();
    }

    /** Returns whether the load queue is empty */
    boolean isEmpty() {
        return mQueue.isEmpty();
    }
}
