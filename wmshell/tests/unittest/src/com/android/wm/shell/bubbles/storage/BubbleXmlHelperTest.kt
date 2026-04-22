/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.bubbles.storage

import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.testing.AndroidTestingRunner
import android.util.SparseArray
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@SmallTest
@RunWith(AndroidTestingRunner::class)
class BubbleXmlHelperTest : ShellTestCase() {

    private val user0Bubbles = listOf(
            BubbleEntity(0, "com.example.messenger", "shortcut-1", "0k1", 120, 0, null, 1,
                    isDismissable = true),
            BubbleEntity(10, "com.example.chat", "alice and bob", "0k2", 0, 16537428, "title", 2,
                    null),
            BubbleEntity(0, "com.example.messenger", "shortcut-2", "0k3", 120, 0, null,
                    INVALID_TASK_ID, "l3")
    )

    private val user1Bubbles = listOf(
            BubbleEntity(1, "com.example.messenger", "shortcut-1", "1k1", 120, 0, null, 3,
                    isDismissable = true),
            BubbleEntity(12, "com.example.chat", "alice and bob", "1k2", 0, 16537428, "title", 4,
                    null),
            BubbleEntity(1, "com.example.messenger", "shortcut-2", "1k3", 120, 0, null,
                    INVALID_TASK_ID, "l4")
    )

    private val bubbles = SparseArray<List<BubbleEntity>>()

    @Before
    fun setup() {
        bubbles.put(0, user0Bubbles)
        bubbles.put(1, user1Bubbles)
    }

    @Test
    fun testWriteXml() {
        val expectedEntries = """
<bs uid="0">
<bb uid="0" pkg="com.example.messenger" sid="shortcut-1" key="0k1" h="120" hid="0" tid="1" d="true" />
<bb uid="10" pkg="com.example.chat" sid="alice and bob" key="0k2" h="0" hid="16537428" t="title" tid="2" d="false" />
<bb uid="0" pkg="com.example.messenger" sid="shortcut-2" key="0k3" h="120" hid="0" tid="-1" l="l3" d="false" />
</bs>
<bs uid="1">
<bb uid="1" pkg="com.example.messenger" sid="shortcut-1" key="1k1" h="120" hid="0" tid="3" d="true" />
<bb uid="12" pkg="com.example.chat" sid="alice and bob" key="1k2" h="0" hid="16537428" t="title" tid="4" d="false" />
<bb uid="1" pkg="com.example.messenger" sid="shortcut-2" key="1k3" h="120" hid="0" tid="-1" l="l4" d="false" />
</bs>
        """.trimIndent()
        ByteArrayOutputStream().use {
            writeXml(it, bubbles)
            val actual = it.toString()
            assertTrue("cannot find expected entry in \n$actual",
                    actual.contains(expectedEntries))
        }
    }

    @Test
    fun testReadXml() {
        val src = """
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<bs v="2">
<bs uid="0">
<bb uid="0" pkg="com.example.messenger" sid="shortcut-1" key="0k1" h="120" hid="0" tid="1" d="true" />
<bb uid="10" pkg="com.example.chat" sid="alice and bob" key="0k2" h="0" hid="16537428" t="title" tid="2" d="false" />
<bb uid="0" pkg="com.example.messenger" sid="shortcut-2" key="0k3" h="120" hid="0" tid="-1" l="l3" d="false" />
</bs>
<bs uid="1">
<bb uid="1" pkg="com.example.messenger" sid="shortcut-1" key="1k1" h="120" hid="0" tid="3" d="true" />
<bb uid="12" pkg="com.example.chat" sid="alice and bob" key="1k2" h="0" hid="16537428" t="title" tid="4" d="false" />
<bb uid="1" pkg="com.example.messenger" sid="shortcut-2" key="1k3" h="120" hid="0" tid="-1" l="l4" d="false" />
</bs>
</bs>
        """.trimIndent()
        val actual = readXml(ByteArrayInputStream(src.toByteArray(Charsets.UTF_8)))
        assertTrue("failed parsing bubbles from xml\n$src", bubbles.contentEquals(actual))
    }

