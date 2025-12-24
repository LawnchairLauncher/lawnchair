/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.systemui.biometrics

import android.Manifest
import android.app.ActivityTaskManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Insets
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.PromptInfo
import android.hardware.biometrics.SensorPropertiesInternal
import android.os.UserManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import com.android.internal.widget.LockPatternUtils
import com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PASSWORD
import com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PATTERN
import com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PIN
import com.android.systemui.biometrics.shared.model.PromptKind
import com.android.systemui.utils.windowmanager.WindowManagerUtils

object Utils {
    private const val TAG = "SysUIBiometricUtils"

    /** Base set of layout flags for fingerprint overlay widgets. */
    const val FINGERPRINT_OVERLAY_LAYOUT_PARAM_FLAGS =
        (WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)

    @JvmStatic
    fun dpToPixels(context: Context, dp: Float): Float {
        val density = context.resources.displayMetrics.densityDpi.toFloat()
        return dp * (density / DisplayMetrics.DENSITY_DEFAULT)
    }

    /**
     * Note: Talkback 14.0 has new rate-limitation design to reduce frequency of
     * TYPE_WINDOW_CONTENT_CHANGED events to once every 30 seconds. (context: b/281765653#comment18)
     * Using {@link View#announceForAccessibility} instead as workaround when sending events
     * exceeding this frequency is required.
     */
    @JvmStatic
    fun notifyAccessibilityContentChanged(am: AccessibilityManager, view: ViewGroup) {
        if (!am.isEnabled) {
            return
        }
        val event = AccessibilityEvent.obtain()
        event.eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        event.contentChangeTypes = AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
        view.sendAccessibilityEventUnchecked(event)
        view.notifySubtreeAccessibilityStateChanged(
            view,
            view,
            AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE,
        )
    }

    @JvmStatic
    fun isDeviceCredentialAllowed(promptInfo: PromptInfo): Boolean =
        (promptInfo.authenticators and Authenticators.DEVICE_CREDENTIAL) != 0

    @JvmStatic
    fun isBiometricAllowed(promptInfo: PromptInfo): Boolean =
        (promptInfo.authenticators and Authenticators.BIOMETRIC_WEAK) != 0

    @JvmStatic
    fun getCredentialType(utils: LockPatternUtils, userId: Int): PromptKind =
        when (utils.getCredentialTypeForUser(userId)) {
            CREDENTIAL_TYPE_PATTERN -> PromptKind.Pattern
            CREDENTIAL_TYPE_PIN -> PromptKind.Pin
            CREDENTIAL_TYPE_PASSWORD -> PromptKind.Password
            else -> PromptKind.Password
        }

    @JvmStatic
    fun isManagedProfile(context: Context, userId: Int): Boolean =
        context.getSystemService(UserManager::class.java)?.isManagedProfile(userId) ?: false

    @JvmStatic
    fun isSupervisingProfile(context: Context, userId: Int): Boolean =
        context.getSystemService(UserManager::class.java)?.getUserInfo(userId)?.userType ==
            UserManager.USER_TYPE_PROFILE_SUPERVISING

    @JvmStatic
    fun <T : SensorPropertiesInternal> findFirstSensorProperties(
        properties: List<T>?,
        sensorIds: IntArray,
    ): T? = properties?.firstOrNull { sensorIds.contains(it.sensorId) }

    @JvmStatic
    fun isSystem(context: Context, clientPackage: String?): Boolean {
        val hasPermission =
            (context.checkCallingOrSelfPermission(Manifest.permission.USE_BIOMETRIC_INTERNAL) ==
                PackageManager.PERMISSION_GRANTED)
        return hasPermission && "android" == clientPackage
    }

    @JvmStatic
    fun getNavbarInsets(context: Context) = getInsetsOf(context, WindowInsets.Type.navigationBars())

    @JvmStatic
    fun getInsetsOf(context: Context, typeMask: Int): Insets {
        val windowManager: WindowManager = WindowManagerUtils.getWindowManager(context)
        val windowMetrics: WindowMetrics = windowManager.maximumWindowMetrics
        return windowMetrics.windowInsets.getInsets(typeMask)
    }

