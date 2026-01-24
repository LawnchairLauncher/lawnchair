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

package com.android.launcher3.util.rule

import android.content.Context
import android.content.pm.PackageInstaller.SessionParams
import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import com.android.launcher3.util.RunnableList
import org.junit.rules.ExternalResource

/** Rule for creating package installer sessions */
class InstallerSessionRule
@JvmOverloads
constructor(private val ctx: Context = getApplicationContext()) : ExternalResource() {

    private val cleanupActions = RunnableList()

    /** Creates a installer session for the provided package. */
    fun createInstallerSession(pkg: String): Int {
        val sp = SessionParams(SessionParams.MODE_FULL_INSTALL)
        sp.setAppPackageName(pkg)
        val icon = Bitmap.createBitmap(100, 100, ARGB_8888)
        icon.eraseColor(Color.RED)
        sp.setAppIcon(icon)
        sp.setAppLabel(pkg)
        sp.setInstallerPackageName(ctx.packageName)
        val pi = ctx.packageManager.packageInstaller
        val sessionId = pi.createSession(sp)
        cleanupActions.add { pi.abandonSession(sessionId) }
        return sessionId
    }

    override fun after() = cleanupActions.executeAllAndDestroy()
}
