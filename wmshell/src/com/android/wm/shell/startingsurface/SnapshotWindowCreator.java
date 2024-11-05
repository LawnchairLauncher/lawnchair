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

package com.android.wm.shell.startingsurface;

import android.window.StartingWindowInfo;
import android.window.TaskSnapshot;

import com.android.wm.shell.common.ShellExecutor;

class SnapshotWindowCreator {
    private final ShellExecutor mMainExecutor;
    private final StartingSurfaceDrawer.StartingWindowRecordManager
            mStartingWindowRecordManager;

    SnapshotWindowCreator(ShellExecutor mainExecutor,
            StartingSurfaceDrawer.StartingWindowRecordManager startingWindowRecordManager) {
        mMainExecutor = mainExecutor;
        mStartingWindowRecordManager = startingWindowRecordManager;
    }

    void makeTaskSnapshotWindow(StartingWindowInfo startingWindowInfo, TaskSnapshot snapshot) {
        final int taskId = startingWindowInfo.taskInfo.taskId;
        // Remove any existing starting window for this task before adding.
        mStartingWindowRecordManager.removeWindow(taskId);
        final TaskSnapshotWindow surface = TaskSnapshotWindow.create(startingWindowInfo,
                startingWindowInfo.appToken, snapshot, mMainExecutor,
                () -> mStartingWindowRecordManager.removeWindow(taskId));
        if (surface != null) {
            final SnapshotWindowRecord tView = new SnapshotWindowRecord(surface,
                    startingWindowInfo.taskInfo.topActivityType, mMainExecutor,
                    taskId, mStartingWindowRecordManager);
            mStartingWindowRecordManager.addRecord(taskId, tView);
        }
    }

    private static class SnapshotWindowRecord extends StartingSurfaceDrawer.SnapshotRecord {
        private final TaskSnapshotWindow mTaskSnapshotWindow;

        SnapshotWindowRecord(TaskSnapshotWindow taskSnapshotWindow,
                int activityType, ShellExecutor removeExecutor, int id,
                StartingSurfaceDrawer.StartingWindowRecordManager recordManager) {
            super(activityType, removeExecutor, id, recordManager);
            mTaskSnapshotWindow = taskSnapshotWindow;
            mBGColor = mTaskSnapshotWindow.getBackgroundColor();
        }

        @Override
        protected void removeImmediately() {
            super.removeImmediately();
            mTaskSnapshotWindow.removeImmediately();
        }

        @Override
        protected boolean hasImeSurface() {
            return mTaskSnapshotWindow.hasImeSurface();
        }
    }
}
