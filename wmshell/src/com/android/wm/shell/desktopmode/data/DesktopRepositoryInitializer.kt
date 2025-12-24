/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.desktopmode.data

import com.android.wm.shell.desktopmode.DesktopUserRepositories
import kotlinx.coroutines.flow.StateFlow

/** Interface for initializing the [DesktopUserRepositories]. */
interface DesktopRepositoryInitializer {
    /** A factory used to re-create a desk from persistence. */
    var deskRecreationFactory: DeskRecreationFactory

    /** A flow that emits true when the repository has been initialized. */
    val isInitialized: StateFlow<Boolean>

    /** Initialize the user repositories from a persistent data store. */
    fun initialize(userRepositories: DesktopUserRepositories)

    /** A factory for re-creating desks. */
    fun interface DeskRecreationFactory {
        /**
         * Re-creates a restored desk and returns the new desk id, or null if re-creation failed.
         */
        suspend fun recreateDesk(userId: Int, destinationDisplayId: Int, deskId: Int): Int?
    }
}
