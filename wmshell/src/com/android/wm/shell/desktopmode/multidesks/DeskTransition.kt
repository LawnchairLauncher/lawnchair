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
package com.android.wm.shell.desktopmode.multidesks

import android.graphics.Rect
import android.os.IBinder
import com.android.wm.shell.desktopmode.DesktopModeEventLogger

/** Represents shell-started transitions involving desks. */
sealed interface DeskTransition {
    /** The transition token. */
    val token: IBinder
    /** The user associated with this transition. */
    val userId: Int

    /** Returns a copy of this desk transition with a new transition token. */
    fun copyWithToken(token: IBinder): DeskTransition

    /** A transition to remove a desk and its tasks from a display. */
    data class RemoveDesk(
        override val token: IBinder,
        override val userId: Int,
        val displayId: Int,
        val deskId: Int,
        val tasks: Set<Int>,
        val onDeskRemovedListener: OnDeskRemovedListener?,
        val exitReason: DesktopModeEventLogger.Companion.ExitReason,
    ) : DeskTransition {
        constructor(
            token: IBinder,
            userId: Int,
            displayId: Int,
            deskId: Int,
            tasks: Set<Int>,
            onDeskRemovedListener: OnDeskRemovedListener?,
            exitReason: DesktopModeEventLogger.Companion.ExitReason,
            // TODO(b/415259520): Consolidate desk removed listeners and callback lambdas
            // after verifying that the call order before and after repository does not matter
            // for [DesktopDisplayEventHandler].
            runOnTransitEnd: (() -> Unit)?,
        ) : this(token, userId, displayId, deskId, tasks, onDeskRemovedListener, exitReason) {
            this.runOnTransitEnd = runOnTransitEnd
        }

        var runOnTransitEnd: (() -> Unit)? = null

        override fun copyWithToken(token: IBinder): DeskTransition = copy(token)
    }

    /** A transition to activate a desk in its display. */
    data class ActivateDesk(
        override val token: IBinder,
        override val userId: Int,
        val displayId: Int,
        val deskId: Int,
        val enterReason: DesktopModeEventLogger.Companion.EnterReason,
    ) : DeskTransition {
        constructor(
            token: IBinder,
            userId: Int,
            displayId: Int,
            deskId: Int,
            enterReason: DesktopModeEventLogger.Companion.EnterReason,
            runOnTransitEnd: (() -> Unit)?,
        ) : this(token, userId, displayId, deskId, enterReason) {
            this.runOnTransitEnd = runOnTransitEnd
        }

        var runOnTransitEnd: (() -> Unit)? = null

        override fun copyWithToken(token: IBinder): DeskTransition = copy(token)
    }

    /** A transition to activate a desk by moving an outside task to it. */
    data class ActivateDeskWithTask(
        override val token: IBinder,
        override val userId: Int,
        val displayId: Int,
        val deskId: Int,
        val enterTaskId: Int,
        val enterReason: DesktopModeEventLogger.Companion.EnterReason,
    ) : DeskTransition {
        constructor(
            token: IBinder,
            userId: Int,
            displayId: Int,
            deskId: Int,
            enterTaskId: Int,
            enterReason: DesktopModeEventLogger.Companion.EnterReason,
            runOnTransitEnd: (() -> Unit)?,
        ) : this(token, userId, displayId, deskId, enterTaskId, enterReason) {
            this.runOnTransitEnd = runOnTransitEnd
        }

        var runOnTransitEnd: (() -> Unit)? = null

        override fun copyWithToken(token: IBinder): DeskTransition = copy(token)
    }

    /** A transition to deactivate a desk. */
    data class DeactivateDesk(
        override val token: IBinder,
        override val userId: Int,
        val deskId: Int,
        val displayId: Int,
        val switchingUser: Boolean,
        val exitReason: DesktopModeEventLogger.Companion.ExitReason,
    ) : DeskTransition {
        constructor(
            token: IBinder,
            userId: Int,
            deskId: Int,
            displayId: Int,
            switchingUser: Boolean,
            exitReason: DesktopModeEventLogger.Companion.ExitReason,
            runOnTransitEnd: (() -> Unit)?,
        ) : this(token, userId, deskId, displayId, switchingUser, exitReason) {
            this.runOnTransitEnd = runOnTransitEnd
        }

        var runOnTransitEnd: (() -> Unit)? = null

        override fun copyWithToken(token: IBinder): DeskTransition = copy(token)
    }

    /** A transition to move a desk to a new display */
    data class ChangeDeskDisplay(
        override val token: IBinder,
        override val userId: Int,
        val deskId: Int,
        val displayId: Int,
        val uniqueDisplayId: String?,
    ) : DeskTransition {
        override fun copyWithToken(token: IBinder): DeskTransition = copy(token)
    }

    /** A transition to remove a display and any desks on it. */
    data class RemoveDisplay(
        override val token: IBinder,
        override val userId: Int,
        val displayId: Int,
    ) : DeskTransition {
        override fun copyWithToken(token: IBinder): DeskTransition = copy(token)
    }

    /** A transition to add a task to a desk, including bounds and minimize state */
    data class AddTaskToDesk(
        override val token: IBinder,
        override val userId: Int,
        val displayId: Int,
        val deskId: Int,
        val taskId: Int,
        val taskBounds: Rect?,
        val minimized: Boolean,
    ) : DeskTransition {
        override fun copyWithToken(token: IBinder): DeskTransition = copy(token)
    }
}
