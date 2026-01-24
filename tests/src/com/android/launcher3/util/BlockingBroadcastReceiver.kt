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

package com.android.launcher3.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Parcelable
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS

private const val DEFAULT_BROADCAST_TIMEOUT_SECS: Long = 10

/** Broadcast receiver which blocks until the result is received. */
open class BlockingBroadcastReceiver(action: String) : BroadcastReceiver() {

    val value = CompletableFuture<Intent>()

    init {
        getInstrumentation()
            .targetContext
            .registerReceiver(this, IntentFilter(action), Context.RECEIVER_EXPORTED)
    }

    override fun onReceive(context: Context, intent: Intent) {
        value.complete(intent)
    }

    @Throws(InterruptedException::class)
    fun blockingGetIntent(): Intent =
        value.get(DEFAULT_BROADCAST_TIMEOUT_SECS, SECONDS).also {
            getInstrumentation().targetContext.unregisterReceiver(this)
        }

    @Throws(InterruptedException::class)
    fun blockingGetExtraIntent(): Intent? =
        blockingGetIntent().getParcelableExtra<Parcelable>(Intent.EXTRA_INTENT) as Intent?
}
