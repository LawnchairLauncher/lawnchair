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

package com.android.launcher3.util

import android.R
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageInstaller.SessionParams
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.model.data.AppPairInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY2
import com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE
import com.android.launcher3.util.ModelTestExtensions.bgDataModel
import com.android.launcher3.util.ModelTestExtensions.isPersistedModelItem
import java.util.function.Supplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This test copies a lot of the DefaultLayoutProviderTest, which imports a variety of scenarios of
 * different types of items onto its workspace and hot seat. After setting up and verifying those
 * scenarios, these tests then additionally verify that the layout data can be exported properly,
 * via checking if it is loaded properly after being re-uploaded into the DB (after clearing the DB
 * of course).
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class LayoutImportExportHelperTest {
    @get:Rule val context = SandboxApplication().withModelDependency()
    @get:Rule val layout = LayoutResource(context)

    private val model: LauncherModel
        get() = context.appComponent.testableModelState.model

    private val workspaceItems: List<ItemInfo>
        get() =
            context.bgDataModel.itemsIdMap.filter {
                it.isPersistedModelItem() &&
                    (it.container == CONTAINER_DESKTOP || it.container == CONTAINER_HOTSEAT)
            }

    @Test
    fun exportAppOnHotseat() =
        importVerifyExportClearReImportVerify(
            LauncherLayoutBuilder().atHotseat(0).putApp(TEST_PACKAGE, TEST_ACTIVITY)
        ) {
            1 == workspaceItems.size &&
                CONTAINER_HOTSEAT == workspaceItems[0].container &&
                ITEM_TYPE_APPLICATION == workspaceItems[0].itemType
        }

    @Test
    fun exportAppFromWorkspace() =
        importVerifyExportClearReImportVerify(
            LauncherLayoutBuilder().atWorkspace(1, 1, 0).putApp(TEST_PACKAGE, TEST_ACTIVITY)
        ) {
            1 == workspaceItems.size &&
                CONTAINER_DESKTOP == workspaceItems[0].container &&
                ITEM_TYPE_APPLICATION == workspaceItems[0].itemType
        }

    @Test
    fun exportsFolderAndContentsFromHotseat() =
        importVerifyExportClearReImportVerify(
            LauncherLayoutBuilder()
                .atHotseat(0)
                .putFolder(R.string.copy)
                .addApp(TEST_PACKAGE, TEST_ACTIVITY)
                .addApp(TEST_PACKAGE, TEST_ACTIVITY)
                .addApp(TEST_PACKAGE, TEST_ACTIVITY)
                .build()
        ) {
            1 == workspaceItems.size &&
                ITEM_TYPE_FOLDER == workspaceItems[0].itemType &&
                3 == (workspaceItems[0] as FolderInfo).getContents().size
        }

    @Test
    fun exportsFolderAndContentsFromWorkspace() =
        importVerifyExportClearReImportVerify(
            LauncherLayoutBuilder()
                .atWorkspace(1, 1, 1)
                .putFolder("CustomFolder")
                .addApp(TEST_PACKAGE, TEST_ACTIVITY)
                .addApp(TEST_PACKAGE, TEST_ACTIVITY)
                .addApp(TEST_PACKAGE, TEST_ACTIVITY)
                .build()
        ) {
            1 == workspaceItems.size &&
                ITEM_TYPE_FOLDER == workspaceItems[0].itemType &&
                3 == (workspaceItems[0] as FolderInfo).getContents().size &&
                "CustomFolder" == workspaceItems[0].title
        }

    @Test
    fun exportWidgetFromWorkspace() {
        val pendingAppPkg = "com.test.pending"

        // Add a placeholder session info so that the widget exists
        ApplicationProvider.getApplicationContext<Context>().packageManager.packageInstaller.also {
            it.createSession(
                SessionParams(SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(pendingAppPkg)
                    setAppIcon(BitmapInfo.LOW_RES_ICON)
                    installerPackageName =
                        ApplicationProvider.getApplicationContext<Context>().packageName
                }
            )
        }

        importVerifyExportClearReImportVerify(
            LauncherLayoutBuilder()
                .atWorkspace(0, 1, 0)
                .putWidget(pendingAppPkg, "PlaceholderWidget", 2, 2)
        ) {
            1 == workspaceItems.size &&
                ITEM_TYPE_APPWIDGET == workspaceItems[0].itemType &&
                2 == workspaceItems[0].spanX &&
                2 == workspaceItems[0].spanY
        }
    }

    @Test
    fun exportDeepShortcutFromHotseat() {
        assumeTrue(
            context.getSystemService(LauncherApps::class.java)?.hasShortcutHostPermission() ?: false
        )
        importVerifyExportClearReImportVerify(
            LauncherLayoutBuilder()
                .atHotseat(0)
                .putFolder(R.string.copy)
                .addApp(TEST_PACKAGE, TEST_ACTIVITY)
                .addApp(TEST_PACKAGE, TEST_ACTIVITY)
                .addShortcut(TEST_PACKAGE, "shortcut2")
                .build()
        ) {
            val folder =
                workspaceItems.firstOrNull() as? FolderInfo
                    ?: return@importVerifyExportClearReImportVerify false
            CONTAINER_HOTSEAT == folder.container &&
                ITEM_TYPE_FOLDER == folder.itemType &&
                3 == folder.getContents().size &&
                ITEM_TYPE_DEEP_SHORTCUT == folder.getContents()[2].itemType
        }
    }

    @Test
    fun exportDeepShortcutFromFolder() {
        assumeTrue(
            context.getSystemService(LauncherApps::class.java)?.hasShortcutHostPermission() ?: false
        )
        importVerifyExportClearReImportVerify(
            LauncherLayoutBuilder().atHotseat(0).putShortcut(TEST_PACKAGE, "shortcut2")
        ) {
            1 == workspaceItems.size &&
                CONTAINER_HOTSEAT == workspaceItems[0].container &&
                ITEM_TYPE_DEEP_SHORTCUT == workspaceItems[0].itemType
        }
    }

    @Test
    fun exportAndImportAppPairOnWorkspace() {
        importVerifyExportClearReImportVerify(
            LauncherLayoutBuilder()
                .atWorkspace(1, 1, 1)
                .putAppPair("CustomAppPair")
                .addApp(TEST_PACKAGE, TEST_ACTIVITY)
                .addApp(TEST_PACKAGE, TEST_ACTIVITY2)
                .build()
        ) {
            1 == workspaceItems.size &&
                ITEM_TYPE_APP_PAIR == workspaceItems[0].itemType &&
                2 == (workspaceItems[0] as AppPairInfo).getContents().size &&
                "CustomAppPair" == workspaceItems[0].title
        }
    }

    @Test
    fun exportAndImportAppPairInFolder() {
        importVerifyExportClearReImportVerify(
            LauncherLayoutBuilder()
                .atWorkspace(1, 1, 1)
                .putFolder("CustomFolder")
                .addApp(TEST_PACKAGE, TEST_ACTIVITY)
                .putAppPair("CustomAppPair")
                .addApp(TEST_PACKAGE, TEST_ACTIVITY)
                .addApp(TEST_PACKAGE, TEST_ACTIVITY2)
                .build()
        ) {
            if (workspaceItems.size != 1) return@importVerifyExportClearReImportVerify false

            val folderInfo = workspaceItems[0] as FolderInfo
            val appPairInfo = folderInfo.getContents()[1] as AppPairInfo

            ITEM_TYPE_APP_PAIR == appPairInfo.itemType &&
                2 == appPairInfo.getContents().size &&
                "CustomAppPair" == appPairInfo.title
        }
    }

    @Test
    fun importSetsGridToXmlAttributes() {
        val idp = InvariantDeviceProfile.INSTANCE.get(context)
        val differentGridOption =
            idp.parseAllGridOptions(context).firstOrNull {
                it.numRows != idp.numRows || it.numColumns != idp.numColumns
            }
        assumeNotNull(differentGridOption)
        val layoutXml =
            LauncherLayoutBuilder(differentGridOption!!.numRows, differentGridOption.numColumns)
                .atWorkspace(1, 1, 1)
                .putFolder("CustomFolder")
                .addApp(TEST_PACKAGE, TEST_ACTIVITY)
                .putAppPair("CustomAppPair")
                .addApp(TEST_PACKAGE, TEST_ACTIVITY)
                .addApp(TEST_PACKAGE, TEST_ACTIVITY2)
                .build()
        context.appComponent.layoutImportExportHelper.importModelFromXml(layoutXml.build())
        assertEquals(differentGridOption.numRows, idp.numRows)
        assertEquals(differentGridOption.numColumns, idp.numColumns)
    }

    private fun importVerifyExportClearReImportVerify(
        layoutBuilder: LauncherLayoutBuilder,
        verification: Supplier<Boolean>,
    ) {
        layout.set(layoutBuilder)
        assertTrue(verification.get())

        val exportedXml =
            context.appComponent.layoutImportExportHelper.exportModelDbAsXmlFuture().get()

        // To test the exported XML, we merely clear the DB and re-load it. If the XML is
        // well-formatted, it will be loaded into the DB properly. Otherwise, it will be rejected.
        layout.set(LauncherLayoutBuilder())
        MODEL_EXECUTOR.submit {
                try {
                    model.modelDbController.createEmptyDB()
                } catch (_: Exception) {}
            }
            .get()
        MAIN_EXECUTOR.submit { model.forceReload() }.get()
        MODEL_EXECUTOR.submit {}.get()
        assertFalse(verification.get())

        context.appComponent.layoutImportExportHelper.importModelFromXml(exportedXml)
        assertTrue(verification.get())
    }
}
