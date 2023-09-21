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

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import androidx.concurrent.futures.CallbackToFutureAdapter
import com.google.common.util.concurrent.ListenableFuture
import java.util.function.Consumer

class FlagManager constructor(
    private val context: Context,
    private val settings: FlagSettingsHelper,
    private val handler: Handler
) : FlagListenable {
    companion object {
        const val RECEIVING_PACKAGE = "com.android.systemui"
        const val ACTION_SET_FLAG = "com.android.systemui.action.SET_FLAG"
        const val ACTION_GET_FLAGS = "com.android.systemui.action.GET_FLAGS"
        const val FLAGS_PERMISSION = "com.android.systemui.permission.FLAGS"
        const val ACTION_SYSUI_STARTED = "com.android.systemui.STARTED"
        const val EXTRA_NAME = "name"
        const val EXTRA_VALUE = "value"
        const val EXTRA_FLAGS = "flags"
        private const val SETTINGS_PREFIX = "systemui/flags"
    }

    constructor(context: Context, handler: Handler) : this(
        context,
        FlagSettingsHelper(context.contentResolver),
        handler
    )

    /**
     * An action called on restart which takes as an argument whether the listeners requested
     * that the restart be suppressed
     */
    var onSettingsChangedAction: Consumer<Boolean>? = null
    var clearCacheAction: Consumer<String>? = null
    private val listeners: MutableSet<PerFlagListener> = mutableSetOf()
    private val settingsObserver: ContentObserver = SettingsObserver()

    fun getFlagsFuture(): ListenableFuture<Collection<Flag<*>>> {
        val intent = Intent(ACTION_GET_FLAGS)
        intent.setPackage(RECEIVING_PACKAGE)

        return CallbackToFutureAdapter.getFuture {
                completer: CallbackToFutureAdapter.Completer<Collection<Flag<*>>> ->
            context.sendOrderedBroadcast(
                intent,
                null,
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        val extras: Bundle? = getResultExtras(false)
                        val listOfFlags: java.util.ArrayList<ParcelableFlag<*>>? =
                            extras?.getParcelableArrayList(
                                EXTRA_FLAGS, ParcelableFlag::class.java
                            )
                        if (listOfFlags != null) {
                            completer.set(listOfFlags)
                        } else {
                            completer.setException(NoFlagResultsException())
                        }
                    }
                },
                null,
                Activity.RESULT_OK,
                "extra data",
                null
            )
            "QueryingFlags"
        }
    }

    /**
     * Returns the stored value or null if not set.
     * This API is used by TheFlippinApp.
     */
    fun isEnabled(name: String): Boolean? = readFlagValue(name, BooleanFlagSerializer)

    /**
     * Sets the value of a boolean flag.
     * This API is used by TheFlippinApp.
     */
    fun setFlagValue(name: String, enabled: Boolean) {
        val intent = createIntent(name)
        intent.putExtra(EXTRA_VALUE, enabled)

        context.sendBroadcast(intent)
    }

    fun eraseFlag(name: String) {
        val intent = createIntent(name)

        context.sendBroadcast(intent)
    }

    /** Returns the stored value or null if not set.  */
    // TODO(b/265188950): Remove method this once ids are fully deprecated.
    fun <T> readFlagValue(id: Int, serializer: FlagSerializer<T>): T? {
        val data = settings.getStringFromSecure(idToSettingsKey(id))
        return serializer.fromSettingsData(data)
    }

    /** Returns the stored value or null if not set.  */
    fun <T> readFlagValue(name: String, serializer: FlagSerializer<T>): T? {
        val data = settings.getString(nameToSettingsKey(name))
        return serializer.fromSettingsData(data)
    }

    override fun addListener(flag: Flag<*>, listener: FlagListenable.Listener) {
        synchronized(listeners) {
            val registerNeeded = listeners.isEmpty()
            listeners.add(PerFlagListener(flag.name, listener))
            if (registerNeeded) {
                settings.registerContentObserver(SETTINGS_PREFIX, true, settingsObserver)
            }
        }
    }

    override fun removeListener(listener: FlagListenable.Listener) {
        synchronized(listeners) {
            if (listeners.isEmpty()) {
                return
            }
            listeners.removeIf { it.listener == listener }
            if (listeners.isEmpty()) {
                settings.unregisterContentObserver(settingsObserver)
            }
        }
    }

    private fun createIntent(name: String): Intent {
        val intent = Intent(ACTION_SET_FLAG)
        intent.setPackage(RECEIVING_PACKAGE)
        intent.putExtra(EXTRA_NAME, name)

        return intent
    }

    // TODO(b/265188950): Remove method this once ids are fully deprecated.
    fun idToSettingsKey(id: Int): String {
        return "$SETTINGS_PREFIX/$id"
    }

    fun nameToSettingsKey(name: String): String {
        return "$SETTINGS_PREFIX/$name"
    }

    inner class SettingsObserver : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (uri == null) {
                return
            }
            val parts = uri.pathSegments
            val name = parts[parts.size - 1]
            clearCacheAction?.accept(name)
            dispatchListenersAndMaybeRestart(name, onSettingsChangedAction)
        }
    }

    fun dispatchListenersAndMaybeRestart(name: String, restartAction: Consumer<Boolean>?) {
        val filteredListeners: List<FlagListenable.Listener> = synchronized(listeners) {
            listeners.mapNotNull { if (it.name == name) it.listener else null }
        }
        // If there are no listeners, there's nothing to dispatch to, and nothing to suppress it.
        if (filteredListeners.isEmpty()) {
            restartAction?.accept(false)
            return
        }
        // Dispatch to every listener and save whether each one called requestNoRestart.
        val suppressRestartList: List<Boolean> = filteredListeners.map { listener ->
            var didRequestNoRestart = false
            val event = object : FlagListenable.FlagEvent {
                override val flagName = name
                override fun requestNoRestart() {
                    didRequestNoRestart = true
                }
            }
            listener.onFlagChanged(event)
            didRequestNoRestart
        }
        // Suppress restart only if ALL listeners request it.
        val suppressRestart = suppressRestartList.all { it }
        restartAction?.accept(suppressRestart)
    }

    private data class PerFlagListener(val name: String, val listener: FlagListenable.Listener)
}

class NoFlagResultsException : Exception(
    "SystemUI failed to communicate its flags back successfully"
)
