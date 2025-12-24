/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.annotation.UserIdInt
import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
//import com.android.app.tracing.TraceUtils.trace
//import com.android.app.tracing.coroutines.launchTraced as launch
//import com.android.app.tracing.coroutines.withContextTraced as withContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Used to interact with mainly with Settings.Global, but can also be used for Settings.System and
 * Settings.Secure. To use the per-user System and Secure settings, [UserSettingsProxy] must be used
 * instead.
 *
 * This interface can be implemented to give instance method (instead of static method) versions of
 * Settings.Global. It can be injected into class constructors and then faked or mocked as needed in
 * tests.
 *
 * You can ask for [GlobalSettings] to be injected as needed.
 *
 * This class also provides [.registerContentObserver] methods, normally found on [ContentResolver]
 * instances, unifying setting related actions in one place.
 */
public interface SettingsProxy {
    /** Returns the [ContentResolver] this instance was constructed with. */
    public fun getContentResolver(): ContentResolver

    /** Returns the [CoroutineScope] that the async APIs will use. */
    public val settingsScope: CoroutineScope

    @OptIn(ExperimentalStdlibApi::class)
    public suspend fun executeOnSettingsScopeDispatcher(name: String, block: () -> Unit) {
        val settingsDispatcher = settingsScope.coroutineContext[CoroutineDispatcher]
        if (
            settingsDispatcher != null &&
                settingsDispatcher != coroutineContext[CoroutineDispatcher]
        ) {
//            withContext(name, settingsDispatcher) { block() }
        } else {
//            trace(name) { block() }
        }
    }

    /**
     * Construct the content URI for a particular name/value pair, useful for monitoring changes
     * with a ContentObserver.
     *
     * @param name to look up in the table
     * @return the corresponding content URI, or null if not present
     */
    @AnyThread public fun getUriFor(name: String): Uri

    /**
     * Registers listener for a given content observer <b>while blocking the current thread</b>.
     * Implicitly calls [getUriFor] on the passed in name.
     *
     * This should not be called from the main thread, use [registerContentObserver] or
     * [registerContentObserverAsync] instead.
     */
    @WorkerThread
    public fun registerContentObserverSync(name: String, settingsObserver: ContentObserver) {
        registerContentObserverSync(getUriFor(name), settingsObserver)
    }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * suspend API corresponding to [registerContentObserver] to ensure that [ContentObserver]
     * registration happens on a worker thread. Caller may wrap the API in an async block if they
     * wish to synchronize execution.
     */
    public suspend fun registerContentObserver(name: String, settingsObserver: ContentObserver) {
        executeOnSettingsScopeDispatcher("registerContentObserver-A") {
            registerContentObserverSync(getUriFor(name), settingsObserver)
        }
    }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * API corresponding to [registerContentObserver] for Java usage.
     */
    @AnyThread
    public fun registerContentObserverAsync(name: String, settingsObserver: ContentObserver): Job =
        settingsScope.launch() {
            registerContentObserverSync(getUriFor(name), settingsObserver)
        }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * API corresponding to [registerContentObserver] for Java usage. After registration is
     * complete, the callback block is called on the <b>background thread</b> to allow for update of
     * value.
     */
    @AnyThread
    public fun registerContentObserverAsync(
        name: String,
        settingsObserver: ContentObserver,
        @WorkerThread registered: Runnable,
    ): Job =
        settingsScope.launch() {
            registerContentObserverSync(getUriFor(name), settingsObserver)
            registered.run()
        }

