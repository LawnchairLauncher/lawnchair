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
package com.android.launcher3

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.annotation.VisibleForTesting
import com.android.launcher3.BuildConfig.WIDGET_ON_FIRST_SCREEN
import com.android.launcher3.LauncherFiles.DEVICE_PREFERENCES_KEY
import com.android.launcher3.LauncherFiles.SHARED_PREFERENCES_KEY
import com.android.launcher3.model.DeviceGridState
import com.android.launcher3.pm.InstallSessionHelper
import com.android.launcher3.provider.RestoreDbTask
import com.android.launcher3.provider.RestoreDbTask.FIRST_LOAD_AFTER_RESTORE_KEY
import com.android.launcher3.states.RotationHelper
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.util.Themes

/**
 * Use same context for shared preferences, so that we use a single cached instance
 *
 * TODO(b/262721340): Replace all direct SharedPreference refs with LauncherPrefs / Item methods.
 */
class LauncherPrefs(private val encryptedContext: Context) : SafeCloseable {
    private val deviceProtectedStorageContext =
        encryptedContext.createDeviceProtectedStorageContext()

    private val bootAwarePrefs
        get() =
            deviceProtectedStorageContext.getSharedPreferences(BOOT_AWARE_PREFS_KEY, MODE_PRIVATE)

    private val Item.encryptedPrefs
        get() = encryptedContext.getSharedPreferences(sharedPrefFile, MODE_PRIVATE)

    private fun chooseSharedPreferences(item: Item): SharedPreferences =
        if (item.encryptionType == EncryptionType.DEVICE_PROTECTED) bootAwarePrefs
        else item.encryptedPrefs

    /** Wrapper around `getInner` for a `ContextualItem` */
    fun <T> get(item: ContextualItem<T>): T =
        getInner(item, item.defaultValueFromContext(encryptedContext))

    /** Wrapper around `getInner` for an `Item` */
    fun <T> get(item: ConstantItem<T>): T = getInner(item, item.defaultValue)

    /**
     * Retrieves the value for an [Item] from [SharedPreferences]. It handles method typing via the
     * default value type, and will throw an error if the type of the item provided is not a
     * `String`, `Boolean`, `Float`, `Int`, `Long`, or `Set<String>`.
     */
    @Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
    private fun <T> getInner(item: Item, default: T): T {
        val sp = chooseSharedPreferences(item)

        return when (item.type) {
            String::class.java -> sp.getString(item.sharedPrefKey, default as? String)
            Boolean::class.java,
            java.lang.Boolean::class.java -> sp.getBoolean(item.sharedPrefKey, default as Boolean)
            Int::class.java,
            java.lang.Integer::class.java -> sp.getInt(item.sharedPrefKey, default as Int)
            Float::class.java,
            java.lang.Float::class.java -> sp.getFloat(item.sharedPrefKey, default as Float)
            Long::class.java,
            java.lang.Long::class.java -> sp.getLong(item.sharedPrefKey, default as Long)
            Set::class.java -> sp.getStringSet(item.sharedPrefKey, default as? Set<String>)
            else ->
                throw IllegalArgumentException(
                    "item type: ${item.type}" + " is not compatible with sharedPref methods"
                )
        }
            as T
    }

    /**
     * Stores each of the values provided in `SharedPreferences` according to the configuration
     * contained within the associated items provided. Internally, it uses apply, so the caller
     * cannot assume that the values that have been put are immediately available for use.
     *
     * The forEach loop is necessary here since there is 1 `SharedPreference.Editor` returned from
     * prepareToPutValue(itemsToValues) for every distinct `SharedPreferences` file present in the
     * provided item configurations.
     */
    fun put(vararg itemsToValues: Pair<Item, Any>): Unit =
        prepareToPutValues(itemsToValues).forEach { it.apply() }

    /** See referenced `put` method above. */
    fun <T : Any> put(item: Item, value: T): Unit = put(item.to(value))

    /**
     * Synchronously stores all the values provided according to their associated Item
     * configuration.
     */
    fun putSync(vararg itemsToValues: Pair<Item, Any>): Unit =
        prepareToPutValues(itemsToValues).forEach { it.commit() }

