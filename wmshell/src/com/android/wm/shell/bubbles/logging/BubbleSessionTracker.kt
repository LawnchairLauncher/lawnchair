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

package com.android.wm.shell.bubbles.logging

import com.android.wm.shell.bubbles.Bubble
import com.android.wm.shell.bubbles.BubbleOverflow
import com.android.wm.shell.bubbles.BubbleViewProvider

/**
 * Keeps track of the current Bubble session.
 *
 * Bubble sessions start when the stack expands and end when the stack collapses.
 */
fun interface BubbleSessionTracker {

    /** Starts tracking a new bubble bar session. */
    fun log(event: SessionEvent)

    /** Session events that are tracked. */
    sealed class SessionEvent {
        data class Started(
            val forBubbleBar: Boolean,
            val selectedBubblePackage: String,
        ) : SessionEvent() {

            companion object {

                @JvmStatic
                fun forBubbleBar(selectedBubblePackage: String) =
                    Started(forBubbleBar = true, selectedBubblePackage)

                @JvmStatic
                fun forFloatingBubble(selectedBubblePackage: String) =
                    Started(forBubbleBar = false, selectedBubblePackage)
            }
        }

        data class Ended(val forBubbleBar: Boolean) : SessionEvent() {

            companion object {

                @JvmStatic
                fun forBubbleBar() = Ended(forBubbleBar = true)

                @JvmStatic
                fun forFloatingBubble() = Ended(forBubbleBar = false)
            }
        }

        data class SwitchedBubble(
            val forBubbleBar: Boolean,
            val toBubblePackage: String,
        ) : SessionEvent() {

            companion object {

                @JvmStatic
                fun forBubbleBar(toBubblePackage: String) =
                    SwitchedBubble(forBubbleBar = true, toBubblePackage)

                @JvmStatic
                fun forFloatingBubble(toBubblePackage: String) =
                    SwitchedBubble(forBubbleBar = false, toBubblePackage)
            }
        }
    }

    companion object {
        @JvmStatic
        fun BubbleViewProvider.getBubblePackageForLogging() = when (this) {
            is Bubble -> packageName
            is BubbleOverflow -> "Overflow"
            else -> throw IllegalArgumentException("Unsupported type of BubbleViewProvider")
        }
    }
}