    /**
     * Registers listener for a given content observer <b>while blocking the current thread</b>.
     *
     * This should not be called from the main thread, use [registerContentObserver] or
     * [registerContentObserverAsync] instead.
     */
    @WorkerThread
    public fun registerContentObserverSync(uri: Uri, settingsObserver: ContentObserver) {
        registerContentObserverSync(uri, false, settingsObserver)
    }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * suspend API corresponding to [registerContentObserver] to ensure that [ContentObserver]
     * registration happens on a worker thread. Caller may wrap the API in an async block if they
     * wish to synchronize execution.
     */
    public suspend fun registerContentObserver(uri: Uri, settingsObserver: ContentObserver) {
        executeOnSettingsScopeDispatcher("registerContentObserver-B") {
            registerContentObserverSync(uri, settingsObserver)
        }
    }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * API corresponding to [registerContentObserver] for Java usage.
     */
    @AnyThread
    public fun registerContentObserverAsync(uri: Uri, settingsObserver: ContentObserver): Job =
        settingsScope.launch() {
            registerContentObserverSync(uri, settingsObserver)
        }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * API corresponding to [registerContentObserver] for Java usage. After registration is
     * complete, the callback block is called on the <b>background thread</b> to allow for update of
     * value.
     */
    @AnyThread
    public fun registerContentObserverAsync(
        uri: Uri,
        settingsObserver: ContentObserver,
        @WorkerThread registered: Runnable,
    ): Job =
        settingsScope.launch() {
            registerContentObserverSync(uri, settingsObserver)
            registered.run()
        }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * Implicitly calls [getUriFor] on the passed in name.
     */
    @WorkerThread
    public fun registerContentObserverSync(
        name: String,
        notifyForDescendants: Boolean,
        settingsObserver: ContentObserver,
    ) {
        registerContentObserverSync(getUriFor(name), notifyForDescendants, settingsObserver)
    }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * suspend API corresponding to [registerContentObserver] to ensure that [ContentObserver]
     * registration happens on a worker thread. Caller may wrap the API in an async block if they
     * wish to synchronize execution.
     */
    public suspend fun registerContentObserver(
        name: String,
        notifyForDescendants: Boolean,
        settingsObserver: ContentObserver,
    ) {
        executeOnSettingsScopeDispatcher("registerContentObserver-C") {
            registerContentObserverSync(getUriFor(name), notifyForDescendants, settingsObserver)
        }
    }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * API corresponding to [registerContentObserver] for Java usage.
     */
    @AnyThread
    public fun registerContentObserverAsync(
        name: String,
        notifyForDescendants: Boolean,
        settingsObserver: ContentObserver,
    ): Job =
        settingsScope.launch() {
            registerContentObserverSync(getUriFor(name), notifyForDescendants, settingsObserver)
        }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * API corresponding to [registerContentObserver] for Java usage. After registration is
     * complete, the callback block is called on the <b>background thread</b> to allow for update of
     * value.
     */
    @AnyThread
    public fun registerContentObserverAsync(
        name: String,
        notifyForDescendants: Boolean,
        settingsObserver: ContentObserver,
        @WorkerThread registered: Runnable,
    ): Job =
        settingsScope.launch() {
            registerContentObserverSync(getUriFor(name), notifyForDescendants, settingsObserver)
            registered.run()
        }

    /**
     * Registers listener for a given content observer <b>while blocking the current thread</b>.
     *
     * This should not be called from the main thread, use [registerContentObserver] or
     * [registerContentObserverAsync] instead.
     */
    @WorkerThread
    public fun registerContentObserverSync(
        uri: Uri,
        notifyForDescendants: Boolean,
        settingsObserver: ContentObserver,
    ) {

            getContentResolver()
                .registerContentObserver(uri, notifyForDescendants, settingsObserver)

    }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * suspend API corresponding to [registerContentObserver] to ensure that [ContentObserver]
     * registration happens on a worker thread. Caller may wrap the API in an async block if they
     * wish to synchronize execution.
     */
    public suspend fun registerContentObserver(
        uri: Uri,
        notifyForDescendants: Boolean,
        settingsObserver: ContentObserver,
    ) {
        executeOnSettingsScopeDispatcher("registerContentObserver-D") {
            registerContentObserverSync(uri, notifyForDescendants, settingsObserver)
        }
    }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * API corresponding to [registerContentObserver] for Java usage.
     */
    @AnyThread
    public fun registerContentObserverAsync(
        uri: Uri,
        notifyForDescendants: Boolean,
        settingsObserver: ContentObserver,
    ): Job =
        settingsScope.launch() {
            registerContentObserverSync(uri, notifyForDescendants, settingsObserver)
        }

