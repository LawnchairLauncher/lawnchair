/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.wm.shell.splitscreen

import android.app.IActivityTaskManager
import android.content.Context
import android.os.Handler
import com.android.launcher3.icons.IconProvider
import com.android.wm.shell.RootDisplayAreaOrganizer
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.bubbles.BubbleController
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayImeController
import com.android.wm.shell.common.DisplayInsetsController
import com.android.wm.shell.common.LaunchAdjacentController
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.common.split.SplitLayout
import com.android.wm.shell.common.split.SplitState
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.recents.RecentTasksController
import com.android.wm.shell.shared.TransactionPool
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.WindowDecorViewModel
import com.google.android.msdl.domain.MSDLPlayer
import java.util.Optional

class StageCoordinator2 : StageCoordinator {
    constructor(
        context: Context, displayId: Int,
        syncQueue: SyncTransactionQueue?,
        taskOrganizer: ShellTaskOrganizer?,
        displayController: DisplayController?,
        displayImeController: DisplayImeController?,
        displayInsetsController: DisplayInsetsController?,
        transitions: Transitions?,
        transactionPool: TransactionPool?,
        iconProvider: IconProvider?,
        mainExecutor: ShellExecutor?, mainHandler: Handler?,
        recentTasks: Optional<RecentTasksController?>?,
        launchAdjacentController: LaunchAdjacentController?,
        windowDecorViewModel: Optional<WindowDecorViewModel?>?,
        splitState: SplitState?,
        desktopTasksController: Optional<DesktopTasksController?>?,
        desktopUserRepositories: Optional<DesktopUserRepositories?>?,
        rootTDAOrganizer: RootTaskDisplayAreaOrganizer?,
        rootDisplayAreaOrganizer: RootDisplayAreaOrganizer?,
        desktopState: DesktopState?,
        activityTaskManager: IActivityTaskManager?,
        msdlPlayer: MSDLPlayer?,
        bubbleController: Optional<BubbleController?>?
    ) : super(
        context, displayId, syncQueue, taskOrganizer, displayController, displayImeController,
        displayInsetsController, transitions, transactionPool, iconProvider, mainExecutor,
        mainHandler, recentTasks, launchAdjacentController, windowDecorViewModel,
        splitState,
        desktopTasksController, desktopUserRepositories, rootTDAOrganizer,
        rootDisplayAreaOrganizer, desktopState, activityTaskManager, msdlPlayer,
        bubbleController
    )

    internal constructor(
        context: Context?, displayId: Int, syncQueue: SyncTransactionQueue?,
        taskOrganizer: ShellTaskOrganizer?, mainStage: StageTaskListener?,
        sideStage: StageTaskListener?,
        displayController: DisplayController?, displayImeController: DisplayImeController?,
        displayInsetsController: DisplayInsetsController?,
        splitLayout: SplitLayout?, transitions: Transitions?,
        transactionPool: TransactionPool?, mainExecutor: ShellExecutor?, mainHandler: Handler?,
        recentTasks: Optional<RecentTasksController?>?,
        launchAdjacentController: LaunchAdjacentController?,
        windowDecorViewModel: Optional<WindowDecorViewModel?>?, splitState: SplitState?,
        desktopTasksController: Optional<DesktopTasksController?>?,
        desktopUserRepositories: Optional<DesktopUserRepositories?>?,
        rootTDAOrganizer: RootTaskDisplayAreaOrganizer?,
        rootDisplayAreaOrganizer: RootDisplayAreaOrganizer?, desktopState: DesktopState?,
        activityTaskManager: IActivityTaskManager?, msdlPlayer: MSDLPlayer?,
        bubbleController: Optional<BubbleController?>?
    ) : super(
        context, displayId, syncQueue, taskOrganizer, mainStage, sideStage, displayController,
        displayImeController, displayInsetsController, splitLayout, transitions,
        transactionPool, mainExecutor, mainHandler, recentTasks, launchAdjacentController,
        windowDecorViewModel, splitState, desktopTasksController, desktopUserRepositories,
        rootTDAOrganizer, rootDisplayAreaOrganizer, desktopState, activityTaskManager,
        msdlPlayer, bubbleController
    )
}
