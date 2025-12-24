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

package com.android.launcher3.homescreenfiles

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract.Document.MIME_TYPE_DIR
import com.android.launcher3.Flags.showFilesOnHomeScreen

/** Other utility methods related to managing files on the home screen. */
class HomeScreenFilesUtils {
    companion object {
        /** Returns `true` if the feature to show files on the home screen is enabled. */
        val isFeatureEnabled: Boolean by lazy {
            showFilesOnHomeScreen() && Environment.isExternalStorageManager()
        }

        /**
         * Creates an [Intent] to open [homeScreenFile] in the app associated with its MIME type.
         */
        fun buildLaunchIntent(uri: Uri, homeScreenFile: HomeScreenFile) =
            Intent(Intent.ACTION_VIEW).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                setDataAndType(
                    uri,
                    if (homeScreenFile.isDirectory) MIME_TYPE_DIR else homeScreenFile.mimeType,
                )
            }
    }
}
