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

@file:JvmName("AppToWebUtils")

package com.android.wm.shell.apptoweb

import android.app.assist.AssistContent
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.view.Display
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.protolog.ShellProtoLogGroup
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper
import com.android.wm.shell.shared.desktopmode.DesktopState

private const val TAG = "AppToWebUtils"

private val GenericBrowserIntent = Intent()
    .setAction(ACTION_VIEW)
    .addCategory(Intent.CATEGORY_BROWSABLE)
    .setData(Uri.parse("http:"))

/**
 * Check if app links can be shown
 */
fun canShowAppLinks(display: Display, desktopState: DesktopState): Boolean {
    if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
        return desktopState.isDesktopModeSupportedOnDisplay(display)
    }
    return true
}

/**
 * Returns a boolean indicating whether a given package is a browser app.
 */
fun isBrowserApp(context: Context, packageName: String, userId: Int): Boolean {
    GenericBrowserIntent.setPackage(packageName)
    val list = context.packageManager.queryIntentActivitiesAsUser(
        GenericBrowserIntent, PackageManager.MATCH_ALL, userId
    )

    list.forEach {
        if (it.activityInfo != null && it.handleAllWebDataURI) {
            return true
        }
    }
    return false
}

/**
 * Returns intent if there is a browser application available to handle the uri. Otherwise, returns
 * null.
 */
fun getBrowserIntent(uri: Uri, packageManager: PackageManager, userId: Int): Intent? {
    val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
        .setData(uri)
        .addFlags(FLAG_ACTIVITY_NEW_TASK)
    // If there is a browser application available to handle the intent, return the intent.
    // Otherwise, return null.
    val resolveInfo = packageManager.resolveActivityAsUser(intent, /* flags= */ 0, userId)
        ?: return null
    intent.setComponent(resolveInfo.componentInfo.componentName)
    return intent
}

/**
 * Returns intent if there is a non-browser application available to handle the uri. Otherwise,
 * returns null.
 */
fun getAppIntent(uri: Uri, packageManager: PackageManager, userId: Int): Intent? {
    val intent = Intent(ACTION_VIEW, uri).addFlags(FLAG_ACTIVITY_NEW_TASK)
    val resolveInfo = packageManager.resolveActivityAsUser(intent, /* flags= */ 0, userId)
        ?: return null
    // If there is a non-browser application available to handle the intent, return the intent.
    // Otherwise, return null.
     if (resolveInfo.activityInfo != null && !resolveInfo.handleAllWebDataURI) {
        intent.setComponent(resolveInfo.componentInfo.componentName)
        return intent
    }
    return null
}

/**
 * Returns the [DomainVerificationUserState] of the user associated with the given
 * [DomainVerificationManager] and the given package.
 */
fun getDomainVerificationUserState(
    manager: DomainVerificationManager,
    packageName: String
): DomainVerificationUserState? {
    try {
        return manager.getDomainVerificationUserState(packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        ProtoLog.w(
            ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE,
            "%s: Failed to get domain verification user state: %s",
            TAG,
            e.message!!
        )
        return null
    }
}

/**
 * Returns the web uri from the given [AssistContent].
 */
fun AssistContent.getSessionWebUri(): Uri? {
    return sessionTransferUri ?: webUri
}
