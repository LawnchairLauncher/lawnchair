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

package com.android.wm.shell.splitscreen.tv;

import android.content.Context;
import android.os.Handler;

import com.android.launcher3.icons.IconProvider;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.LaunchAdjacentController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.SystemWindows;
import com.android.wm.shell.common.TransactionPool;
import com.android.wm.shell.common.split.SplitScreenConstants;
import com.android.wm.shell.recents.RecentTasksController;
import com.android.wm.shell.splitscreen.StageCoordinator;
import com.android.wm.shell.transition.Transitions;

import java.util.Optional;

/**
 * Expands {@link StageCoordinator} functionality with Tv-specific methods.
 */
public class TvStageCoordinator extends StageCoordinator
        implements TvSplitMenuController.StageController {

    private final TvSplitMenuController mTvSplitMenuController;

    public TvStageCoordinator(Context context, int displayId, SyncTransactionQueue syncQueue,
            ShellTaskOrganizer taskOrganizer, DisplayController displayController,
            DisplayImeController displayImeController,
            DisplayInsetsController displayInsetsController, Transitions transitions,
            TransactionPool transactionPool,
            IconProvider iconProvider, ShellExecutor mainExecutor,
            Handler mainHandler,
            Optional<RecentTasksController> recentTasks,
            LaunchAdjacentController launchAdjacentController,
            SystemWindows systemWindows) {
        super(context, displayId, syncQueue, taskOrganizer, displayController, displayImeController,
                displayInsetsController, transitions, transactionPool, iconProvider,
                mainExecutor, recentTasks, launchAdjacentController, Optional.empty());

        mTvSplitMenuController = new TvSplitMenuController(context, this,
                systemWindows, mainHandler);

    }

    @Override
    protected void onSplitScreenEnter() {
        mTvSplitMenuController.addSplitMenuViewToSystemWindows();
        mTvSplitMenuController.registerBroadcastReceiver();
    }

    @Override
    protected void onSplitScreenExit() {
        mTvSplitMenuController.unregisterBroadcastReceiver();
        mTvSplitMenuController.removeSplitMenuViewFromSystemWindows();
    }

    @Override
    public void grantFocusToStage(@SplitScreenConstants.SplitPosition int stageToFocus) {
        super.grantFocusToStage(stageToFocus);
    }

    @Override
    public void exitStage(@SplitScreenConstants.SplitPosition int stageToClose) {
        super.exitStage(stageToClose);
    }

    /**
     * Swaps the stages inside the SplitLayout.
     */
    @Override
    public void swapStages() {
        onDoubleTappedDivider();
    }

}