    /**
     * Convenience wrapper around [ContentResolver.registerContentObserver].'
     *
     * API corresponding to [registerContentObserver] for Java usage. After registration is
     * complete, the callback block is called on the <b>background thread</b> to allow for update of
     * value.
     */
    @AnyThread
    public fun registerContentObserverAsync(
        uri: Uri,
        notifyForDescendants: Boolean,
        settingsObserver: ContentObserver,
        @WorkerThread registered: Runnable,
    ): Job =
        settingsScope.launch() {
            registerContentObserverSync(uri, notifyForDescendants, settingsObserver)
            registered.run()
        }

    /**
     * Unregisters the given content observer <b>while blocking the current thread</b>.
     *
     * This should not be called from the main thread, use [unregisterContentObserver] or
     * [unregisterContentObserverAsync] instead.
     */
    @WorkerThread
    public fun unregisterContentObserverSync(settingsObserver: ContentObserver) {

            getContentResolver().unregisterContentObserver(settingsObserver)

    }

    /**
     * Convenience wrapper around [ContentResolver.unregisterContentObserver].'
     *
     * API corresponding to [unregisterContentObserver] for Java usage to ensure that
     * [ContentObserver] un-registration happens on a worker thread. Caller may wrap the API in an
     * async block if they wish to synchronize execution.
     */
    public suspend fun unregisterContentObserver(settingsObserver: ContentObserver) {
        executeOnSettingsScopeDispatcher("unregisterContentObserver") {
            unregisterContentObserverSync(settingsObserver)
        }
    }

    /**
     * Convenience wrapper around [ContentResolver.unregisterContentObserver].'
     *
     * API corresponding to [unregisterContentObserver] for Java usage to ensure that
     * [ContentObserver] registration happens on a worker thread.
     */
    @AnyThread
    public fun unregisterContentObserverAsync(settingsObserver: ContentObserver): Job =
        settingsScope.launch() {
            unregisterContentObserver(settingsObserver)
        }

    /**
     * Look up a name in the database.
     *
     * @param name to look up in the table
     * @return the corresponding value, or null if not present
     */
    public fun getString(name: String): String?

    /**
     * Store a name/value pair into the database.
     *
     * @param name to store
     * @param value to associate with the name
     * @return true if the value was set, false on database errors
     */
    public fun putString(name: String, value: String?): Boolean

    /**
     * Store a name/value pair into the database.
     *
     * The method takes an optional tag to associate with the setting which can be used to clear
     * only settings made by your package and associated with this tag by passing the tag to
     * [ ][.resetToDefaults]. Anyone can override the current tag. Also if another package changes
     * the setting then the tag will be set to the one specified in the set call which can be null.
     * Also any of the settings setters that do not take a tag as an argument effectively clears the
     * tag.
     *
     * For example, if you set settings A and B with tags T1 and T2 and another app changes setting
     * A (potentially to the same value), it can assign to it a tag T3 (note that now the package
     * that changed the setting is not yours). Now if you reset your changes for T1 and T2 only
     * setting B will be reset and A not (as it was changed by another package) but since A did not
     * change you are in the desired initial state. Now if the other app changes the value of A
     * (assuming you registered an observer in the beginning) you would detect that the setting was
     * changed by another app and handle this appropriately (ignore, set back to some value, etc).
     *
     * Also the method takes an argument whether to make the value the default for this setting. If
     * the system already specified a default value, then the one passed in here will **not** be set
     * as the default.
     *
     * @param name to store.
     * @param value to associate with the name.
     * @param tag to associate with the setting.
     * @param makeDefault whether to make the value the default one.
     * @return true if the value was set, false on database errors.
     * @see .resetToDefaults
     */
    public fun putString(name: String, value: String?, tag: String?, makeDefault: Boolean): Boolean

    /**
     * Convenience function for retrieving a single secure settings value as an integer. Note that
     * internally setting values are always stored as strings; this function converts the string to
     * an integer for you. The default value will be returned if the setting is not defined or not
     * an integer.
     *
     * @param name The name of the setting to retrieve.
     * @param default Value to return if the setting is not defined.
     * @return The setting's current value, or default if it is not defined or not a valid integer.
     */
    public fun getInt(name: String, default: Int): Int {
        val v = getString(name)
        return try {
            v?.toInt() ?: default
        } catch (e: NumberFormatException) {
            default
        }
    }