    /**
     * Updates the values stored in `SharedPreferences` for each corresponding Item-value pair. If
     * the item is boot aware, this method updates both the boot aware and the encrypted files. This
     * is done because: 1) It allows for easy roll-back if the data is already in encrypted prefs
     * and we need to turn off the boot aware data feature & 2) It simplifies Backup/Restore, which
     * already points to encrypted storage.
     *
     * Returns a list of editors with all transactions added so that the caller can determine to use
     * .apply() or .commit()
     */
    private fun prepareToPutValues(
        updates: Array<out Pair<Item, Any>>
    ): List<SharedPreferences.Editor> {
        val updatesPerPrefFile =
            updates
                .filter { it.first.encryptionType != EncryptionType.DEVICE_PROTECTED }
                .groupBy { it.first.encryptedPrefs }
                .toMutableMap()

        val bootAwareUpdates =
            updates.filter { it.first.encryptionType == EncryptionType.DEVICE_PROTECTED }
        if (bootAwareUpdates.isNotEmpty()) {
            updatesPerPrefFile[bootAwarePrefs] = bootAwareUpdates
        }

        return updatesPerPrefFile.map { prefToItemValueList ->
            prefToItemValueList.key.edit().apply {
                prefToItemValueList.value.forEach { itemToValue: Pair<Item, Any> ->
                    putValue(itemToValue.first, itemToValue.second)
                }
            }
        }
    }

    /**
     * Handles adding values to `SharedPreferences` regardless of type. This method is especially
     * helpful for updating `SharedPreferences` values for `List<<Item>Any>` that have multiple
     * types of Item values.
     */
    @Suppress("UNCHECKED_CAST")
    private fun SharedPreferences.Editor.putValue(
        item: Item,
        value: Any?
    ): SharedPreferences.Editor =
        when (item.type) {
            String::class.java -> putString(item.sharedPrefKey, value as? String)
            Boolean::class.java,
            java.lang.Boolean::class.java -> putBoolean(item.sharedPrefKey, value as Boolean)
            Int::class.java,
            java.lang.Integer::class.java -> putInt(item.sharedPrefKey, value as Int)
            Float::class.java,
            java.lang.Float::class.java -> putFloat(item.sharedPrefKey, value as Float)
            Long::class.java,
            java.lang.Long::class.java -> putLong(item.sharedPrefKey, value as Long)
            Set::class.java -> putStringSet(item.sharedPrefKey, value as? Set<String>)
            else ->
                throw IllegalArgumentException(
                    "item type: ${item.type} is not compatible with sharedPref methods"
                )
        }

    /**
     * After calling this method, the listener will be notified of any future updates to the
     * `SharedPreferences` files associated with the provided list of items. The listener will need
     * to filter update notifications so they don't activate for non-relevant updates.
     */
    fun addListener(listener: OnSharedPreferenceChangeListener, vararg items: Item) {
        items
            .map { chooseSharedPreferences(it) }
            .distinct()
            .forEach { it.registerOnSharedPreferenceChangeListener(listener) }
    }

