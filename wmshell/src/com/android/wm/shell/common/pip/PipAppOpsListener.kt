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
package com.android.wm.shell.common.pip

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Pair
import com.android.internal.annotations.VisibleForTesting
import com.android.wm.shell.common.ShellExecutor

class PipAppOpsListener(
    private val mContext: Context,
    private val mMainExecutor: ShellExecutor
) {
    private val mAppOpsManager: AppOpsManager = checkNotNull(
        mContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
    private var mTopPipActivityInfoSupplier: (Context) -> Pair<ComponentName?, Int> =
        PipUtils::getTopPipActivity
    private val mAppOpsChangedListener = AppOpsManager.OnOpChangedListener { _, packageName ->
        try {
            // Dismiss the PiP once the user disables the app ops setting for that package
            val topPipActivityInfo = mTopPipActivityInfoSupplier.invoke(mContext)
            val componentName = topPipActivityInfo.first ?: return@OnOpChangedListener
            val userId = topPipActivityInfo.second
            val appInfo = mContext.packageManager
                .getApplicationInfoAsUser(packageName, 0, userId)
            if (appInfo.packageName == componentName.packageName &&
                mAppOpsManager.checkOpNoThrow(
                    AppOpsManager.OP_PICTURE_IN_PICTURE, appInfo.uid,
                    packageName
                ) != AppOpsManager.MODE_ALLOWED
            ) {
                mCallback?.let {
                    mMainExecutor.execute { it.dismissPip() }
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            // Unregister the listener if the package can't be found
            unregisterAppOpsListener()
        }
    }

    private var mCallback: Callback? = null

    fun setCallback(callback: Callback) {
        mCallback = callback
    }

    fun onActivityPinned(packageName: String) {
        // Register for changes to the app ops setting for this package while it is in PiP
        registerAppOpsListener(packageName)
    }

    fun onActivityUnpinned() {
        // Unregister for changes to the previously PiP'ed package
        unregisterAppOpsListener()
    }

    private fun registerAppOpsListener(packageName: String) {
        mAppOpsManager.startWatchingMode(
            AppOpsManager.OP_PICTURE_IN_PICTURE, packageName,
            mAppOpsChangedListener
        )
    }

    private fun unregisterAppOpsListener() {
        mAppOpsManager.stopWatchingMode(mAppOpsChangedListener)
    }

    /** Callback for PipAppOpsListener to request changes to the PIP window.  */
    interface Callback {
        /** Dismisses the PIP window.  */
        fun dismissPip()
    }

    @VisibleForTesting
    fun setTopPipActivityInfoSupplier(supplier: (Context) -> Pair<ComponentName?, Int>) {
        mTopPipActivityInfoSupplier = supplier
    }
}