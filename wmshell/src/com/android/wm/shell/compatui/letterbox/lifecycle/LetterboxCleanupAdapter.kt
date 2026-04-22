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

package com.android.wm.shell.compatui.letterbox.lifecycle

import android.app.ActivityManager.RunningTaskInfo
import com.android.window.flags.Flags.appCompatRefactoring
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.suppliers.TransactionSupplier
import com.android.wm.shell.compatui.letterbox.MixedLetterboxController
import com.android.wm.shell.compatui.letterbox.letterboxKey
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.sysui.ShellInit
import javax.inject.Inject

/**
 * This is responsible for listening to the destroy of [Task]s and use the related
 * [LetterboxController] to remove the related surfaces. This makes it easier to detect cases
 * when the letterbox surfaces should be removed completely (e.g. close a task from Recents).
 */
@WMSingleton
class LetterboxCleanupAdapter @Inject constructor(
    shellInit: ShellInit,
    shellTaskOrganizer: ShellTaskOrganizer,
    private val transactionSupplier: TransactionSupplier,
    private val letterboxController: MixedLetterboxController
) : ShellTaskOrganizer.TaskVanishedListener {

    init {
        if (appCompatRefactoring()) {
            shellInit.addInitCallback({
                shellTaskOrganizer.addTaskVanishedListener(this)
            }, this)
        }
    }

    override fun onTaskVanished(taskInfo: RunningTaskInfo) {
        with(transactionSupplier.get()) {
            letterboxController.destroyLetterboxSurface(
                taskInfo.letterboxKey(),
                this
            )
            apply()
        }
        letterboxController.dump()
    }
}