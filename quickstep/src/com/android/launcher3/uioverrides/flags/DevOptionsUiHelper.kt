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

package com.android.launcher3.uioverrides.flags

import android.app.PendingIntent
import android.app.blob.BlobHandle.createWithSha256
import android.app.blob.BlobStoreManager
import android.content.Context
import android.content.IIntentReceiver
import android.content.IIntentSender.Stub
import android.content.Intent
import android.content.Intent.ACTION_CREATE_DOCUMENT
import android.content.Intent.ACTION_OPEN_DOCUMENT
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor.AutoCloseOutputStream
import android.provider.DeviceConfig
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.provider.Settings.Secure
import android.text.Html
import android.util.AttributeSet
import android.util.Base64
import android.util.Base64.NO_PADDING
import android.util.Base64.NO_WRAP
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreference
import com.android.launcher3.AutoInstallsLayout
import com.android.launcher3.ExtendedEditText
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.LauncherSettings.Settings.LAYOUT_DIGEST_KEY
import com.android.launcher3.LauncherSettings.Settings.LAYOUT_DIGEST_LABEL
import com.android.launcher3.LauncherSettings.Settings.LAYOUT_DIGEST_TAG
import com.android.launcher3.R
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.proxy.ProxyActivityStarter
import com.android.launcher3.secondarydisplay.SecondaryDisplayLauncher
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapperImpl
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.Executors.ORDERED_BG_EXECUTOR
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.OnboardingPrefs.ALL_APPS_VISITED_COUNT
import com.android.launcher3.util.OnboardingPrefs.HOME_BOUNCE_COUNT
import com.android.launcher3.util.OnboardingPrefs.HOME_BOUNCE_SEEN
import com.android.launcher3.util.OnboardingPrefs.HOTSEAT_DISCOVERY_TIP_COUNT
import com.android.launcher3.util.OnboardingPrefs.HOTSEAT_LONGPRESS_TIP_SEEN
import com.android.launcher3.util.OnboardingPrefs.TASKBAR_EDU_TOOLTIP_STEP
import com.android.launcher3.util.PluginManagerWrapper
import com.android.launcher3.util.StartActivityParams
import com.android.launcher3.util.UserIconInfo
import com.android.quickstep.util.DeviceConfigHelper
import com.android.quickstep.util.DeviceConfigHelper.Companion.NAMESPACE_LAUNCHER
import com.android.quickstep.util.DeviceConfigHelper.DebugInfo
import com.android.systemui.shared.plugins.PluginEnabler
import com.android.systemui.shared.plugins.PluginPrefs
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executor

/** Helper class to generate UI for Device Config */
class DevOptionsUiHelper(c: Context, attr: AttributeSet?) : PreferenceGroup(c, attr) {

    init {
        layoutResource = R.layout.developer_options_top_bar
        isPersistent = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        // Initialize search
        (holder.findViewById(R.id.filter_box) as TextView?)?.doAfterTextChanged {
            val query: String = it.toString().lowercase(Locale.getDefault()).replace("_", " ")
            filterPreferences(query, this)

            // Always keep myself visible
            this@DevOptionsUiHelper.isVisible = true
        }
    }

    private fun filterPreferences(query: String, pg: PreferenceGroup) {
        val count = pg.preferenceCount
        var visible = false
        for (i in 0 until count) {
            val preference = pg.getPreference(i)
            if (preference is PreferenceGroup) {
                filterPreferences(query, preference)
            } else {
                val title =
                    preference.title.toString().lowercase(Locale.getDefault()).replace("_", " ")
                preference.isVisible = query.isEmpty() || title.contains(query)
            }
            visible = visible or preference.isVisible
        }
        pg.isVisible = visible
    }

    override fun onAttached() {
        super.onAttached()

        removeAll()
        inflateServerFlags(newCategory("Server flags", "Long press to reset"))
        if (PluginPrefs.hasPlugins(context)) {
            inflatePluginPrefs(newCategory("Plugins"))
        }
        addIntentTargets()
        addOnboardingPrefsCategory()
        addLayoutSharePref()
    }

    private fun newCategory(titleText: String, subTitleText: String? = null) =
        PreferenceCategory(context).apply {
            title = titleText
            summary = subTitleText
            this@DevOptionsUiHelper.addPreference(this)
        }