    // V0 -> V1 happened prior to release / during dogfood so nothing is saved
    @Test
    fun testUpgradeFromV0DropsPreviousData() {
        val src = """
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<bs>
<bb uid="0" pkg="com.example.messenger" sid="shortcut-1" key="k1" h="120" hid="0" />
<bb uid="10" pkg="com.example.chat" sid="alice and bob" key="k2" h="0" hid="16537428" t="title" />
<bb uid="0" pkg="com.example.messenger" sid="shortcut-2" key="k3" h="120" hid="0" />
</bs>
        """.trimIndent()
        val actual = readXml(ByteArrayInputStream(src.toByteArray(Charsets.UTF_8)))
        assertEquals("failed parsing bubbles from xml\n$src", 0, actual.size())
    }

    /**
     * In S we changed the XML to include a taskId, version didn't increase because we can set a
     * reasonable default for taskId (INVALID_TASK_ID) if it wasn't in the XML previously, this
     * tests that that works.
     */
    @Test
    fun testReadXMLWithoutTaskId() {
        val expectedBubbles = SparseArray<List<BubbleEntity>>()
        expectedBubbles.put(0, listOf(
                        BubbleEntity(0, "com.example.messenger", "shortcut-1", "k1", 120, 0,
                                null, INVALID_TASK_ID),
                        BubbleEntity(0, "com.example.messenger", "shortcut-2", "k3", 120, 0,
                                null, INVALID_TASK_ID))
                )
        val src = """
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<bs v="2">
<bs uid="0">
<bb uid="0" pkg="com.example.messenger" sid="shortcut-1" key="k1" h="120" hid="0" />
<bb uid="0" pkg="com.example.messenger" sid="shortcut-2" key="k3" h="120" hid="0" />
</bs>
</bs>
        """.trimIndent()
        val actual = readXml(ByteArrayInputStream(src.toByteArray(Charsets.UTF_8)))
        assertTrue("failed parsing bubbles from xml\n$src", expectedBubbles.contentEquals(actual))
    }

    /**
     * LocusId is optional so it can be added without a version change, this test makes sure that
     * works.
     */
    @Test
    fun testXMLWithoutLocusToLocus() {
        val expectedBubbles = SparseArray<List<BubbleEntity>>()
        expectedBubbles.put(0, listOf(
                BubbleEntity(0, "com.example.messenger", "shortcut-1", "k1", 120, 0,
                        null, INVALID_TASK_ID),
                BubbleEntity(0, "com.example.messenger", "shortcut-2", "k3", 120, 0,
                        null, INVALID_TASK_ID))
        )
        val src = """
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<bs v="1">
<bb uid="0" pkg="com.example.messenger" sid="shortcut-1" key="k1" h="120" hid="0" />
<bb uid="0" pkg="com.example.messenger" sid="shortcut-2" key="k3" h="120" hid="0" />
</bs>
        """.trimIndent()
        val actual = readXml(ByteArrayInputStream(src.toByteArray(Charsets.UTF_8)))
        assertTrue("failed parsing bubbles from xml\n$src",
                expectedBubbles.contentEquals(actual))
    }

    @Test
    fun testUpgradeToV2SavesPreviousData() {
        val src = """
 <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
 <bs v="1">
 <bb uid="0" pkg="com.example.messenger" sid="shortcut-1" key="k1" h="120" hid="0" />
 <bb uid="10" pkg="com.example.chat" sid="alice and bob" key="k2" h="0" hid="16537428" t="title" />
 <bb uid="2" pkg="com.example.messenger" sid="shortcut-2" key="k3" h="120" hid="0" />
  <bb uid="0" pkg="com.example.messenger" sid="shortcut-4" key="k4" h="0" hid="16537428" />
 </bs>
        """.trimIndent()
        val expectedBubbles = SparseArray<List<BubbleEntity>>()
        expectedBubbles.put(0, listOf(
                BubbleEntity(0, "com.example.messenger", "shortcut-1", "k1", 120, 0,
                        null, INVALID_TASK_ID, null),
                BubbleEntity(0, "com.example.messenger", "shortcut-4", "k4", 0, 16537428,
                        null, INVALID_TASK_ID, null))
        )
        val actual = readXml(ByteArrayInputStream(src.toByteArray(Charsets.UTF_8)))
        assertTrue("failed parsing bubbles from xml\n$src",
                expectedBubbles.contentEquals(actual))
    }
}