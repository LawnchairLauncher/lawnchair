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

package com.android.wm.shell.compatui.impl

import android.os.HandlerThread
import java.util.concurrent.CountDownLatch
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Utility {@link TestRule} to manage Handlers in Compat UI tests.
 */
class CompatUIHandlerRule : TestRule {

    private lateinit var handler: HandlerThread

    /**
     * Makes the HandlerThread available during the test
     */
    override fun apply(base: Statement?, description: Description?): Statement {
        handler = HandlerThread("CompatUIHandler").apply {
            start()
        }
        return object : Statement() {
            @Throws(Throwable::class)
            override fun evaluate() {
                try {
                    base!!.evaluate()
                } finally {
                    handler.quitSafely()
                }
            }
        }
    }

    /**
     * Posts a {@link Runnable} for the Handler
     * @param runnable The Runnable to execute
     */
    fun postBlocking(runnable: Runnable) {
        val countDown = CountDownLatch(/* count = */ 1)
        handler.threadHandler.post{
            runnable.run()
            countDown.countDown()
        }
        try {
            countDown.await()
        } catch (e: InterruptedException) {
            // No-op
        }
    }
}
