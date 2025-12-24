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

package com.android.launcher3.taskbar.bubbles

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.quickstep.SystemUiProxy
import com.android.wm.shell.shared.bubbles.BubbleBarLocation
import com.android.wm.shell.shared.bubbles.logging.EntryPoint
import javax.inject.Inject

/** Utility class to request WMShell to launch an app into a bubble and open that bubble. */
@LauncherAppSingleton
class BubbleActivityStarter @Inject constructor(private val systemUiProxy: SystemUiProxy) {

    private val listeners = mutableListOf<Listener>()

    /** Tell SysUI to show the provided shortcut in a bubble. */
    @JvmOverloads
    fun showShortcutBubble(
        info: ShortcutInfo?,
        entryPoint: EntryPoint,
        bubbleBarLocation: BubbleBarLocation? = null,
    ) {
        systemUiProxy.showShortcutBubble(info, entryPoint, bubbleBarLocation)
        notifyListeners()
    }

    /** Tells SysUI to show a bubble of an app. */
    @JvmOverloads
    fun showAppBubble(
        intent: Intent?,
        user: UserHandle,
        entryPoint: EntryPoint,
        bubbleBarLocation: BubbleBarLocation? = null,
    ) {
        systemUiProxy.showAppBubble(intent, user, entryPoint, bubbleBarLocation)
        notifyListeners()
    }

    /** Adds [Listener]. Returns `true` if listener was added. */
    fun addListener(listener: Listener): Boolean {
        return listeners.add(listener)
    }

    /** Removes [Listener]. Returns `true` if listener was removed. */
    fun removeListener(listener: Listener): Boolean {
        return listeners.remove(listener)
    }

    private fun notifyListeners() {
        for (listener in listeners) {
            listener.onBubbleLaunchRequested()
        }
    }

    /** Interface for listening of bubble lunch requests. */
    fun interface Listener {

        /** Invoked when bubble launch is requested. */
        fun onBubbleLaunchRequested()
    }

    companion object {

        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getBubbleActivityStarter)
    }
}