    /** Converts `drawable` to a [Bitmap]. */
    @JvmStatic
    fun Drawable?.toBitmap(): Bitmap? {
        if (this == null) {
            return null
        }
        if (this is BitmapDrawable) {
            return bitmap
        }
        val bitmap: Bitmap =
            if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                // Single color bitmap will be created of 1x1 pixel
            } else {
                Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
            }
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }

    @JvmStatic
    fun String.ellipsize(cutOffLength: Int) =
        if (length <= cutOffLength) this else replaceRange(cutOffLength, length, "...")

    // LINT.IfChange
    @JvmStatic
    /**
     * Checks if a client package is running in the background or it's a system app.
     *
     * @param clientPackage The name of the package to be checked.
     * @param clientClassNameIfItIsConfirmDeviceCredentialActivity The class name of
     *   ConfirmDeviceCredentialActivity.
     * @return Whether the client package is running in background
     */
    fun ActivityTaskManager.isSystemAppOrInBackground(
        context: Context,
        clientPackage: String,
        clientClassNameIfItIsConfirmDeviceCredentialActivity: String?,
    ): Boolean {
        // TODO (b/409812027): Consolidate and scope out auth requirements for biometric prompt

        Log.v(TAG, "Checking if the authenticating is in background, clientPackage:$clientPackage")
        val tasks = getTasks(Int.MAX_VALUE)
        if (tasks == null || tasks.isEmpty()) {
            Log.w(TAG, "No running tasks reported")
            return false
        }

        val topActivity = tasks[0].topActivity
        val isSystemApp = isSystem(context, clientPackage)
        val topPackageEqualsToClient = topActivity!!.packageName == clientPackage
        val isClientConfirmDeviceCredentialActivity =
            clientClassNameIfItIsConfirmDeviceCredentialActivity != null

        if (
            !isClientInBackgroundOrNotVisible(
                isSystemApp,
                isVisible = true,
                topPackageEqualsToClient,
                isClientConfirmDeviceCredentialActivity,
                clientClassNameIfItIsConfirmDeviceCredentialActivity,
                topActivity!!.className,
                isTopPackage = true,
            )
        ) {
            return false
        }

        for (task in tasks) {
            val packageName = task.topActivity!!.packageName
            val className = task.topActivity!!.className
            val isVisible = task.isVisible

            Log.v(TAG, "Running task, top: $packageName, isVisible: $isVisible")

            if (
                !isClientInBackgroundOrNotVisible(
                    isSystemApp = false,
                    isVisible,
                    taskPackageEqualsClientPackage = packageName == clientPackage,
                    isClientConfirmDeviceCredentialActivity,
                    clientClassNameIfItIsConfirmDeviceCredentialActivity,
                    className,
                )
            ) {
                return false
            }
        }

        return true
    }

    fun isClientInBackgroundOrNotVisible(
        isSystemApp: Boolean,
        isVisible: Boolean,
        taskPackageEqualsClientPackage: Boolean,
        isClientConfirmDeviceCredentialActivity: Boolean,
        clientClassNameIfItIsConfirmDeviceCredentialActivity: String?,
        topActivityClassName: String,
        isTopPackage: Boolean = false,
    ): Boolean {

        if (isVisible) {
            if (isSystemApp || taskPackageEqualsClientPackage) {
                // b/339532378: If it's ConfirmDeviceCredentialActivity, we need to check further on
                // class name.
                if (isClientConfirmDeviceCredentialActivity) {
                    return !isTopPackage ||
                        clientClassNameIfItIsConfirmDeviceCredentialActivity != topActivityClassName
                }
                return false
            } else if (
                isTopPackage &&
                    isClientConfirmDeviceCredentialActivity &&
                    clientClassNameIfItIsConfirmDeviceCredentialActivity == topActivityClassName
            ) {
                return false
            }
        }

        return true
    }
    // LINT.ThenChange(frameworks/base/services/core/java/com/android/server/biometrics/Utils.java)
}
