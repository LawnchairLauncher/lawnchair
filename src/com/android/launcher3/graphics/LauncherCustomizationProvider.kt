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
package com.android.launcher3.graphics

import android.Manifest.permission.BIND_WALLPAPER
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import com.android.launcher3.BuildConfig.APPLICATION_ID
import com.android.launcher3.R
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.util.ContentProviderProxy
import java.security.MessageDigest

/** Provider for various Launcher customizations exposed via a ContentProvider API */
class LauncherCustomizationProvider : ContentProviderProxy() {

    override fun getProxy(ctx: Context): ProxyProvider? {
        // Check if the caller has access
        enforceCallerPermission(ctx)
        return ctx.appComponent.gridCustomizationsProxy
    }

    private fun enforceCallerPermission(ctx: Context) {
        if (ctx.checkCallingPermission(BIND_WALLPAPER) == PackageManager.PERMISSION_GRANTED) return
        if (ctx.checkCallingPermission(PERM_GRID_CONTROL) == PackageManager.PERMISSION_GRANTED)
            return

        // Temporary change until the clients migrate to the new permission
        val source = callingAttributionSource?.packageName ?: throw genericAccessException()
        val signatures =
            ctx.packageManager.getPackageInfo(source, PackageManager.GET_SIGNATURES)?.signatures
                ?: throw genericAccessException()
        val signature =
            MessageDigest.getInstance("SHA-256")
                .apply { signatures.forEach { update(it.toByteArray()) } }
                .digest()
                .joinToString("") { String.format("%02x", it) }
        if (ctx.resources.getStringArray(R.array.grid_control_known_signers).contains(signature))
            return
        throw genericAccessException()
    }

    private fun genericAccessException() =
        SecurityException(
            "Permission Denial: opening provider com.android.launcher3.graphics.LauncherCustomizationProvider (pid=${Binder.getCallingPid()}, uid=${Binder.getCallingUid()}) requires $BIND_WALLPAPER or $PERM_GRID_CONTROL"
        )

    override fun getType(uri: Uri) = "vnd.android.cursor.dir/launcher_grid"

    companion object {

        private const val PERM_GRID_CONTROL = "$APPLICATION_ID.permission.GRID_CONTROL"
    }
}
