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
package com.android.systemui.util.settings

import android.annotation.SuppressLint
import android.annotation.UserIdInt
import android.annotation.WorkerThread
import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.UserHandle
import android.provider.Settings.SettingNotFoundException
//import com.android.app.tracing.TraceUtils.trace
//import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.util.settings.SettingsProxy.Companion.parseFloat
import com.android.systemui.util.settings.SettingsProxy.Companion.parseFloatOrThrow
import com.android.systemui.util.settings.SettingsProxy.Companion.parseLongOrThrow
import com.android.systemui.util.settings.SettingsProxy.Companion.parseLongOrUseDefault
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Used to interact with per-user Settings.Secure and Settings.System settings (but not
 * Settings.Global, since those do not vary per-user)
 *
 * This interface can be implemented to give instance method (instead of static method) versions of
 * Settings.Secure and Settings.System. It can be injected into class constructors and then faked or
 * mocked as needed in tests.
 *
 * You can ask for [SecureSettings] or [SystemSettings] to be injected as needed.
 *
 * This class also provides [.registerContentObserver] methods, normally found on [ContentResolver]
 * instances, unifying setting related actions in one place.
 */
@SuppressLint("RegisterContentObserverSyncWarning")
public interface UserSettingsProxy : SettingsProxy {
    public val currentUserProvider: SettingsProxy.CurrentUserIdProvider

    /** Returns the user id for the associated [ContentResolver]. */
    public var userId: Int
        get() = getContentResolver().userId
        set(_) {
            throw UnsupportedOperationException(
                "userId cannot be set in interface, use setter from an implementation instead."
            )
        }

    /**
     * Returns the actual current user handle when querying with the current user. Otherwise,
     * returns the passed in user id.
     */
    public fun getRealUserHandle(userHandle: Int): Int {
        return if (userHandle != UserHandle.USER_CURRENT) {
            userHandle
        } else currentUserProvider.getUserId()
    }

    @WorkerThread
    override fun registerContentObserverSync(uri: Uri, settingsObserver: ContentObserver) {
        registerContentObserverForUserSync(uri, settingsObserver, userId)
    }

    override suspend fun registerContentObserver(uri: Uri, settingsObserver: ContentObserver) {
        executeOnSettingsScopeDispatcher("registerContentObserver-A") {
            registerContentObserverForUserSync(uri, settingsObserver, userId)
        }
    }

    override fun registerContentObserverAsync(uri: Uri, settingsObserver: ContentObserver): Job =
        settingsScope.launch() {
            registerContentObserverForUserSync(uri, settingsObserver, userId)
        }

    /** Convenience wrapper around [ContentResolver.registerContentObserver].' */
    @WorkerThread
    override fun registerContentObserverSync(
        uri: Uri,
        notifyForDescendants: Boolean,
        settingsObserver: ContentObserver,
    ) {
        registerContentObserverForUserSync(uri, notifyForDescendants, settingsObserver, userId)
    }

