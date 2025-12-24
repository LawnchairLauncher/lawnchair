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

package com.android.launcher3.util

import android.R
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY2
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

private const val PKG = "com.android.launcher3"

@SmallTest
@RunWith(AndroidJUnit4::class)
class LauncherLayoutBuilderTest {

    @Test
    fun appOnHotseat() {
        val xml: String =
            LauncherLayoutBuilder().atHotseat(0).putApp(PKG, TEST_ACTIVITY).build()
        assertEquals(
            "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\r\n" +
                "<workspace>\r\n" +
                "  <autoinstall container=\"hotseat\" rank=\"0\" className=\"$TEST_ACTIVITY\" packageName=\"$PKG\" />\r\n" +
                "</workspace>",
            xml,
        )
    }

    @Test
    fun widgetOnWorkspace() {
        val xml: String =
            LauncherLayoutBuilder()
                .atWorkspace(0, 1, 0)
                .putWidget("com.test.pending", "PlaceholderWidget", 2, 2)
                .build()
        assertEquals(
            "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\r\n" +
                "<workspace>\r\n" +
                "  <appwidget container=\"desktop\" spanX=\"2\" spanY=\"2\" x=\"0\" y=\"1\" screen=\"0\" className=\"PlaceholderWidget\" packageName=\"com.test.pending\" />\r\n" +
                "</workspace>",
            xml,
        )
    }

    @Test
    fun deepShortcutOnHotseat() {
        val xml: String =
            LauncherLayoutBuilder()
                .atHotseat(0)
                .putFolder("folder")
                .addApp(PKG, TEST_ACTIVITY)
                .addApp(PKG, TEST_ACTIVITY)
                .addShortcut(PKG, "shortcut2")
                .build()
                .build()
        assertEquals(
            "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\r\n" +
                "<workspace>\r\n" +
                "  <folder container=\"hotseat\" rank=\"0\" titleText=\"folder\">\r\n" +
                "    <autoinstall className=\"$TEST_ACTIVITY\" packageName=\"$PKG\" />\r\n" +
                "    <autoinstall className=\"$TEST_ACTIVITY\" packageName=\"$PKG\" />\r\n" +
                "    <shortcut shortcutId=\"shortcut2\" packageName=\"$PKG\" />\r\n" +
                "  </folder>\r\n" +
                "</workspace>",
            xml,
        )
    }

    @Test
    fun appOnWorkspace() {
        val xml: String =
            LauncherLayoutBuilder().atWorkspace(1, 1, 0).putApp(PKG, TEST_ACTIVITY).build()
        assertEquals(
            "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\r\n" +
                "<workspace>\r\n" +
                "  <autoinstall container=\"desktop\" x=\"1\" y=\"1\" screen=\"0\" className=\"$TEST_ACTIVITY\" packageName=\"$PKG\" />\r\n" +
                "</workspace>",
            xml,
        )
    }

    @Test
    fun appPairOnWorkspace() {
        val xml: String =
            LauncherLayoutBuilder()
                .atWorkspace(1, 1, 1)
                .putAppPair("CustomAppPair")
                .addApp(PKG, TEST_ACTIVITY)
                .addApp(PKG, TEST_ACTIVITY2)
                .build()
                .build()
        assertEquals(
            "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\r\n" +
                "<workspace>\r\n" +
                "  <apppair container=\"desktop\" x=\"1\" y=\"1\" screen=\"1\" titleText=\"CustomAppPair\">\r\n" +
                "    <autoinstall className=\"$TEST_ACTIVITY\" packageName=\"$PKG\" />\r\n" +
                "    <autoinstall className=\"$TEST_ACTIVITY2\" packageName=\"$PKG\" />\r\n" +
                "  </apppair>\r\n" +
                "</workspace>",
            xml,
        )
    }

    @Test
    fun folderAndContentsOnHotseat() {
        val xml: String =
            LauncherLayoutBuilder()
                .atHotseat(0)
                .putFolder(R.string.copy)
                .addApp(PKG, TEST_ACTIVITY)
                .addApp(PKG, TEST_ACTIVITY)
                .addApp(PKG, TEST_ACTIVITY)
                .build()
                .build()
        assertEquals(
            "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\r\n" +
                "<workspace>\r\n" +
                "  <folder container=\"hotseat\" title=\"17039361\" rank=\"0\">\r\n" +
                "    <autoinstall className=\"$TEST_ACTIVITY\" packageName=\"$PKG\" />\r\n" +
                "    <autoinstall className=\"$TEST_ACTIVITY\" packageName=\"$PKG\" />\r\n" +
                "    <autoinstall className=\"$TEST_ACTIVITY\" packageName=\"$PKG\" />\r\n" +
                "  </folder>\r\n" +
                "</workspace>",
            xml,
        )
    }

    @Test
    fun folderInFolderFails() {
        assertThrows(IllegalArgumentException::class.java) {
            LauncherLayoutBuilder()
                .atWorkspace(1, 1, 1)
                .putFolder("OuterFolder")
                .putFolder("InnerFolder")
        }
    }

    @Test
    fun appPairInAppPairFails() {
        assertThrows(IllegalArgumentException::class.java) {
            LauncherLayoutBuilder()
                .atWorkspace(1, 1, 1)
                .putAppPair("OuterAppPair")
                .putAppPair("InnerAppPair")
        }
    }

    @Test
    fun moreThan2ItemAppPairFails() {
        assertThrows(IllegalStateException::class.java) {
            LauncherLayoutBuilder()
                .atWorkspace(1, 1, 1)
                .putAppPair("OuterAppPair")
                .addApp(PKG, TEST_ACTIVITY)
                .addApp(PKG, TEST_ACTIVITY2)
                .addApp(PKG, TEST_ACTIVITY3)
        }
    }
}