    /**
     * Convenience function for retrieving a single secure settings value as an integer. Note that
     * internally setting values are always stored as strings; this function converts the string to
     * an integer for you.
     *
     * This version does not take a default value. If the setting has not been set, or the string
     * value is not a number, it throws [Settings.SettingNotFoundException].
     *
     * @param name The name of the setting to retrieve.
     * @return The setting's current value.
     * @throws Settings.SettingNotFoundException Thrown if a setting by the given name can't be
     *   found or the setting value is not an integer.
     */
    @Throws(SettingNotFoundException::class)
    public fun getInt(name: String): Int {
        val v = getString(name) ?: throw SettingNotFoundException(name)
        return try {
            v.toInt()
        } catch (e: NumberFormatException) {
            throw SettingNotFoundException(name)
        }
    }

    /**
     * Convenience function for updating a single settings value as an integer. This will either
     * create a new entry in the table if the given name does not exist, or modify the value of the
     * existing row with that name. Note that internally setting values are always stored as
     * strings, so this function converts the given value to a string before storing it.
     *
     * @param name The name of the setting to modify.
     * @param value The new value for the setting.
     * @return true if the value was set, false on database errors
     */
    public fun putInt(name: String, value: Int): Boolean {
        return putString(name, value.toString())
    }

    /**
     * Convenience function for retrieving a single secure settings value as a boolean. Note that
     * internally setting values are always stored as strings; this function converts the string to
     * a boolean for you. The default value will be returned if the setting is not defined or not a
     * boolean.
     *
     * @param name The name of the setting to retrieve.
     * @param default Value to return if the setting is not defined.
     * @return The setting's current value, or default if it is not defined or not a valid boolean.
     */
    public fun getBool(name: String, default: Boolean): Boolean {
        return getInt(name, if (default) 1 else 0) != 0
    }

    /**
     * Convenience function for retrieving a single secure settings value as a boolean. Note that
     * internally setting values are always stored as strings; this function converts the string to
     * a boolean for you.
     *
     * This version does not take a default value. If the setting has not been set, or the string
     * value is not a number, it throws [Settings.SettingNotFoundException].
     *
     * @param name The name of the setting to retrieve.
     * @return The setting's current value.
     * @throws Settings.SettingNotFoundException Thrown if a setting by the given name can't be
     *   found or the setting value is not a boolean.
     */
    @Throws(SettingNotFoundException::class)
    public fun getBool(name: String): Boolean {
        return getInt(name) != 0
    }

    /**
     * Convenience function for updating a single settings value as a boolean. This will either
     * create a new entry in the table if the given name does not exist, or modify the value of the
     * existing row with that name. Note that internally setting values are always stored as
     * strings, so this function converts the given value to a string before storing it.
     *
     * @param name The name of the setting to modify.
     * @param value The new value for the setting.
     * @return true if the value was set, false on database errors
     */
    public fun putBool(name: String, value: Boolean): Boolean {
        return putInt(name, if (value) 1 else 0)
    }

    /**
     * Convenience function for retrieving a single secure settings value as a `long`. Note that
     * internally setting values are always stored as strings; this function converts the string to
     * a `long` for you. The default value will be returned if the setting is not defined or not a
     * `long`.
     *
     * @param name The name of the setting to retrieve.
     * @param def Value to return if the setting is not defined.
     * @return The setting's current value, or 'def' if it is not defined or not a valid `long`.
     */
    public fun getLong(name: String, def: Long): Long {
        val valString = getString(name)
        return parseLongOrUseDefault(valString, def)
    }

