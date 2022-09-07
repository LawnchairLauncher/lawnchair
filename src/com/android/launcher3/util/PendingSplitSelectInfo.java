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

package com.android.launcher3.util;

import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition;

/**
 * Utility class to store information regarding a split select request. This includes the taskId of
 * the originating task, plus the stage position.
 * This information is intended to be saved across launcher instances, e.g. when Launcher needs to
 * recover straight into a split select state.
 */
public class PendingSplitSelectInfo {

    private final int mStagedTaskId;
    private final int mStagePosition;
    private final StatsLogManager.EventEnum mSource;

    public PendingSplitSelectInfo(int stagedTaskId, int stagePosition,
            StatsLogManager.EventEnum source) {
        this.mStagedTaskId = stagedTaskId;
        this.mStagePosition = stagePosition;
        this.mSource = source;
    }

    public int getStagedTaskId() {
        return mStagedTaskId;
    }

    public @StagePosition int getStagePosition() {
        return mStagePosition;
    }

    public StatsLogManager.EventEnum getSource() {
        return mSource;
    }
}
