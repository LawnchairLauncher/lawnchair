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

package com.android.wm.shell.common

/**
 * Simple implementation of [ShellExecutor] that collects all runnables and executes them
 * sequentially once [flushAll] is called
 */
class TestShellExecutor : ShellExecutor {

    private val runnables: MutableList<Runnable> = mutableListOf()

    override fun execute(runnable: Runnable) {
        runnables.add(runnable)
    }

    override fun executeDelayed(runnable: Runnable, delayMillis: Long) {
        execute(runnable)
    }

    override fun removeCallbacks(runnable: Runnable?) {}

    override fun hasCallback(runnable: Runnable?): Boolean = false

    /**
     * Execute all posted runnables sequentially
     */
    fun flushAll() {
        while (runnables.isNotEmpty()) {
            runnables.removeAt(0).run()
        }
    }
}