    /**
     * Convenience function for retrieving a single secure settings value as a `long`. Note that
     * internally setting values are always stored as strings; this function converts the string to
     * a `long` for you.
     *
     * This version does not take a default value. If the setting has not been set, or the string
     * value is not a number, it throws [Settings.SettingNotFoundException].
     *
     * @param name The name of the setting to retrieve.
     * @return The setting's current value.
     * @throws Settings.SettingNotFoundException Thrown if a setting by the given name can't be
     *   found or the setting value is not an integer.
     */
    @Throws(SettingNotFoundException::class)
    public fun getLong(name: String): Long {
        val valString = getString(name)
        return parseLongOrThrow(name, valString)
    }

    /**
     * Convenience function for updating a secure settings value as a long integer. This will either
     * create a new entry in the table if the given name does not exist, or modify the value of the
     * existing row with that name. Note that internally setting values are always stored as
     * strings, so this function converts the given value to a string before storing it.
     *
     * @param name The name of the setting to modify.
     * @param value The new value for the setting.
     * @return true if the value was set, false on database errors
     */
    public fun putLong(name: String, value: Long): Boolean {
        return putString(name, value.toString())
    }

    /**
     * Convenience function for retrieving a single secure settings value as a floating point
     * number. Note that internally setting values are always stored as strings; this function
     * converts the string to an float for you. The default value will be returned if the setting is
     * not defined or not a valid float.
     *
     * @param name The name of the setting to retrieve.
     * @param def Value to return if the setting is not defined.
     * @return The setting's current value, or 'def' if it is not defined or not a valid float.
     */
    public fun getFloat(name: String, def: Float): Float {
        val v = getString(name)
        return parseFloat(v, def)
    }

    /**
     * Convenience function for retrieving a single secure settings value as a float. Note that
     * internally setting values are always stored as strings; this function converts the string to
     * a float for you.
     *
     * This version does not take a default value. If the setting has not been set, or the string
     * value is not a number, it throws [Settings.SettingNotFoundException].
     *
     * @param name The name of the setting to retrieve.
     * @return The setting's current value.
     * @throws Settings.SettingNotFoundException Thrown if a setting by the given name can't be
     *   found or the setting value is not a float.
     */
    @Throws(SettingNotFoundException::class)
    public fun getFloat(name: String): Float {
        val v = getString(name)
        return parseFloatOrThrow(name, v)
    }

    /**
     * Convenience function for updating a single settings value as a floating point number. This
     * will either create a new entry in the table if the given name does not exist, or modify the
     * value of the existing row with that name. Note that internally setting values are always
     * stored as strings, so this function converts the given value to a string before storing it.
     *
     * @param name The name of the setting to modify.
     * @param value The new value for the setting.
     * @return true if the value was set, false on database errors
     */
    public fun putFloat(name: String, value: Float): Boolean {
        return putString(name, value.toString())
    }

    public companion object {
        /** Convert a string to a long, or uses a default if the string is malformed or null */
        @JvmStatic
        public fun parseLongOrUseDefault(valString: String?, default: Long): Long {
            val value: Long =
                try {
                    valString?.toLong() ?: default
                } catch (e: NumberFormatException) {
                    default
                }
            return value
        }

        /** Convert a string to a long, or throws an exception if the string is malformed or null */
        @JvmStatic
        @Throws(SettingNotFoundException::class)
        public fun parseLongOrThrow(name: String, valString: String?): Long {
            if (valString == null) {
                throw SettingNotFoundException(name)
            }
            return try {
                valString.toLong()
            } catch (e: NumberFormatException) {
                throw SettingNotFoundException(name)
            }
        }

        /** Convert a string to a float, or uses a default if the string is malformed or null */
        @JvmStatic
        public fun parseFloat(v: String?, def: Float): Float {
            return try {
                v?.toFloat() ?: def
            } catch (e: NumberFormatException) {
                def
            }
        }

        /**
         * Convert a string to a float, or throws an exception if the string is malformed or null
         */
        @JvmStatic
        @Throws(SettingNotFoundException::class)
        public fun parseFloatOrThrow(name: String, v: String?): Float {
            if (v == null) {
                throw SettingNotFoundException(name)
            }
            return try {
                v.toFloat()
            } catch (e: NumberFormatException) {
                throw SettingNotFoundException(name)
            }
        }
    }

    public fun interface CurrentUserIdProvider {
        @UserIdInt public fun getUserId(): Int
    }
}
