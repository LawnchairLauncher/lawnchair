/*
 * Copyright (C) 2019 The Android Open Source Project
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

import java.io.PrintWriter;

/**
 * Utility class used to store state information shared across multiple transitions.
 */
public class SwipeSharedState {

    public boolean canGestureBeContinued;
    public boolean goingToLauncher;
    public boolean recentsAnimationFinishInterrupted;
    public int nextRunningTaskId = -1;
    private int mLogId;

    /**
     * Called when a recents animation has finished, but was interrupted before the next task was
     * launched. The given {@param runningTaskId} should be used as the running task for the
     * continuing input consumer.
     */
    public void setRecentsAnimationFinishInterrupted(int runningTaskId) {
        recentsAnimationFinishInterrupted = true;
        nextRunningTaskId = runningTaskId;
    }

    public void clearAllState() {
        canGestureBeContinued = false;
        recentsAnimationFinishInterrupted = false;
        nextRunningTaskId = -1;
        goingToLauncher = false;
    }

    public void dump(String prefix, PrintWriter pw) {
        pw.println(prefix + "goingToLauncher=" + goingToLauncher);
        pw.println(prefix + "canGestureBeContinued=" + canGestureBeContinued);
        pw.println(prefix + "recentsAnimationFinishInterrupted=" + recentsAnimationFinishInterrupted);
        pw.println(prefix + "nextRunningTaskId=" + nextRunningTaskId);
        pw.println(prefix + "logTraceId=" + mLogId);
    }

    public void setLogTraceId(int logId) {
        this.mLogId = logId;
    }
}
