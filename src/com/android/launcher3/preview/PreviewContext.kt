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
package com.android.launcher3.preview

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.text.TextUtils
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Item
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.ProxyPrefs
import com.android.launcher3.WorkspaceLayoutManager
import com.android.launcher3.compose.core.widgetpicker.NoOpWidgetPickerModule
import com.android.launcher3.concurrent.ExecutorsModule
import com.android.launcher3.dagger.ApiWrapperModule
import com.android.launcher3.dagger.AppModule
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.dagger.LauncherConcurrencyModule
import com.android.launcher3.dagger.PerDisplayModule
import com.android.launcher3.dagger.PluginManagerWrapperModule
import com.android.launcher3.dagger.StaticObjectModule
import com.android.launcher3.dagger.WindowManagerProxyModule
import com.android.launcher3.model.LayoutParserFactory
import com.android.launcher3.model.LayoutParserFactory.XmlLayoutParserFactory
import com.android.launcher3.model.ModelInitializer
import com.android.launcher3.model.data.LoaderParams
import com.android.launcher3.provider.LauncherDbUtils.selectionForWorkspaceScreen
import com.android.launcher3.util.SandboxContext
import com.android.launcher3.util.dagger.LauncherExecutorsModule
import com.android.launcher3.widget.LauncherWidgetHolder
import com.android.launcher3.widget.LauncherWidgetHolder.WidgetHolderFactory
import com.android.launcher3.widget.util.WidgetSizeHandler
import com.android.systemui.shared.Flags
import dagger.BindsInstance
import dagger.Component
import java.io.File
import java.util.Arrays
import java.util.UUID

/**
 * Context used just for preview. It also provides a few objects (e.g. UserCache) just for preview
 * purposes.
 */
class PreviewContext
@JvmOverloads
constructor(
    base: Context,
    gridName: String?,
    widgetHostId: Int = LauncherWidgetHolder.APPWIDGET_HOST_ID,
    layoutXml: String? = null,
    workspacePageId: Int = WorkspaceLayoutManager.FIRST_SCREEN_ID,
) : SandboxContext(base) {
    private val mPrefName: String

    private val mDbDir: File?

    init {
        val randomUid = UUID.randomUUID().toString()
        mPrefName = "preview-$randomUid"
        val prefs = ProxyPrefs(this, getSharedPreferences(mPrefName, MODE_PRIVATE))
        prefs.putOrRemove(LauncherPrefs.GRID_NAME, gridName)
        prefs.put(LauncherPrefs.FIXED_LANDSCAPE_MODE, false)

        val isTwoPanel =
            base.appComponent.idp.supportedProfiles.any { it.deviceProperties.isTwoPanels }
        val closestEvenPageId: Int = workspacePageId - (workspacePageId % 2)
        val selectionQuery =
            if (isTwoPanel) selectionForWorkspaceScreen(closestEvenPageId, closestEvenPageId + 1)
            else selectionForWorkspaceScreen(workspacePageId)

        val builder = DaggerPreviewContext_PreviewAppComponent.builder().bindPrefs(prefs)
        builder
            .bindLoaderParams(
                LoaderParams(
                    workspaceSelection = selectionQuery,
                    sanitizeData = false,
                    loadNonWorkspaceItems = false,
                )
            )
            .bindWidgetSizeHandler(NoOpWidgetSizeHandler(this))

        if (layoutXml.isNullOrEmpty() || !Flags.extendibleThemeManager()) {
            mDbDir = null
            builder
                .bindParserFactory(LayoutParserFactory(this))
                .bindWidgetsFactory(base.appComponent.widgetHolderFactory)
        } else {
            mDbDir = File(base.filesDir, randomUid)
            emptyDbDir()
            mDbDir.mkdirs()
            builder.bindParserFactory(XmlLayoutParserFactory(this, layoutXml)).bindWidgetsFactory {
                c: Context ->
                LauncherWidgetHolder(c, widgetHostId).apply { startListening() }
            }
        }
        initDaggerComponent(builder)

        if (!TextUtils.isEmpty(layoutXml)) {
            // Use null the DB file so that we use a new in-memory DB
            InvariantDeviceProfile.INSTANCE[this].dbFile = null
        }
    }

    fun <T : Any> LauncherPrefs.putOrRemove(item: Item, value: T?) {
        if (value != null) put(item, value) else remove(item)
    }

    private fun emptyDbDir() {
        if (mDbDir != null && mDbDir.exists()) {
            Arrays.stream(mDbDir.listFiles()).forEach { obj: File -> obj.delete() }
        }
    }

    override fun cleanUpObjects() {
        super.cleanUpObjects()
        deleteSharedPreferences(mPrefName)
        if (mDbDir != null) {
            emptyDbDir()
            mDbDir.delete()
        }
    }

    override fun getDatabasePath(name: String): File =
        if (mDbDir != null) File(mDbDir, name) else super.getDatabasePath(name)

    private class NoOpWidgetSizeHandler(context: Context) : WidgetSizeHandler(context) {

        override fun updateSizeRangesAsync(
            widgetId: Int,
            info: AppWidgetProviderInfo,
            spanX: Int,
            spanY: Int,
        ) {
            // Ignore
        }
    }

    @LauncherAppSingleton // Exclude widget module since we bind widget holder separately
    @Component(
        modules =
            [
                WindowManagerProxyModule::class,
                ApiWrapperModule::class,
                PluginManagerWrapperModule::class,
                StaticObjectModule::class,
                AppModule::class,
                PerDisplayModule::class,
                LauncherConcurrencyModule::class,
                ExecutorsModule::class,
                LauncherExecutorsModule::class,
                NoOpWidgetPickerModule::class,
            ]
    )
    interface PreviewAppComponent : LauncherAppComponent {
        val model: LauncherModel
        val modelInitializer: ModelInitializer

        /** Builder for NexusLauncherAppComponent. */
        @Component.Builder
        interface Builder : LauncherAppComponent.Builder {
            @BindsInstance fun bindPrefs(prefs: LauncherPrefs): Builder

            @BindsInstance fun bindParserFactory(parserFactory: LayoutParserFactory): Builder

            @BindsInstance fun bindWidgetsFactory(holderFactory: WidgetHolderFactory): Builder

            @BindsInstance fun bindLoaderParams(params: LoaderParams): Builder

            @BindsInstance fun bindWidgetSizeHandler(handler: WidgetSizeHandler): Builder

            override fun build(): PreviewAppComponent
        }
    }
}
