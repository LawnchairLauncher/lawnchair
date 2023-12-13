/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.animation.back

import android.annotation.IntRange
import android.util.DisplayMetrics
import android.view.View
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher
import android.window.OnBackInvokedDispatcher.Priority

/**
 * Generates an [OnBackAnimationCallback] given a [backAnimationSpec]. [onBackProgressed] will be
 * called on each update passing the current [BackTransformation].
 *
 * Optionally, you can specify [onBackStarted], [onBackInvoked], and [onBackCancelled] callbacks.
 *
 * @sample com.android.systemui.util.registerAnimationOnBackInvoked
 */
fun onBackAnimationCallbackFrom(
    backAnimationSpec: BackAnimationSpec,
    displayMetrics: DisplayMetrics, // TODO(b/265060720): We could remove this
    onBackProgressed: (BackTransformation) -> Unit,
    onBackStarted: (BackEvent) -> Unit = {},
    onBackInvoked: () -> Unit = {},
    onBackCancelled: () -> Unit = {},
): OnBackAnimationCallback {
    return object : OnBackAnimationCallback {
        private var initialY = 0f
        private val lastTransformation = BackTransformation()

        override fun onBackStarted(backEvent: BackEvent) {
            initialY = backEvent.touchY
            onBackStarted(backEvent)
        }

        override fun onBackProgressed(backEvent: BackEvent) {
            val progressY = (backEvent.touchY - initialY) / displayMetrics.heightPixels

            backAnimationSpec.getBackTransformation(
                backEvent = backEvent,
                progressY = progressY,
                result = lastTransformation,
            )

            onBackProgressed(lastTransformation)
        }

        override fun onBackInvoked() {
            onBackInvoked()
        }

        override fun onBackCancelled() {
            onBackCancelled()
        }
    }
}

/**
 * Register [OnBackAnimationCallback] when View is attached and unregister it when View is detached
 *
 * @sample com.android.systemui.util.registerAnimationOnBackInvoked
 */
fun View.registerOnBackInvokedCallbackOnViewAttached(
    onBackInvokedDispatcher: OnBackInvokedDispatcher,
    onBackAnimationCallback: OnBackAnimationCallback,
    @Priority @IntRange(from = 0) priority: Int = OnBackInvokedDispatcher.PRIORITY_DEFAULT,
) {
    addOnAttachStateChangeListener(
        object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    priority,
                    onBackAnimationCallback
                )
            }

            override fun onViewDetachedFromWindow(v: View) {
                removeOnAttachStateChangeListener(this)
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(onBackAnimationCallback)
            }
        }
    )

    if (isAttachedToWindow) {
        onBackInvokedDispatcher.registerOnBackInvokedCallback(priority, onBackAnimationCallback)
    }
}
