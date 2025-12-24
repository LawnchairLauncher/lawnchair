/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.launcher3.util

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PatternMatcher.PATTERN_LITERAL
import androidx.annotation.AnyThread
import java.util.function.Consumer

class SimpleBroadcastReceiver
@JvmOverloads
constructor(
    private val context: Context,
    // Executor on which the registration will be done
    private val executor: LooperExecutor,
    // Executor on with the callback should be executed
    private val callbackExecutor: LooperExecutor = executor,
    private val intentConsumer: Consumer<Intent>,
) : BroadcastReceiver(), SafeCloseable {

    override fun onReceive(context: Context, intent: Intent) {
        intentConsumer.accept(intent)
    }

    /**
     * Wrapper around [Context.registerReceiver]
     *
     * @param completionCallback callback that will be triggered after registration is completed on
     *   the [callbackExecutor], caller usually pass this callback to check if states has changed
     *   while registerReceiver() is executed on a binder call.
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @AnyThread
    @JvmOverloads
    fun register(
        filter: IntentFilter,
        flags: Int = 0,
        permission: String? = null,
        completionCallback: Runnable? = null,
    ) = apply {
        executor.execute {
            context.registerReceiver(this, filter, permission, callbackExecutor.handler, flags)

            if (completionCallback != null) {
                callbackExecutor.execute(completionCallback)
            }
        }
    }

    /** Unregister broadcast receiver */
    override fun close() {
        executor.execute {
            try {
                context.unregisterReceiver(this)
            } catch (e: IllegalArgumentException) {
                // It was probably never registered or already unregistered. Ignore.
            }
        }
    }

    companion object {

        /**
         * Creates an intent filter to listen for [actions] with a specific [pkg] in the data field.
         */
        @JvmStatic
        fun packageFilter(pkg: String?, vararg actions: String) =
            actionsFilter(*actions).apply {
                addDataScheme("package")
                if (!pkg.isNullOrEmpty()) addDataSchemeSpecificPart(pkg, PATTERN_LITERAL)
            }

        /** Creates an intent filter to listen for [actions] */
        @JvmStatic
        fun actionsFilter(vararg actions: String): IntentFilter =
            IntentFilter().apply { actions.forEach { addAction(it) } }
    }
}
