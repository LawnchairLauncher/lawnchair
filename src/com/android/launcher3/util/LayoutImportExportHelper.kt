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
package com.android.launcher3.util

import android.app.blob.BlobHandle.createWithSha256
import android.app.blob.BlobStoreManager
import android.content.Context
import android.database.sqlite.SQLiteReadOnlyDatabaseException
import android.os.ParcelFileDescriptor.AutoCloseOutputStream
import android.provider.Settings.Secure
import android.util.Log
import com.android.launcher3.AutoInstallsLayout
import com.android.launcher3.GridSizeUtil
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.LauncherSettings.Settings.LAYOUT_DIGEST_LABEL
import com.android.launcher3.LauncherSettings.Settings.LAYOUT_DIGEST_TAG
import com.android.launcher3.LauncherSettings.Settings.LAYOUT_PROVIDER_KEY
import com.android.launcher3.LauncherSettings.Settings.createBlobProviderKey
import com.android.launcher3.concurrent.annotations.Background
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.logging.FileLog
import com.android.launcher3.model.data.AppPairInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import javax.inject.Inject

class LayoutImportExportHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val model: LauncherModel,
    private val idp: InvariantDeviceProfile,
    @Background private val backgroundExecutor: ExecutorService,
    @Ui private val uiExecutor: ExecutorService,
) {

    fun exportModelDbAsXmlFuture(): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        exportModelDbAsXml { xmlString -> future.complete(xmlString) }
        return future
    }

    fun exportModelDbAsXml(callback: (String) -> Unit) {
        model.enqueueModelUpdateTask { _, dataModel, _ ->
            val builder = LauncherLayoutBuilder(idp.numRows, idp.numColumns)
            dataModel.itemsIdMap.forEach { info ->
                val loc =
                    when (info.container) {
                        CONTAINER_DESKTOP ->
                            builder.atWorkspace(info.cellX, info.cellY, info.screenId)

                        CONTAINER_HOTSEAT -> builder.atHotseat(info.screenId)
                        else -> return@forEach
                    }
                loc.addItem(context, info)
            }

            val layoutXml = builder.build()
            FileLog.i(TAG, "Exporting Home Screen Layout as XML:\n$layoutXml")
            callback(layoutXml)
        }
    }

    fun importModelFromXml(xmlString: String) {
        importModelFromXml(xmlString.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * The provided XML will be loaded into the launcher's data model via the AutoInstallLayout
     * mechanism that runs the first time the Launcher is setup. We simulate those conditions by
     * deleting the launcher's db first, which queries the "default launcher layout" from secure
     * settings.
     *
     * This method is long-lasting and synchronous. It also has the potential to block multiple
     * threads. This is acceptable because this method is only expected to be called when the user
     * is not interacting with a launcher, and another operation is waiting on this to completely
     * finish to begin. Current usages (June 2025) is cross OEM backup - restore and launcher test
     * setup, both which happen when the launcher is unavailable.
     *
     * Internally, LauncherModel.rebindCallbacks() is called to load the updated data into in-memory
     * data model. This will short-circuit (and not load the new data) if called without callbacks.
     */
    fun importModelFromXml(data: ByteArray) {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        val handle = createWithSha256(digest, LAYOUT_DIGEST_LABEL, 0, LAYOUT_DIGEST_TAG)
        val blobManager = context.getSystemService(BlobStoreManager::class.java)!!
        val syncLatch = CountDownLatch(1)

        val resolver = context.contentResolver
        blobManager.openSession(blobManager.createSession(handle)).use { session ->
            AutoCloseOutputStream(session.openWrite(0, -1)).use { it.write(data) }
            session.allowPublicAccess()
            session.commit(backgroundExecutor) {
                Secure.putString(resolver, LAYOUT_PROVIDER_KEY, createBlobProviderKey(digest))
                val xmlString = data.toString(StandardCharsets.UTF_8)
                GridSizeUtil(context).parseAndSetGridSize(xmlString)
                FileLog.i(TAG, "Importing XML as Home Screen Layout:\n$xmlString")
                MODEL_EXECUTOR.submit { createEmptyDbSafe() }.get()
                uiExecutor.submit { model.forceReload() }.get()
                MODEL_EXECUTOR.submit {}.get()
                syncLatch.countDown()
            }
        }
        syncLatch.await()
    }

    private fun createEmptyDbSafe() {
        try {
            model.modelDbController.createEmptyDB()
        } catch (e: SQLiteReadOnlyDatabaseException) {
            // This issue has only been observed in tests so far, likely due
            // to less strict threading for accessing and writing to the
            // launcher test DB.
            Log.w(TAG, "Failed to clear Launcher DB. It was already deleted.", e)
        }
    }

    private fun LauncherLayoutBuilder.ItemTarget.addItem(context: Context, info: ItemInfo) {
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
                            folderBuilder.addItem(context, folderContent)
                        }
                    }
                }
            ITEM_TYPE_APPWIDGET ->
                putWidget(
                    (info as LauncherAppWidgetInfo).providerName.packageName,
                    info.providerName.className,
                    info.spanX,
                    info.spanY,
                    userType,
                )
            ITEM_TYPE_APP_PAIR ->
                (info as AppPairInfo).let { appPairInfo ->
                    putAppPair(appPairInfo.title?.toString() ?: "").also { appPairBuilder ->
                        appPairInfo.getContents().forEach { appPairContent ->
                            appPairBuilder.addItem(context, appPairContent)
                        }
                    }
                }
        }
    }

    companion object {
        private const val TAG = "LayoutImportExportHelper"
    }
}
