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

package com.android.launcher3

import android.content.ComponentName
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Process.myUserHandle
import android.os.UserHandle
import android.util.Xml
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.AutoInstallsLayout.LayoutParserCallback
import com.android.launcher3.AutoInstallsLayout.SourceResources
import com.android.launcher3.AutoInstallsLayout.TAG_WORKSPACE
import com.android.launcher3.AutoInstallsLayout.USER_TYPE_WORK
import com.android.launcher3.LauncherSettings.Favorites.APPWIDGET_PROVIDER
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.INTENT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.LauncherSettings.Favorites.PROFILE_ID
import com.android.launcher3.LauncherSettings.Favorites.SPANX
import com.android.launcher3.LauncherSettings.Favorites.SPANY
import com.android.launcher3.LauncherSettings.Favorites._ID
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.ApiWrapper
import com.android.launcher3.util.Executors
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.LauncherModelHelper
import com.android.launcher3.util.LauncherModelHelper.SandboxModelContext
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.UserIconInfo
import com.android.launcher3.util.UserIconInfo.TYPE_MAIN
import com.android.launcher3.util.UserIconInfo.TYPE_WORK
import com.android.launcher3.widget.LauncherWidgetHolder
import com.google.common.truth.Truth.assertThat
import java.io.StringReader
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

/** Tests for [AutoInstallsLayout] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class AutoInstallsLayoutTest {

    lateinit var modelHelper: LauncherModelHelper
    lateinit var targetContext: SandboxModelContext

    lateinit var callback: MyCallback

    @Mock lateinit var widgetHolder: LauncherWidgetHolder
    @Mock lateinit var db: SQLiteDatabase

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        modelHelper = LauncherModelHelper()
        targetContext = modelHelper.sandboxContext
        callback = MyCallback()
    }

    @After
    fun tearDown() {
        modelHelper.destroy()
    }

    @Test
    fun pending_icon_added_on_home() {
        LauncherLayoutBuilder()
            .atWorkspace(1, 1, 0)
            .putApp("p1", "c1")
            .toAutoInstallsLayout()
            .loadLayout(db)

        assertThat(callback.items.size).isEqualTo(1)
        assertThat(callback.items[0][ITEM_TYPE]).isEqualTo(ITEM_TYPE_APPLICATION)
        assertThat(callback.items[0][INTENT])
            .isEqualTo(AppInfo.makeLaunchIntent(ComponentName("p1", "c1")).toUri(0))
        assertThat(callback.items[0][CONTAINER]).isEqualTo(CONTAINER_DESKTOP)
        assertThat(callback.items[0].containsKey(PROFILE_ID)).isFalse()
    }

    @Test
    fun pending_icon_added_on_hotseat() {
        LauncherLayoutBuilder()
            .atHotseat(1)
            .putApp("p1", "c1")
            .toAutoInstallsLayout()
            .loadLayout(db)

        assertThat(callback.items.size).isEqualTo(1)
        assertThat(callback.items[0][ITEM_TYPE]).isEqualTo(ITEM_TYPE_APPLICATION)
        assertThat(callback.items[0][CONTAINER]).isEqualTo(CONTAINER_HOTSEAT)
    }

    @Test
    fun widget_added_to_home() {
        LauncherLayoutBuilder()
            .atWorkspace(1, 1, 0)
            .putWidget("p1", "c1", 2, 3)
            .toAutoInstallsLayout()
            .loadLayout(db)

        assertThat(callback.items.size).isEqualTo(1)
        assertThat(callback.items[0][ITEM_TYPE]).isEqualTo(ITEM_TYPE_APPWIDGET)
        assertThat(callback.items[0][CONTAINER]).isEqualTo(CONTAINER_DESKTOP)
        assertThat(callback.items[0][APPWIDGET_PROVIDER])
            .isEqualTo(ComponentName("p1", "c1").flattenToString())
        assertThat(callback.items[0][SPANX]).isEqualTo(2.toString())
        assertThat(callback.items[0][SPANY]).isEqualTo(3.toString())
    }

    @Test
    fun items_added_to_folder() {
        LauncherLayoutBuilder()
            .atHotseat(1)
            .putFolder("Test")
            .addApp("p1", "c")
            .addApp("p2", "c")
            .addApp("p3", "c")
            .build()
            .toAutoInstallsLayout()
            .loadLayout(db)

        assertThat(callback.items.size).isEqualTo(4)
        assertThat(callback.items[0][ITEM_TYPE]).isEqualTo(ITEM_TYPE_FOLDER)
        assertThat(callback.items[0][CONTAINER]).isEqualTo(CONTAINER_HOTSEAT)

        val folderId = callback.items[0][_ID]
        assertThat(callback.items[1][CONTAINER]).isEqualTo(folderId)
        assertThat(callback.items[2][CONTAINER]).isEqualTo(folderId)
        assertThat(callback.items[3][CONTAINER]).isEqualTo(folderId)
    }

    @Test
    fun work_item_added_to_home() {
        val apiWrapperMock = spy(ApiWrapper.INSTANCE[targetContext])
        targetContext.putObject(ApiWrapper.INSTANCE, apiWrapperMock)
        doReturn(
                mapOf(
                    myUserHandle() to UserIconInfo(myUserHandle(), TYPE_MAIN, 0),
                    UserHandle.of(20) to UserIconInfo(UserHandle.of(20), TYPE_WORK, 20),
                )
            )
            .whenever(apiWrapperMock)
            .queryAllUsers()

        val cache = UserCache.getInstance(targetContext)
        TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {
            assertThat(cache.userProfiles.size).isEqualTo(2)
        }

        LauncherLayoutBuilder()
            .atWorkspace(1, 1, 0)
            .putApp("p1", "c1", USER_TYPE_WORK)
            .toAutoInstallsLayout()
            .loadLayout(db)

        assertThat(callback.items.size).isEqualTo(1)
        assertThat(callback.items[0][ITEM_TYPE]).isEqualTo(ITEM_TYPE_APPLICATION)
        assertThat(callback.items[0][INTENT])
            .isEqualTo(AppInfo.makeLaunchIntent(ComponentName("p1", "c1")).toUri(0))
        assertThat(callback.items[0][CONTAINER]).isEqualTo(CONTAINER_DESKTOP)
        assertThat(callback.items[0][PROFILE_ID]).isEqualTo(20)
    }

    private fun LauncherLayoutBuilder.toAutoInstallsLayout() =
        AutoInstallsLayout(
            targetContext,
            widgetHolder,
            callback,
            SourceResources.wrap(targetContext.resources),
            { Xml.newPullParser().also { it.setInput(StringReader(build())) } },
            TAG_WORKSPACE
        )

    class MyCallback : LayoutParserCallback {

        val items = ArrayList<ContentValues>()

        override fun generateNewItemId() = items.size

        override fun insertAndCheck(db: SQLiteDatabase?, values: ContentValues): Int {
            val id = values[_ID]
            items.add(ContentValues(values))
            return if (id is Int) id else 0
        }
    }
}
