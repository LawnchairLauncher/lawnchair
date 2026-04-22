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

package com.android.launcher3.logging

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.logging.DumpManager.LauncherDumpable
import java.io.PrintWriter
import java.io.StringWriter
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class DumpManagerTest {

    private val dumpManager = DumpManager()

    @Test
    fun `dump called on dumpable`() {
        val dumpable1 = mock<LauncherDumpable>()
        val dumpable2 = mock<LauncherDumpable>()
        dumpManager.register(dumpable1)
        dumpManager.register(dumpable2)

        dumpManager.dump("prefix", PrintWriter(StringWriter()), null)
        verify(dumpable1, times(1)).dump(eq("prefix"), any(), anyOrNull())
        verify(dumpable2, times(1)).dump(eq("prefix"), any(), anyOrNull())
    }

    @Test
    fun `unregister doesnot call dump`() {
        val dumpable = mock<LauncherDumpable>()
        val closeAction = dumpManager.register(dumpable)
        dumpManager.dump("prefix", PrintWriter(StringWriter()), null)
        verify(dumpable, times(1)).dump(anyOrNull(), anyOrNull(), anyOrNull())
        reset(dumpable)

        closeAction.close()
        dumpManager.dump("prefix", PrintWriter(StringWriter()), null)
        verify(dumpable, never()).dump(anyOrNull(), anyOrNull(), anyOrNull())
    }
}
