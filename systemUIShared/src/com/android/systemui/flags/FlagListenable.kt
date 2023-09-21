/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.systemui.flags

/**
 * Plugin for loading flag values
 */
interface FlagListenable {
    /** Add a listener to be alerted when the given flag changes.  */
    fun addListener(flag: Flag<*>, listener: Listener)

    /** Remove a listener to be alerted when any flag changes.  */
    fun removeListener(listener: Listener)

    /** A simple listener to be alerted when a flag changes.  */
    fun interface Listener {
        /** Called when the flag changes */
        fun onFlagChanged(event: FlagEvent)
    }

    /** An event representing the change */
    interface FlagEvent {
        /** the id of the flag which changed */
        val flagName: String
        /** if all listeners alerted invoke this method, the restart will be skipped */
        fun requestNoRestart()
    }
}