    /**
     * Stops the listener from getting notified of any more updates to any of the
     * `SharedPreferences` files associated with any of the provided list of [Item].
     */
    fun removeListener(listener: OnSharedPreferenceChangeListener, vararg items: Item) {
        // If a listener is not registered to a SharedPreference, unregistering it does nothing
        items
            .map { chooseSharedPreferences(it) }
            .distinct()
            .forEach { it.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /**
     * Checks if all the provided [Item] have values stored in their corresponding
     * `SharedPreferences` files.
     */
    fun has(vararg items: Item): Boolean {
        items
            .groupBy { chooseSharedPreferences(it) }
            .forEach { (prefs, itemsSublist) ->
                if (!itemsSublist.none { !prefs.contains(it.sharedPrefKey) }) return false
            }
        return true
    }

    /**
     * Asynchronously removes the [Item]'s value from its corresponding `SharedPreferences` file.
     */
    fun remove(vararg items: Item) = prepareToRemove(items).forEach { it.apply() }

    /** Synchronously removes the [Item]'s value from its corresponding `SharedPreferences` file. */
    fun removeSync(vararg items: Item) = prepareToRemove(items).forEach { it.commit() }

    /**
     * Removes the key value pairs stored in `SharedPreferences` for each corresponding Item. If the
     * item is boot aware, this method removes the data from both the boot aware and encrypted
     * files.
     *
     * @return a list of editors with all transactions added so that the caller can determine to use
     *   .apply() or .commit()
     */
    private fun prepareToRemove(items: Array<out Item>): List<SharedPreferences.Editor> {
        val itemsPerFile =
            items
                .filter { it.encryptionType != EncryptionType.DEVICE_PROTECTED }
                .groupBy { it.encryptedPrefs }
                .toMutableMap()

        val bootAwareUpdates = items.filter { it.encryptionType == EncryptionType.DEVICE_PROTECTED }
        if (bootAwareUpdates.isNotEmpty()) {
            itemsPerFile[bootAwarePrefs] = bootAwareUpdates
        }

        return itemsPerFile.map { (prefs, items) ->
            prefs.edit().also { editor ->
                items.forEach { item -> editor.remove(item.sharedPrefKey) }
            }
        }
    }

    override fun close() {}

    companion object {
        @VisibleForTesting const val BOOT_AWARE_PREFS_KEY = "boot_aware_prefs"

        @JvmField var INSTANCE = MainThreadInitializedObject { LauncherPrefs(it) }

        @JvmStatic fun get(context: Context): LauncherPrefs = INSTANCE.get(context)

        const val TASKBAR_PINNING_KEY = "TASKBAR_PINNING_KEY"
        const val SHOULD_SHOW_SMARTSPACE_KEY = "SHOULD_SHOW_SMARTSPACE_KEY"
        @JvmField
        val ICON_STATE = nonRestorableItem("pref_icon_shape_path", "", EncryptionType.ENCRYPTED)

        @JvmField
        val ENABLE_TWOLINE_ALLAPPS_TOGGLE = backedUpItem("pref_enable_two_line_toggle", false)
        @JvmField
        val THEMED_ICONS = backedUpItem(Themes.KEY_THEMED_ICONS, false, EncryptionType.ENCRYPTED)
        @JvmField val PROMISE_ICON_IDS = backedUpItem(InstallSessionHelper.PROMISE_ICON_IDS, "")
        @JvmField val WORK_EDU_STEP = backedUpItem("showed_work_profile_edu", 0)
        @JvmField
        val WORKSPACE_SIZE =
            backedUpItem(DeviceGridState.KEY_WORKSPACE_SIZE, "", EncryptionType.ENCRYPTED)
        @JvmField
        val HOTSEAT_COUNT =
            backedUpItem(DeviceGridState.KEY_HOTSEAT_COUNT, -1, EncryptionType.ENCRYPTED)
        @JvmField
        val TASKBAR_PINNING =
            backedUpItem(TASKBAR_PINNING_KEY, false, EncryptionType.DEVICE_PROTECTED)

        @JvmField
        val DEVICE_TYPE =
            backedUpItem(
                DeviceGridState.KEY_DEVICE_TYPE,
                InvariantDeviceProfile.TYPE_PHONE,
                EncryptionType.ENCRYPTED
            )
        @JvmField
        val DB_FILE = backedUpItem(DeviceGridState.KEY_DB_FILE, "", EncryptionType.ENCRYPTED)
        @JvmField
        val SHOULD_SHOW_SMARTSPACE =
            backedUpItem(
                SHOULD_SHOW_SMARTSPACE_KEY,
                WIDGET_ON_FIRST_SCREEN,
                EncryptionType.DEVICE_PROTECTED
            )
        @JvmField
        val RESTORE_DEVICE =
            backedUpItem(
                RestoreDbTask.RESTORED_DEVICE_TYPE,
                InvariantDeviceProfile.TYPE_PHONE,
                EncryptionType.ENCRYPTED
            )
        @JvmField
        val IS_FIRST_LOAD_AFTER_RESTORE =
            nonRestorableItem(FIRST_LOAD_AFTER_RESTORE_KEY, false, EncryptionType.ENCRYPTED)
        @JvmField val APP_WIDGET_IDS = backedUpItem(RestoreDbTask.APPWIDGET_IDS, "")
        @JvmField val OLD_APP_WIDGET_IDS = backedUpItem(RestoreDbTask.APPWIDGET_OLD_IDS, "")
        @JvmField
        val GRID_NAME =
            ConstantItem(
                "idp_grid_name",
                isBackedUp = true,
                defaultValue = null,
                encryptionType = EncryptionType.ENCRYPTED,
                type = String::class.java
            )
        @JvmField
        val ALLOW_ROTATION =
            backedUpItem(RotationHelper.ALLOW_ROTATION_PREFERENCE_KEY, Boolean::class.java) {
                RotationHelper.getAllowRotationDefaultValue(DisplayController.INSTANCE.get(it).info)
            }

        // Preferences for widget configurations
        @JvmField
        val RECONFIGURABLE_WIDGET_EDUCATION_TIP_SEEN =
            backedUpItem("launcher.reconfigurable_widget_education_tip_seen", false)
        @JvmField
        val WIDGETS_EDUCATION_DIALOG_SEEN =
            backedUpItem("launcher.widgets_education_dialog_seen", false)
        @JvmField
        val WIDGETS_EDUCATION_TIP_SEEN = backedUpItem("launcher.widgets_education_tip_seen", false)

        @JvmStatic
        fun <T> backedUpItem(
            sharedPrefKey: String,
            defaultValue: T,
            encryptionType: EncryptionType = EncryptionType.ENCRYPTED
        ): ConstantItem<T> =
            ConstantItem(sharedPrefKey, isBackedUp = true, defaultValue, encryptionType)

        @JvmStatic
        fun <T> backedUpItem(
            sharedPrefKey: String,
            type: Class<out T>,
            encryptionType: EncryptionType = EncryptionType.ENCRYPTED,
            defaultValueFromContext: (c: Context) -> T
        ): ContextualItem<T> =
            ContextualItem(
                sharedPrefKey,
                isBackedUp = true,
                defaultValueFromContext,
                encryptionType,
                type
            )

        @JvmStatic
        fun <T> nonRestorableItem(
            sharedPrefKey: String,
            defaultValue: T,
            encryptionType: EncryptionType = EncryptionType.ENCRYPTED
        ): ConstantItem<T> =
            ConstantItem(sharedPrefKey, isBackedUp = false, defaultValue, encryptionType)

        @Deprecated("Don't use shared preferences directly. Use other LauncherPref methods.")
        @JvmStatic
        fun getPrefs(context: Context): SharedPreferences {
            // Use application context for shared preferences, so we use single cached instance
            return context.applicationContext.getSharedPreferences(
                SHARED_PREFERENCES_KEY,
                MODE_PRIVATE
            )
        }

        @Deprecated("Don't use shared preferences directly. Use other LauncherPref methods.")
        @JvmStatic
        fun getDevicePrefs(context: Context): SharedPreferences {
            // Use application context for shared preferences, so we use a single cached instance
            return context.applicationContext.getSharedPreferences(
                DEVICE_PREFERENCES_KEY,
                MODE_PRIVATE
            )
        }
    }
}

abstract class Item {
    abstract val sharedPrefKey: String
    abstract val isBackedUp: Boolean
    abstract val type: Class<*>
    abstract val encryptionType: EncryptionType
    val sharedPrefFile: String
        get() = if (isBackedUp) SHARED_PREFERENCES_KEY else DEVICE_PREFERENCES_KEY

    fun <T> to(value: T): Pair<Item, T> = Pair(this, value)
}

data class ConstantItem<T>(
    override val sharedPrefKey: String,
    override val isBackedUp: Boolean,
    val defaultValue: T,
    override val encryptionType: EncryptionType,
    // The default value can be null. If so, the type needs to be explicitly stated, or else NPE
    override val type: Class<out T> = defaultValue!!::class.java
) : Item() {

    fun get(c: Context): T = LauncherPrefs.get(c).get(this)
}

data class ContextualItem<T>(
    override val sharedPrefKey: String,
    override val isBackedUp: Boolean,
    private val defaultSupplier: (c: Context) -> T,
    override val encryptionType: EncryptionType,
    override val type: Class<out T>
) : Item() {
    private var default: T? = null

    fun defaultValueFromContext(context: Context): T {
        if (default == null) {
            default = defaultSupplier(context)
        }
        return default!!
    }

    fun get(c: Context): T = LauncherPrefs.get(c).get(this)
}

enum class EncryptionType {
    ENCRYPTED,
    DEVICE_PROTECTED,
}