    override suspend fun registerContentObserver(
        uri: Uri,
        notifyForDescendants: Boolean,
        settingsObserver: ContentObserver,
    ) {
        executeOnSettingsScopeDispatcher("registerContentObserver-B") {
            registerContentObserverForUserSync(uri, notifyForDescendants, settingsObserver, userId)
        }
    }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * API corresponding to [registerContentObserverForUser] for Java usage.
     */
    override fun registerContentObserverAsync(
        uri: Uri,
        notifyForDescendants: Boolean,
        settingsObserver: ContentObserver,
    ): Job =
        settingsScope.launch() {
            registerContentObserverForUserSync(uri, notifyForDescendants, settingsObserver, userId)
        }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver]
     *
     * Implicitly calls [getUriFor] on the passed in name.
     */
    @WorkerThread
    public fun registerContentObserverForUserSync(
        name: String,
        settingsObserver: ContentObserver,
        userHandle: Int,
    ) {
        registerContentObserverForUserSync(getUriFor(name), settingsObserver, userHandle)
    }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * suspend API corresponding to [registerContentObserverForUser] to ensure that
     * [ContentObserver] registration happens on a worker thread. Caller may wrap the API in an
     * async block if they wish to synchronize execution.
     */
    public suspend fun registerContentObserverForUser(
        name: String,
        settingsObserver: ContentObserver,
        userHandle: Int,
    ) {
        executeOnSettingsScopeDispatcher("registerContentObserverForUser-A") {
            registerContentObserverForUserSync(name, settingsObserver, userHandle)
        }
    }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * API corresponding to [registerContentObserverForUser] for Java usage.
     */
    public fun registerContentObserverForUserAsync(
        name: String,
        settingsObserver: ContentObserver,
        userHandle: Int,
    ): Job =
        settingsScope.launch() {
            try {
                registerContentObserverForUserSync(getUriFor(name), settingsObserver, userHandle)
            } catch (e: SecurityException) {
                throw SecurityException("registerContentObserverForUserAsync-A, name: $name", e)
            }
        }

    /** Convenience wrapper around [ContentResolver.registerContentObserver] */
    @WorkerThread
    public fun registerContentObserverForUserSync(
        uri: Uri,
        settingsObserver: ContentObserver,
        userHandle: Int,
    ) {
        registerContentObserverForUserSync(uri, false, settingsObserver, userHandle)
    }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * suspend API corresponding to [registerContentObserverForUser] to ensure that
     * [ContentObserver] registration happens on a worker thread. Caller may wrap the API in an
     * async block if they wish to synchronize execution.
     */
    public suspend fun registerContentObserverForUser(
        uri: Uri,
        settingsObserver: ContentObserver,
        userHandle: Int,
    ) {
        executeOnSettingsScopeDispatcher("registerContentObserverForUser-B") {
            registerContentObserverForUserSync(uri, settingsObserver, userHandle)
        }
    }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * API corresponding to [registerContentObserverForUser] for Java usage.
     */
    public fun registerContentObserverForUserAsync(
        uri: Uri,
        settingsObserver: ContentObserver,
        userHandle: Int,
    ): Job =
        settingsScope.launch() {
            try {
                registerContentObserverForUserSync(uri, settingsObserver, userHandle)
            } catch (e: SecurityException) {
                throw SecurityException("registerContentObserverForUserAsync-B, uri: $uri", e)
            }
        }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * API corresponding to [registerContentObserverForUser] for Java usage. After registration is
     * complete, the callback block is called on the <b>background thread</b> to allow for update of
     * value.
     */
    public fun registerContentObserverForUserAsync(
        uri: Uri,
        settingsObserver: ContentObserver,
        userHandle: Int,
        @WorkerThread registered: Runnable,
    ): Job =
        settingsScope.launch() {
            try {
                registerContentObserverForUserSync(uri, settingsObserver, userHandle)
            } catch (e: SecurityException) {
                throw SecurityException("registerContentObserverForUserAsync-C, uri: $uri", e)
            }
            registered.run()
        }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver]
     *
     * Implicitly calls [getUriFor] on the passed in name.
     */
    @WorkerThread
    public fun registerContentObserverForUserSync(
        name: String,
        notifyForDescendants: Boolean,
        settingsObserver: ContentObserver,
        userHandle: Int,
    ) {
        registerContentObserverForUserSync(
            getUriFor(name),
            notifyForDescendants,
            settingsObserver,
            userHandle,
        )
    }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * suspend API corresponding to [registerContentObserverForUser] to ensure that
     * [ContentObserver] registration happens on a worker thread. Caller may wrap the API in an
     * async block if they wish to synchronize execution.
     */
    public suspend fun registerContentObserverForUser(
        name: String,
        notifyForDescendants: Boolean,
        settingsObserver: ContentObserver,
        userHandle: Int,
    ) {
        executeOnSettingsScopeDispatcher("registerContentObserverForUser-C") {
            registerContentObserverForUserSync(
                name,
                notifyForDescendants,
                settingsObserver,
                userHandle,
            )
        }
    }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * API corresponding to [registerContentObserverForUser] for Java usage.
     */
    public fun registerContentObserverForUserAsync(
        name: String,
        notifyForDescendants: Boolean,
        settingsObserver: ContentObserver,
        userHandle: Int,
    ) {
        settingsScope.launch() {
            try {
                registerContentObserverForUserSync(
                    getUriFor(name),
                    notifyForDescendants,
                    settingsObserver,
                    userHandle,
                )
            } catch (e: SecurityException) {
                throw SecurityException("registerContentObserverForUserAsync-D, name: $name", e)
            }
        }
    }

    /** Convenience wrapper around [ContentResolver.registerContentObserver] */
    @WorkerThread
    public fun registerContentObserverForUserSync(
        uri: Uri,
        notifyForDescendants: Boolean,
        settingsObserver: ContentObserver,
        userHandle: Int,
    ) {

            getContentResolver()
                .registerContentObserver(
                    uri,
                    notifyForDescendants,
                    settingsObserver,
                    getRealUserHandle(userHandle),
                )

    }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * suspend API corresponding to [registerContentObserverForUser] to ensure that
     * [ContentObserver] registration happens on a worker thread. Caller may wrap the API in an
     * async block if they wish to synchronize execution.
     */
    public suspend fun registerContentObserverForUser(
        uri: Uri,
        notifyForDescendants: Boolean,
        settingsObserver: ContentObserver,
        userHandle: Int,
    ) {
        executeOnSettingsScopeDispatcher("registerContentObserverForUser-D") {
            registerContentObserverForUserSync(
                uri,
                notifyForDescendants,
                settingsObserver,
                getRealUserHandle(userHandle),
            )
        }
    }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * API corresponding to [registerContentObserverForUser] for Java usage.
     */
    public fun registerContentObserverForUserAsync(
        uri: Uri,
        notifyForDescendants: Boolean,
        settingsObserver: ContentObserver,
        userHandle: Int,
    ): Job =
        settingsScope.launch() {
            try {
                registerContentObserverForUserSync(
                    uri,
                    notifyForDescendants,
                    settingsObserver,
                    userHandle,
                )
            } catch (e: SecurityException) {
                throw SecurityException("registerContentObserverForUserAsync-E, uri: $uri", e)
            }
        }

    /**
     * Look up a name in the database.
     *
     * @param name to look up in the table
     * @return the corresponding value, or null if not present
     */
    override fun getString(name: String): String? {
        return getStringForUser(name, userId)
    }

    /** See [getString]. */
    public fun getStringForUser(name: String, userHandle: Int): String?

    /**
     * Store a name/value pair into the database. Values written by this method will be overridden
     * if a restore happens in the future.
     *
     * @param name to store
     * @param value to associate with the name
     * @return true if the value was set, false on database errors
     */
    public fun putString(name: String, value: String?, overrideableByRestore: Boolean): Boolean

    override fun putString(name: String, value: String?): Boolean {
        return putStringForUser(name, value, userId)
    }

    /** Similar implementation to [putString] for the specified [userHandle]. */
    public fun putStringForUser(name: String, value: String?, userHandle: Int): Boolean

    /** Similar implementation to [putString] for the specified [userHandle]. */
    public fun putStringForUser(
        name: String,
        value: String?,
        tag: String?,
        makeDefault: Boolean,
        @UserIdInt userHandle: Int,
        overrideableByRestore: Boolean,
    ): Boolean

    override fun getInt(name: String, default: Int): Int {
        return getIntForUser(name, default, userId)
    }

    /** Similar implementation to [getInt] for the specified [userHandle]. */
    public fun getIntForUser(name: String, default: Int, userHandle: Int): Int {
        val v = getStringForUser(name, userHandle)
        return try {
            v?.toInt() ?: default
        } catch (e: NumberFormatException) {
            default
        }
    }

    @Throws(SettingNotFoundException::class)
    override fun getInt(name: String): Int = getIntForUser(name, userId)

    /** Similar implementation to [getInt] for the specified [userHandle]. */
    @Throws(SettingNotFoundException::class)
    public fun getIntForUser(name: String, userHandle: Int): Int {
        val v = getStringForUser(name, userHandle) ?: throw SettingNotFoundException(name)
        return try {
            v.toInt()
        } catch (e: NumberFormatException) {
            throw SettingNotFoundException(name)
        }
    }

    override fun putInt(name: String, value: Int): Boolean = putIntForUser(name, value, userId)

    /** Similar implementation to [getInt] for the specified [userHandle]. */
    public fun putIntForUser(name: String, value: Int, userHandle: Int): Boolean =
        putStringForUser(name, value.toString(), userHandle)

    override fun getBool(name: String, def: Boolean): Boolean = getBoolForUser(name, def, userId)

    /** Similar implementation to [getBool] for the specified [userHandle]. */
    public fun getBoolForUser(name: String, def: Boolean, userHandle: Int): Boolean =
        getIntForUser(name, if (def) 1 else 0, userHandle) != 0

    @Throws(SettingNotFoundException::class)
    override fun getBool(name: String): Boolean = getBoolForUser(name, userId)

    /** Similar implementation to [getBool] for the specified [userHandle]. */
    @Throws(SettingNotFoundException::class)
    public fun getBoolForUser(name: String, userHandle: Int): Boolean {
        return getIntForUser(name, userHandle) != 0
    }

    override fun putBool(name: String, value: Boolean): Boolean {
        return putBoolForUser(name, value, userId)
    }

    /** Similar implementation to [putBool] for the specified [userHandle]. */
    public fun putBoolForUser(name: String, value: Boolean, userHandle: Int): Boolean =
        putIntForUser(name, if (value) 1 else 0, userHandle)

    /** Similar implementation to [getLong] for the specified [userHandle]. */
    public fun getLongForUser(name: String, def: Long, userHandle: Int): Long {
        val valString = getStringForUser(name, userHandle)
        return parseLongOrUseDefault(valString, def)
    }

    /** Similar implementation to [getLong] for the specified [userHandle]. */
    @Throws(SettingNotFoundException::class)
    public fun getLongForUser(name: String, userHandle: Int): Long {
        val valString = getStringForUser(name, userHandle)
        return parseLongOrThrow(name, valString)
    }

    /** Similar implementation to [putLong] for the specified [userHandle]. */
    public fun putLongForUser(name: String, value: Long, userHandle: Int): Boolean =
        putStringForUser(name, value.toString(), userHandle)

    /** Similar implementation to [getFloat] for the specified [userHandle]. */
    public fun getFloatForUser(name: String, def: Float, userHandle: Int): Float {
        val v = getStringForUser(name, userHandle)
        return parseFloat(v, def)
    }

    /** Similar implementation to [getFloat] for the specified [userHandle]. */
    @Throws(SettingNotFoundException::class)
    public fun getFloatForUser(name: String, userHandle: Int): Float {
        val v = getStringForUser(name, userHandle)
        return parseFloatOrThrow(name, v)
    }

    /** Similar implementation to [putFloat] for the specified [userHandle]. */
    public fun putFloatForUser(name: String, value: Float, userHandle: Int): Boolean =
        putStringForUser(name, value.toString(), userHandle)
}
