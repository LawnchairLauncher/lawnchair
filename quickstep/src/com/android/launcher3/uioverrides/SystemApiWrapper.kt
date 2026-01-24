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
package com.android.launcher3.uioverrides

import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.Context
import android.content.IIntentReceiver
import android.content.IIntentSender
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Flags.allowPrivateProfile
import android.os.IBinder
import android.os.UserHandle
import android.os.UserManager
import android.util.ArrayMap
import android.view.SurfaceControlViewHost
import android.widget.Toast
import android.window.RemoteTransition
import android.window.ScreenCapture
import com.android.launcher3.BaseActivity
import androidx.annotation.RequiresApi
import com.android.launcher3.Flags.enablePrivateSpace
import com.android.launcher3.Flags.privateSpaceSysAppsSeparation
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.proxy.ProxyActivityStarter
import com.android.launcher3.uioverrides.touchcontrollers.StatusBarTouchController
import com.android.launcher3.util.ApiWrapper
import com.android.launcher3.util.Executors
import com.android.launcher3.util.StartActivityParams
import com.android.launcher3.util.UserIconInfo
import com.android.quickstep.util.FadeOutRemoteTransition
import java.util.function.Supplier
import javax.inject.Inject

import app.lawnchair.LawnchairApp

/** A wrapper for the hidden API calls */
@LauncherAppSingleton
open class SystemApiWrapper @Inject constructor(@ApplicationContext context: Context?) :
    ApiWrapper(context) {

    override fun getPersons(si: ShortcutInfo) = si.persons ?: Utilities.EMPTY_PERSON_ARRAY

    override fun getActivityOverrides(): Map<String, LauncherActivityInfo> {
        return try {
            mContext.getSystemService(LauncherApps::class.java)!!.activityOverrides
        } catch (t: Throwable) {
            super.activityOverrides
        }
    }

    override fun createFadeOutAnimOptions(): ActivityOptions {
        return try {
            ActivityOptions.makeBasic().apply {
                remoteTransition = RemoteTransition(FadeOutRemoteTransition(), "FadeOut")
            }
        } catch (t: Throwable) {
            super.createFadeOutAnimOptions()
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun queryAllUsers(): Map<UserHandle, UserIconInfo> {
        if (!enablePrivateSpace() || !LawnchairApp.isRecentsEnabled) {
            return super.queryAllUsers()
        }
        return try {
            val users = ArrayMap<UserHandle, UserIconInfo>()
            mContext.getSystemService(UserManager::class.java)!!.userProfiles?.forEach { user ->
                mContext.getSystemService(LauncherApps::class.java)!!.getLauncherUserInfo(user)?.apply {
                    users[user] =
                        UserIconInfo(
                            user,
                            when (userType) {
                                UserManager.USER_TYPE_PROFILE_MANAGED -> UserIconInfo.TYPE_WORK
                                UserManager.USER_TYPE_PROFILE_CLONE -> UserIconInfo.TYPE_CLONED
                                UserManager.USER_TYPE_PROFILE_PRIVATE -> UserIconInfo.TYPE_PRIVATE
                                else -> UserIconInfo.TYPE_MAIN
                            },
                            userSerialNumber.toLong()
                        )
                }
            }
            return users
        } catch (t : Throwable) {
            return super.queryAllUsers()
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun getPreInstalledSystemPackages(user: UserHandle): List<String> {
       return try {
           if (enablePrivateSpace() && privateSpaceSysAppsSeparation())
               mContext
                   .getSystemService(LauncherApps::class.java)!!
                   .getPreInstalledSystemPackages(user)
           else ArrayList()
       } catch (t: Throwable) {
           super.getPreInstalledSystemPackages(user)
       }
    }

    override fun getAppMarketActivityIntent(packageName: String, user: UserHandle): Intent {
        return try {
            if (allowPrivateProfile() && enablePrivateSpace())
                ProxyActivityStarter.getLaunchIntent(
                    mContext,
                    StartActivityParams(null as PendingIntent?, 0).apply {
                        intentSender =
                            mContext
                                .getSystemService(LauncherApps::class.java)!!
                                .getAppMarketActivityIntent(packageName, user)
                        options =
                            ActivityOptions.makeBasic()
                                .setPendingIntentBackgroundActivityStartMode(
                                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                )
                                .toBundle()
                        requireActivityResult = false
                    },
                )
            else super.getAppMarketActivityIntent(packageName, user)
        } catch (t: Throwable) {
            super.getAppMarketActivityIntent(packageName, user)
        }
    }

    /** Returns an intent which can be used to open Private Space Settings. */
    override fun getPrivateSpaceSettingsIntent(): Intent? {
        return try {
            if (allowPrivateProfile() && enablePrivateSpace())
                ProxyActivityStarter.getLaunchIntent(
                    mContext,
                    StartActivityParams(null as PendingIntent?, 0).apply {
                        intentSender =
                            mContext
                                .getSystemService(LauncherApps::class.java)
                                ?.privateSpaceSettingsIntent ?: return null
                        options =
                            ActivityOptions.makeBasic()
                                .setPendingIntentBackgroundActivityStartMode(
                                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                )
                                .toBundle()
                        requireActivityResult = false
                    }
                )
            else null
        } catch (t: Throwable) {
            super.privateSpaceSettingsIntent
        }
    }

    override fun isNonResizeableActivity(lai: LauncherActivityInfo): Boolean {
        return try {
            lai.activityInfo.resizeMode == ActivityInfo.RESIZE_MODE_UNRESIZEABLE
        } catch (t: Throwable) {
            super.isNonResizeableActivity(lai)
        }
    }

    override fun supportsMultiInstance(lai: LauncherActivityInfo): Boolean {
        return try {
            super.supportsMultiInstance(lai) || lai.supportsMultiInstance()
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Starts an Activity which can be used to set this Launcher as the HOME app, via a consent
     * screen. In case the consent screen cannot be shown, or the user does not set current Launcher
     * as HOME app, a toast asking the user to do the latter is shown.
     */
    override fun assignDefaultHomeRole(context: Context) {
        try {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (
                (roleManager!!.isRoleAvailable(RoleManager.ROLE_HOME) &&
                        !roleManager.isRoleHeld(RoleManager.ROLE_HOME))
            ) {
                val roleRequestIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                val pendingIntent =
                    PendingIntent(
                        object : IIntentSender.Stub() {
                            override fun send(
                                code: Int,
                                intent: Intent,
                                resolvedType: String?,
                                allowlistToken: IBinder?,
                                finishedReceiver: IIntentReceiver?,
                                requiredPermission: String?,
                                options: Bundle?,
                            ) {
                                if (code != -1) {
                                    Executors.MAIN_EXECUTOR.execute {
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                R.string.set_default_home_app,
                                                context.getString(R.string.derived_app_name),
                                            ),
                                            Toast.LENGTH_LONG,
                                        )
                                            .show()
                                    }
                                }
                            }
                        }
                    )
                val params = StartActivityParams(pendingIntent, 0)
                params.intent = roleRequestIntent
                context.startActivity(ProxyActivityStarter.getLaunchIntent(context, params))
            }
        } catch (t: Throwable) {
            super.assignDefaultHomeRole(context)
        }
    }

    override fun createStatusBarTouchController(
        launcher: BaseActivity,
        isEnabledCheck: Supplier<Boolean>,
    ): StatusBarTouchController? {
        return StatusBarTouchController(launcher, isEnabledCheck)
    }

    override fun isFileDrawable(shortcutInfo: ShortcutInfo) =
        shortcutInfo.hasIconFile() || shortcutInfo.hasIconUri()

    override fun captureSnapshot(host: SurfaceControlViewHost, width: Int, height: Int): Bitmap =
        ScreenCapture.captureLayers(
                ScreenCapture.LayerCaptureArgs.Builder(host.surfacePackage!!.surfaceControl)
                    .setSourceCrop(Rect(0, 0, width, height))
                    .setAllowProtected(true)
                    .setHintForSeamlessTransition(true)
                    .build()
            )
            .asBitmap()
            .copy(Bitmap.Config.ARGB_8888, true)
}
