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
import android.os.ParcelFileDescriptor.AutoCloseOutputStream
import android.provider.Settings.Secure
import com.android.launcher3.AutoInstallsLayout
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.LauncherSettings.Settings.LAYOUT_DIGEST_LABEL
import com.android.launcher3.LauncherSettings.Settings.LAYOUT_DIGEST_TAG
import com.android.launcher3.LauncherSettings.Settings.LAYOUT_PROVIDER_KEY
import com.android.launcher3.LauncherSettings.Settings.createBlobProviderKey
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.Executors.ORDERED_BG_EXECUTOR
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture

object LayoutImportExportHelper {
    fun exportModelDbAsXmlFuture(context: Context): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        exportModelDbAsXml(context) { xmlString -> future.complete(xmlString) }
        return future
    }

    fun exportModelDbAsXml(context: Context, callback: (String) -> Unit) {
        val model = LauncherAppState.getInstance(context).model

        model.enqueueModelUpdateTask { _, dataModel, _ ->
            val builder = LauncherLayoutBuilder()
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
            callback(layoutXml)
        }
    }

    fun importModelFromXml(context: Context, xmlString: String) {
        importModelFromXml(context, xmlString.toByteArray(StandardCharsets.UTF_8))
    }

    fun importModelFromXml(context: Context, data: ByteArray) {
        val model = LauncherAppState.getInstance(context).model

        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        val handle = createWithSha256(digest, LAYOUT_DIGEST_LABEL, 0, LAYOUT_DIGEST_TAG)
        val blobManager = context.getSystemService(BlobStoreManager::class.java)!!

        val resolver = context.contentResolver

        blobManager.openSession(blobManager.createSession(handle)).use { session ->
            AutoCloseOutputStream(session.openWrite(0, -1)).use { it.write(data) }
            session.allowPublicAccess()

            session.commit(ORDERED_BG_EXECUTOR) {
                Secure.putString(resolver, LAYOUT_PROVIDER_KEY, createBlobProviderKey(digest))

                MODEL_EXECUTOR.submit { model.modelDbController.createEmptyDB() }.get()
                MAIN_EXECUTOR.submit { model.forceReload() }.get()
                MODEL_EXECUTOR.submit {}.get()
                Secure.putString(resolver, LAYOUT_PROVIDER_KEY, null)
            }
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
        }
    }
}
