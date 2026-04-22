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

package com.android.launcher3.util

import android.content.ContentResolver
import android.net.Uri
import android.os.Looper
import java.io.InputStream
import java.util.function.Supplier
import org.robolectric.Shadows

object RoboApiWrapper {

    fun registerInputStream(
        contentResolver: ContentResolver,
        uri: Uri,
        inputStreamSupplier: Supplier<InputStream>,
    ) {
        Shadows.shadowOf(contentResolver).registerInputStreamSupplier(uri, inputStreamSupplier)
    }

    fun waitForLooperSync(looper: Looper) {
        Shadows.shadowOf(looper).runToEndOfTasks()
    }
}
