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

package com.android.launcher3.model

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_ONE_SHOT
import android.content.Context
import android.content.Intent
import android.util.Log

/** Data model for the information used for [FirstScreenBroadcastHelper] Broadcast Extras */
data class FirstScreenBroadcastModel(
    // Package name of the installer for all items
    val installerPackage: String,
    // Installing items in Folders
    val pendingCollectionItems: MutableSet<String> = mutableSetOf(),
    // Installing items on first screen
    val pendingWorkspaceItems: MutableSet<String> = mutableSetOf(),
    // Installing items on hotseat
    val pendingHotseatItems: MutableSet<String> = mutableSetOf(),
    // Installing widgets on first screen
    val pendingWidgetItems: MutableSet<String> = mutableSetOf(),
    // Installed/Archived Items on first screen
    val installedWorkspaceItems: MutableSet<String> = mutableSetOf(),
    // Installed/Archived items on hotseat
    val installedHotseatItems: MutableSet<String> = mutableSetOf(),
    // Installed/Archived Widgets, sorted such that the first screen items appear before secondary
    // screen items
    val installedWidgets: LinkedHashSet<String> = linkedSetOf(),
    // Should attach extras about installed items in the broadcast intent
    val shouldAttachArchivingExtras: Boolean = true,
) {

    /** Returns count of all Items held by [FirstScreenBroadcastModel]. */
    fun getTotalItemCount() =
        pendingCollectionItems.size +
            pendingWorkspaceItems.size +
            pendingHotseatItems.size +
            pendingWidgetItems.size +
            installedWorkspaceItems.size +
            installedHotseatItems.size +
            installedWidgets.size

    private fun printDebugInfo() {
        if (DEBUG) {
            Log.d(
                TAG,
                "Sending First Screen Broadcast for installer=$installerPackage, total packages=${getTotalItemCount()}",
            )
            pendingCollectionItems.forEach {
                Log.d(TAG, "$installerPackage:Pending Collection item:$it")
            }
            pendingWorkspaceItems.forEach {
                Log.d(TAG, "$installerPackage:Pending Workspace item:$it")
            }
            pendingHotseatItems.forEach { Log.d(TAG, "$installerPackage:Pending Hotseat item:$it") }
            pendingWidgetItems.forEach { Log.d(TAG, "$installerPackage:Pending Widget item:$it") }
            installedWorkspaceItems.forEach {
                Log.d(TAG, "$installerPackage:Installed Workspace item:$it")
            }
            installedHotseatItems.forEach {
                Log.d(TAG, "$installerPackage:Installed Hotseat item:$it")
            }
            installedWidgets.forEach { Log.d(TAG, "$installerPackage:Installed Widget item :$it") }
        }
    }

    private fun Intent.putListExtra(key: String, items: Collection<String>) =
        putStringArrayListExtra(key, ArrayList(items))

    /** From the model data, create Intent to send broadcasts and fire them. */
    fun sentBroadcast(context: Context) {
        printDebugInfo()
        val verificationToken =
            PendingIntent.getActivity(
                context,
                0 /* requestCode */,
                Intent(),
                FLAG_ONE_SHOT or FLAG_IMMUTABLE,
            )
        val intent =
            Intent(ACTION_FIRST_SCREEN_ACTIVE_INSTALLS)
                .setPackage(installerPackage)
                .putExtra(VERIFICATION_TOKEN_EXTRA, verificationToken)
                .putListExtra(PENDING_COLLECTION_ITEM_EXTRA, pendingCollectionItems)
                .putListExtra(PENDING_WORKSPACE_ITEM_EXTRA, pendingWorkspaceItems)
                .putListExtra(PENDING_HOTSEAT_ITEM_EXTRA, pendingHotseatItems)
                .putListExtra(PENDING_WIDGET_ITEM_EXTRA, pendingWidgetItems)
                .apply {
                    if (shouldAttachArchivingExtras) {
                        putListExtra(INSTALLED_WORKSPACE_ITEMS_EXTRA, installedWorkspaceItems)
                        putListExtra(INSTALLED_HOTSEAT_ITEMS_EXTRA, installedHotseatItems)
                        putListExtra(ALL_INSTALLED_WIDGETS_ITEM_EXTRA, installedWidgets)
                    }
                }
        context.sendBroadcast(intent)
    }

    companion object {
        private const val TAG = "FirstScreenBroadcastModel"
        private const val DEBUG = true

        private const val ACTION_FIRST_SCREEN_ACTIVE_INSTALLS =
            "com.android.launcher3.action.FIRST_SCREEN_ACTIVE_INSTALLS"

        // String retained as "folderItem" for back-compatibility reasons.
        private const val PENDING_COLLECTION_ITEM_EXTRA = "folderItem"
        private const val PENDING_WORKSPACE_ITEM_EXTRA = "workspaceItem"
        private const val PENDING_HOTSEAT_ITEM_EXTRA = "hotseatItem"
        private const val PENDING_WIDGET_ITEM_EXTRA = "widgetItem"
        // Extras containing all installed items, including Archived Apps.
        private const val INSTALLED_WORKSPACE_ITEMS_EXTRA = "workspaceInstalledItems"
        private const val INSTALLED_HOTSEAT_ITEMS_EXTRA = "hotseatInstalledItems"
        // This includes installed widgets on all screens, not just first.
        private const val ALL_INSTALLED_WIDGETS_ITEM_EXTRA = "widgetInstalledItems"
        private const val VERIFICATION_TOKEN_EXTRA = "verificationToken"
    }
}