    /** Inflates preferences for all server flags in the provider PreferenceGroup */
    private fun inflateServerFlags(parent: PreferenceGroup) {
        val prefs = DeviceConfigHelper.prefs
        // Sort the keys in the order of modified first followed by natural order
        val allProps =
            DeviceConfigHelper.allProps.values
                .toList()
                .sortedWith(
                    Comparator.comparingInt { prop: DebugInfo<*> ->
                            if (prefs.contains(prop.key)) 0 else 1
                        }
                        .thenComparing { prop: DebugInfo<*> -> prop.key }
                )

        // First add boolean flags
        allProps.forEach {
            if (it.isInt) return@forEach
            val info = it as DebugInfo<Boolean>

            val preference = CustomSwitchPref { holder, pref ->
                holder.itemView.setOnLongClickListener {
                    prefs.edit().remove(pref.key).apply()
                    pref.setChecked(info.getBoolValue())
                    summary = info.getSummary()
                    true
                }
            }
            preference.key = info.key
            preference.isPersistent = false
            preference.title = info.key
            preference.summary = info.getSummary()
            preference.setChecked(prefs.getBoolean(info.key, info.getBoolValue()))
            preference.setOnPreferenceChangeListener { _, newVal ->
                prefs.edit().putBoolean(info.key, newVal as Boolean).apply()
                preference.summary = info.getSummary()
                true
            }
            parent.addPreference(preference)
        }

        // Apply Int flags
        allProps.forEach {
            if (!it.isInt) return@forEach
            val info = it as DebugInfo<Int>

            val preference = CustomPref { holder, pref ->
                val textView = holder.findViewById(R.id.pref_edit_text) as ExtendedEditText
                textView.setText(info.getIntValueAsString())
                textView.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        prefs.edit().putInt(pref.key, textView.text.toString().toInt()).apply()
                        pref.summary = info.getSummary()
                        true
                    }
                    false
                }
                textView.setOnBackKeyListener {
                    textView.setText(info.getIntValueAsString())
                    true
                }

                holder.itemView.setOnLongClickListener {
                    prefs.edit().remove(pref.key).apply()
                    textView.setText(info.getIntValueAsString())
                    pref.summary = info.getSummary()
                    true
                }
            }
            preference.key = info.key
            preference.isPersistent = false
            preference.title = info.key
            preference.summary = info.getSummary()
            preference.widgetLayoutResource = R.layout.develop_options_edit_text
            parent.addPreference(preference)
        }
    }

    /**
     * Returns the summary to show the description and whether the flag overrides the default value.
     */
    private fun DebugInfo<*>.getSummary() =
        Html.fromHtml(
            (if (DeviceConfigHelper.prefs.contains(this.key))
                "<font color='red'><b>[OVERRIDDEN]</b></font><br>"
            else "") + this.desc
        )

    private fun DebugInfo<Boolean>.getBoolValue() =
        DeviceConfigHelper.prefs.getBoolean(
            this.key,
            DeviceConfig.getBoolean(NAMESPACE_LAUNCHER, this.key, this.valueInCode)
        )

    private fun DebugInfo<Int>.getIntValueAsString() =
        DeviceConfigHelper.prefs
            .getInt(this.key, DeviceConfig.getInt(NAMESPACE_LAUNCHER, this.key, this.valueInCode))
            .toString()

    /**
     * Inflates the preferences for plugins
     *
     * A single pref is added for a plugin-group. A plugin-group is a collection of plugins in a
     * single apk which have the same android:process tags defined. The apk should also hold the
     * PLUGIN_PERMISSION. We collect all the plugin intents which Launcher listens for and fetch all
     * corresponding plugins on the device. When a plugin-group is enabled/disabled we also need to
     * notify the pluginManager manually since the broadcast-mechanism only works in sysui process
     */
    private fun inflatePluginPrefs(parent: PreferenceGroup) {
        val manager = PluginManagerWrapper.INSTANCE[context] as PluginManagerWrapperImpl
        val pm = context.packageManager

        val pluginPermissionApps =
            pm.getPackagesHoldingPermissions(
                    arrayOf(PLUGIN_PERMISSION),
                    PackageManager.MATCH_DISABLED_COMPONENTS
                )
                .map { it.packageName }

        manager.pluginActions
            .flatMap { action ->
                pm.queryIntentServices(
                        Intent(action),
                        PackageManager.MATCH_DISABLED_COMPONENTS or
                            PackageManager.GET_RESOLVED_FILTER
                    )
                    .filter { pluginPermissionApps.contains(it.serviceInfo.packageName) }
            }
            .groupBy { "${it.serviceInfo.packageName}-${it.serviceInfo.processName}" }
            .values
            .forEach { infoList ->
                val pluginInfo = infoList[0]!!
                val pluginUri = Uri.fromParts("package", pluginInfo.serviceInfo.packageName, null)

                CustomSwitchPref { holder, _ ->
                        holder.itemView.setOnLongClickListener {
                            context.startActivity(
                                Intent(ACTION_APPLICATION_DETAILS_SETTINGS, pluginUri)
                            )
                            true
                        }
                    }
                    .apply {
                        isPersistent = true
                        title = pluginInfo.loadLabel(pm)
                        isChecked =
                            infoList.all {
                                manager.pluginEnabler.isEnabled(it.serviceInfo.componentName)
                            }
                        summary =
                            infoList
                                .map { it.filter }
                                .filter { it?.countActions() ?: 0 > 0 }
                                .joinToString(prefix = "Plugins: ") {
                                    it.getAction(0)
                                        .replace("com.android.systemui.action.PLUGIN_", "")
                                        .replace("com.android.launcher3.action.PLUGIN_", "")
                                }

                        setOnPreferenceChangeListener { _, newVal ->
                            val disabledState =
                                if (newVal as Boolean) PluginEnabler.ENABLED
                                else PluginEnabler.DISABLED_MANUALLY
                            infoList.forEach {
                                manager.pluginEnabler.setDisabled(
                                    it.serviceInfo.componentName,
                                    disabledState
                                )
                            }
                            manager.notifyChange(Intent(Intent.ACTION_PACKAGE_CHANGED, pluginUri))
                            true
                        }

                        parent.addPreference(this)
                    }
            }
    }

    private fun addIntentTargets() {
        val launchSandboxIntent =
            Intent("com.android.quickstep.action.GESTURE_SANDBOX")
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        newCategory("Gesture Navigation Sandbox").apply {
            addPreference(
                Preference(context).apply {
                    title = "Launch Gesture Tutorial Steps menu"
                    intent = Intent(launchSandboxIntent).putExtra("use_tutorial_menu", true)
                }
            )
            addPreference(
                Preference(context).apply {
                    title = "Launch Back Tutorial"
                    intent =
                        Intent(launchSandboxIntent)
                            .putExtra("use_tutorial_menu", false)
                            .putExtra("tutorial_steps", arrayOf("BACK_NAVIGATION"))
                }
            )
            addPreference(
                Preference(context).apply {
                    title = "Launch Home Tutorial"
                    intent =
                        Intent(launchSandboxIntent)
                            .putExtra("use_tutorial_menu", false)
                            .putExtra("tutorial_steps", arrayOf("HOME_NAVIGATION"))
                }
            )
            addPreference(
                Preference(context).apply {
                    title = "Launch Overview Tutorial"
                    intent =
                        Intent(launchSandboxIntent)
                            .putExtra("use_tutorial_menu", false)
                            .putExtra("tutorial_steps", arrayOf("OVERVIEW_NAVIGATION"))
                }
            )
        }

        newCategory("Other activity targets").apply {
            addPreference(
                Preference(context).apply {
                    title = "Launch Secondary Display"
                    intent =
                        Intent(context, SecondaryDisplayLauncher::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    private fun addOnboardingPrefsCategory() {
        newCategory("Onboarding Flows").apply {
            summary = "Reset these if you want to see the education again."
            addOnboardPref(
                "All Apps Bounce",
                HOME_BOUNCE_SEEN.sharedPrefKey,
                HOME_BOUNCE_COUNT.sharedPrefKey
            )
            addOnboardPref(
                "Hybrid Hotseat Education",
                HOTSEAT_DISCOVERY_TIP_COUNT.sharedPrefKey,
                HOTSEAT_LONGPRESS_TIP_SEEN.sharedPrefKey
            )
            addOnboardPref("Taskbar Education", TASKBAR_EDU_TOOLTIP_STEP.sharedPrefKey)
            addOnboardPref("All Apps Visited Count", ALL_APPS_VISITED_COUNT.sharedPrefKey)
        }
    }

    private fun PreferenceCategory.addOnboardPref(title: String, vararg keys: String) =
        this.addPreference(
            Preference(context).also {
                it.title = title
                it.summary = "Tap to reset"
                it.setOnPreferenceClickListener { _ ->
                    LauncherPrefs.getPrefs(context)
                        .edit()
                        .apply { keys.forEach { key -> remove(key) } }
                        .apply()
                    Toast.makeText(context, "Reset $title", Toast.LENGTH_SHORT).show()
                    true
                }
            }
        )

    private fun addLayoutSharePref() {
        val model = LauncherAppState.getInstance(context).model
        val category = newCategory("Workspace grid layout")
        Preference(context).apply {
            title = "Export"
            intent =
                createUriPickerIntent(ACTION_CREATE_DOCUMENT, MAIN_EXECUTOR) { uri ->
                    model.enqueueModelUpdateTask { _, dataModel, _ ->
                        val builder = LauncherLayoutBuilder()
                        dataModel.workspaceItems.forEach { info ->
                            val loc =
                                when (info.container) {
                                    CONTAINER_DESKTOP ->
                                        builder.atWorkspace(info.cellX, info.cellY, info.screenId)
                                    CONTAINER_HOTSEAT -> builder.atHotseat(info.screenId)
                                    else -> return@forEach
                                }
                            loc.addItem(info)
                        }
                        dataModel.appWidgets.forEach { info ->
                            builder.atWorkspace(info.cellX, info.cellY, info.screenId).addItem(info)
                        }

                        context.contentResolver.openOutputStream(uri).use { os ->
                            builder.build(OutputStreamWriter(os))
                        }

                        MAIN_EXECUTOR.execute {
                            Toast.makeText(context, "File saved", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            category.addPreference(this)
        }

        Preference(context).apply {
            title = "Import"
            intent =
                createUriPickerIntent(ACTION_OPEN_DOCUMENT, ORDERED_BG_EXECUTOR) { uri ->
                    val resolver = context.contentResolver
                    val data =
                        resolver.openInputStream(uri).use { stream ->
                            stream?.readAllBytes() ?: return@createUriPickerIntent
                        }

                    val digest = MessageDigest.getInstance("SHA-256").digest(data)
                    val handle = createWithSha256(digest, LAYOUT_DIGEST_LABEL, 0, LAYOUT_DIGEST_TAG)
                    val blobManager = context.getSystemService(BlobStoreManager::class.java)!!

                    blobManager.openSession(blobManager.createSession(handle)).use { session ->
                        AutoCloseOutputStream(session.openWrite(0, -1)).use { it.write(data) }
                        session.allowPublicAccess()

                        session.commit(ORDERED_BG_EXECUTOR) {
                            val key = Base64.encodeToString(digest, NO_WRAP or NO_PADDING)
                            Secure.putString(resolver, LAYOUT_DIGEST_KEY, key)

                            MODEL_EXECUTOR.submit { model.modelDbController.createEmptyDB() }.get()
                            MAIN_EXECUTOR.submit { model.forceReload() }.get()
                            MODEL_EXECUTOR.submit {}.get()
                            Secure.putString(resolver, LAYOUT_DIGEST_KEY, null)
                        }
                    }
                }
            category.addPreference(this)
        }
    }

    private fun LauncherLayoutBuilder.ItemTarget.addItem(info: ItemInfo) {
        val userType: String? =
            when (UserCache.INSTANCE.get(context).getUserInfo(info.user).type) {
                UserIconInfo.TYPE_WORK -> AutoInstallsLayout.USER_TYPE_WORK
                UserIconInfo.TYPE_CLONED -> AutoInstallsLayout.USER_TYPE_CLONED
                else -> null
            }
        when (info.itemType) {
            ITEM_TYPE_APPLICATION ->
                info.targetComponent?.let { c -> putApp(c.packageName, c.className, userType) }
            ITEM_TYPE_DEEP_SHORTCUT ->
                ShortcutKey.fromItemInfo(info).let { key ->
                    putShortcut(key.packageName, key.id, userType)
                }
            ITEM_TYPE_FOLDER ->
                (info as FolderInfo).let { folderInfo ->
                    putFolder(folderInfo.title?.toString() ?: "").also { folderBuilder ->
                        folderInfo.getContents().forEach { folderContent ->
                            folderBuilder.addItem(folderContent)
                        }
                    }
                }
            ITEM_TYPE_APPWIDGET ->
                putWidget(
                    (info as LauncherAppWidgetInfo).providerName.packageName,
                    info.providerName.className,
                    info.spanX,
                    info.spanY,
                    userType
                )
        }
    }

    private fun createUriPickerIntent(
        action: String,
        executor: Executor,
        callback: (uri: Uri) -> Unit
    ): Intent {
        val pendingIntent =
            PendingIntent(
                object : Stub() {
                    override fun send(
                        code: Int,
                        intent: Intent,
                        resolvedType: String?,
                        allowlistToken: IBinder?,
                        finishedReceiver: IIntentReceiver?,
                        requiredPermission: String?,
                        options: Bundle?
                    ) {
                        intent.data?.let { uri -> executor.execute { callback(uri) } }
                    }
                }
            )
        val params = StartActivityParams(pendingIntent, 0)
        params.intent =
            Intent(action)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/xml")
                .putExtra(Intent.EXTRA_TITLE, "launcher_grid.xml")
        return ProxyActivityStarter.getLaunchIntent(context, params)
    }

    private inner class CustomSwitchPref(
        private val bindCallback: (holder: PreferenceViewHolder, pref: SwitchPreference) -> Unit
    ) : SwitchPreference(context) {

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)
            bindCallback.invoke(holder, this)
        }
    }

    private inner class CustomPref(
        private val bindCallback: (holder: PreferenceViewHolder, pref: Preference) -> Unit
    ) : Preference(context) {

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)
            bindCallback.invoke(holder, this)
        }
    }

    companion object {
        const val TAG = "DeviceConfigUIHelper"

        const val PLUGIN_PERMISSION = "com.android.systemui.permission.PLUGIN"
    }
}
