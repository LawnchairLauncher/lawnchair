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

import android.net.Uri
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider.FileChange
import com.android.launcher3.util.MutableListenableStream
import java.util.concurrent.CompletableFuture

/** No-op implementation of [HomeScreenFilesProvider]. */
class HomeScreenFilesNoOpProvider : HomeScreenFilesProvider {
    override val fileChanges = MutableListenableStream<FileChange>()

    override fun canMoveToHomeScreen(uriList: List<Uri>?) = false

    override fun moveToHomeScreen(uriList: List<Uri>): List<CompletableFuture<Boolean>> =
        uriList.map { CompletableFuture.supplyAsync { false } }

    override fun query(): Lazy<Map<Uri, HomeScreenFile>> {
        return lazyOf(emptyMap())
    }
}
