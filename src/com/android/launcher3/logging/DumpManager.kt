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

package com.android.launcher3.logging

import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.SafeCloseable
import java.io.PrintWriter
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject

/** Utility class for managing log dump for various objects */
@LauncherAppSingleton
class DumpManager @Inject constructor() {

    private val dumpables = CopyOnWriteArrayList<LauncherDumpable>()

    /** Registers a [LauncherDumpable], and returns a [SafeCloseable] to unregister it */
    fun register(target: LauncherDumpable): SafeCloseable {
        dumpables.add(target)
        return SafeCloseable { dumpables.remove(target) }
    }

    /** Dumps all the registered dumpables */
    fun dump(prefix: String, writer: PrintWriter, args: Array<String>?) {
        dumpables.forEach { it.dump(prefix, writer, args) }
    }

    /**
     * Interface to indicate a dumpable object. Prefixed with Launcher to avoid conflict with
     * platform class
     */
    fun interface LauncherDumpable {
        fun dump(prefix: String, writer: PrintWriter, args: Array<String>?)
    }
}
