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

import android.app.blob.BlobStoreManager
import android.os.ParcelFileDescriptor
import android.os.ParcelFileDescriptor.MODE_READ_WRITE
import android.provider.Settings.Secure
import com.android.launcher3.LauncherSettings.Settings
import com.android.launcher3.LauncherSettings.Settings.LAYOUT_PROVIDER_KEY
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import java.io.File
import java.io.FileWriter
import java.security.MessageDigest
import org.junit.rules.ExternalResource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever

/** Rule for providing default model layout */
class LayoutProviderRule(private val ctx: SandboxApplication) : ExternalResource() {

    override fun before() {
        TestUtil.grantWriteSecurePermission()
    }

    fun setupDefaultLayoutProvider(builder: LauncherLayoutBuilder) {
        val file = File.createTempFile("blobsession", "tmp")
        FileWriter(file).use { builder.build(it) }

        val blobManager = ctx.spyService(BlobStoreManager::class.java)
        doAnswer { ParcelFileDescriptor.open(file, MODE_READ_WRITE) }
            .whenever(blobManager)
            .openBlob(any())

        Secure.putString(
            ctx.getContentResolver(),
            LAYOUT_PROVIDER_KEY,
            Settings.createBlobProviderKey(
                MessageDigest.getInstance("SHA-256").digest(byteArrayOf(1, 1, 1))
            ),
        )
    }

    override fun after() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            Secure.putString(ctx.getContentResolver(), LAYOUT_PROVIDER_KEY, "")
        }
    }
}
