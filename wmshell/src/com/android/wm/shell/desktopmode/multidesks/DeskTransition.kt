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

import android.os.IBinder

/** Represents shell-started transitions involving desks. */
sealed interface DeskTransition {
    /** The transition token. */
    val token: IBinder

    /** Returns a copy of this desk transition with a new transition token. */
    fun copyWithToken(token: IBinder): DeskTransition

    /** A transition to remove a desk and its tasks from a display. */
    data class RemoveDesk(
        override val token: IBinder,
        val displayId: Int,
        val deskId: Int,
        val tasks: Set<Int>,
        val onDeskRemovedListener: OnDeskRemovedListener?,
    ) : DeskTransition {
        constructor(
            token: IBinder,
            displayId: Int,
            deskId: Int,
            tasks: Set<Int>,
            onDeskRemovedListener: OnDeskRemovedListener?,
            // TODO(b/415259520): Consolidate desk removed listeners and callback lambdas
            // after verifying that the call order before and after repository does not matter
            // for [DesktopDisplayEventHandler].
            runOnTransitEnd: (() -> Unit)?,
        ) : this(token, displayId, deskId, tasks, onDeskRemovedListener) {
            this.runOnTransitEnd = runOnTransitEnd
        }

        var runOnTransitEnd: (() -> Unit)? = null

        override fun copyWithToken(token: IBinder): DeskTransition = copy(token)
    }

    /** A transition to activate a desk in its display. */
    data class ActivateDesk(override val token: IBinder, val displayId: Int, val deskId: Int) :
        DeskTransition {
        constructor(
            token: IBinder,
            displayId: Int,
            deskId: Int,
            runOnTransitEnd: (() -> Unit)?,
        ) : this(token, displayId, deskId) {
            this.runOnTransitEnd = runOnTransitEnd
        }

        var runOnTransitEnd: (() -> Unit)? = null

        override fun copyWithToken(token: IBinder): DeskTransition = copy(token)
    }

    /** A transition to activate a desk by moving an outside task to it. */
    data class ActivateDeskWithTask(
        override val token: IBinder,
        val displayId: Int,
        val deskId: Int,
        val enterTaskId: Int,
    ) : DeskTransition {
        constructor(
            token: IBinder,
            displayId: Int,
            deskId: Int,
            enterTaskId: Int,
            runOnTransitEnd: (() -> Unit)?,
        ) : this(token, displayId, deskId, enterTaskId) {
            this.runOnTransitEnd = runOnTransitEnd
        }

        var runOnTransitEnd: (() -> Unit)? = null

        override fun copyWithToken(token: IBinder): DeskTransition = copy(token)
    }

    /** A transition to deactivate a desk. */
    data class DeactivateDesk(override val token: IBinder, val deskId: Int) : DeskTransition {
        constructor(
            token: IBinder,
            deskId: Int,
            runOnTransitEnd: (() -> Unit)?,
        ) : this(token, deskId) {
            this.runOnTransitEnd = runOnTransitEnd
        }

        var runOnTransitEnd: (() -> Unit)? = null

        override fun copyWithToken(token: IBinder): DeskTransition = copy(token)
    }

    /** A transition to move a desk to a new display */
    data class ChangeDeskDisplay(override val token: IBinder, val deskId: Int, val displayId: Int) :
        DeskTransition {
        override fun copyWithToken(token: IBinder): DeskTransition = copy(token)
    }

    /** A transition to remove a display and any desks on it. */
    data class RemoveDisplay(override val token: IBinder, val displayId: Int) : DeskTransition {
        override fun copyWithToken(token: IBinder): DeskTransition = copy(token)
    }
}
