package com.android.launcher3

import android.appwidget.AppWidgetManager.ACTION_APPWIDGET_DELETED
import android.appwidget.AppWidgetManager.ACTION_APPWIDGET_HOST_RESTORED
import android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS
import android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_OLD_IDS
import android.appwidget.AppWidgetManager.EXTRA_HOST_ID
import android.content.Intent
import android.platform.uiautomator_helpers.DeviceHelpers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.LauncherPrefs.Companion.APP_WIDGET_IDS
import com.android.launcher3.LauncherPrefs.Companion.OLD_APP_WIDGET_IDS
import com.android.launcher3.util.IntArray
import com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE
import com.android.launcher3.widget.LauncherWidgetHolder.APPWIDGET_HOST_ID
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [AppWidgetsRestoredReceiver] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class AppWidgetsRestoredReceiverTest {
    private lateinit var launcherPrefs: LauncherPrefs
    private lateinit var receiverUnderTest: AppWidgetsRestoredReceiver

    @Before
    fun setup() {
        launcherPrefs = LauncherPrefs(DeviceHelpers.context)
        receiverUnderTest = AppWidgetsRestoredReceiver()
    }

    @After
    fun tearDown() {
        launcherPrefs.remove(OLD_APP_WIDGET_IDS, APP_WIDGET_IDS)
    }

    @Test
    fun `When AppWidgetsRestoredReceiver gets valid broadcast it sets old and new app widget ids`() {
        // Given
        val oldIds = intArrayOf(1, 2, 10)
        val newIds = intArrayOf(10, 11, 12)
        val expectedOldIds = IntArray.wrap(*oldIds).toConcatString()
        val expectedNewIds = IntArray.wrap(*newIds).toConcatString()
        val intent =
            Intent().apply {
                component = null
                `package` = TEST_PACKAGE
                action = ACTION_APPWIDGET_HOST_RESTORED
                putExtra(EXTRA_APPWIDGET_OLD_IDS, oldIds)
                putExtra(EXTRA_APPWIDGET_IDS, newIds)
                putExtra(EXTRA_HOST_ID, APPWIDGET_HOST_ID)
            }

        // When
        receiverUnderTest.onReceive(DeviceHelpers.context, intent)

        // Then
        assertThat(launcherPrefs.get(OLD_APP_WIDGET_IDS)).isEqualTo(expectedOldIds)
        assertThat(launcherPrefs.get(APP_WIDGET_IDS)).isEqualTo(expectedNewIds)
    }

    @Test
    fun `AppWidgetsRestoredReceiver does not set widget ids when Intent action is invalid`() {
        // Given
        val oldIds = intArrayOf(1, 2, 10)
        val newIds = intArrayOf(10, 11, 12)
        val intent =
            Intent().apply {
                component = null
                `package` = TEST_PACKAGE
                action = ACTION_APPWIDGET_DELETED
                putExtra(EXTRA_APPWIDGET_OLD_IDS, oldIds)
                putExtra(EXTRA_APPWIDGET_IDS, newIds)
                putExtra(EXTRA_HOST_ID, APPWIDGET_HOST_ID)
            }

        // When
        receiverUnderTest.onReceive(DeviceHelpers.context, intent)

        // Then
        assertThat(launcherPrefs.has(OLD_APP_WIDGET_IDS, APP_WIDGET_IDS)).isFalse()
    }

    @Test
    fun `AppWidgetsRestoredReceiver does not set widget ids when Intent host id is not Launcher`() {
        // Given
        val oldIds = intArrayOf(1, 2, 10)
        val newIds = intArrayOf(10, 11, 12)
        val intent =
            Intent().apply {
                component = null
                `package` = TEST_PACKAGE
                action = ACTION_APPWIDGET_HOST_RESTORED
                putExtra(EXTRA_APPWIDGET_OLD_IDS, oldIds)
                putExtra(EXTRA_APPWIDGET_IDS, newIds)
                putExtra(EXTRA_HOST_ID, 999999999)
            }

        // When
        receiverUnderTest.onReceive(DeviceHelpers.context, intent)

        // Then
        assertThat(launcherPrefs.has(OLD_APP_WIDGET_IDS, APP_WIDGET_IDS)).isFalse()
    }

    @Test
    fun `AppWidgetsRestoredReceiver does not set ids when new and old ids differ in length`() {
        // Given
        val oldIds = intArrayOf(10)
        val newIds = intArrayOf(10, 11, 12)
        val intent =
            Intent().apply {
                component = null
                `package` = TEST_PACKAGE
                action = ACTION_APPWIDGET_HOST_RESTORED
                putExtra(EXTRA_APPWIDGET_OLD_IDS, oldIds)
                putExtra(EXTRA_APPWIDGET_IDS, newIds)
                putExtra(EXTRA_HOST_ID, APPWIDGET_HOST_ID)
            }

        // When
        receiverUnderTest.onReceive(DeviceHelpers.context, intent)

        // Then
        assertThat(launcherPrefs.has(OLD_APP_WIDGET_IDS, APP_WIDGET_IDS)).isFalse()
    }

    @Test
    fun `AppWidgetsRestoredReceiver does not set widget ids when old ids not set`() {
        // Given
        val newIds = intArrayOf(10, 11, 12)
        val intent =
            Intent().apply {
                component = null
                `package` = TEST_PACKAGE
                action = ACTION_APPWIDGET_HOST_RESTORED
                putExtra(EXTRA_APPWIDGET_IDS, newIds)
                putExtra(EXTRA_HOST_ID, APPWIDGET_HOST_ID)
            }

        // When
        receiverUnderTest.onReceive(DeviceHelpers.context, intent)

        // Then
        assertThat(launcherPrefs.has(OLD_APP_WIDGET_IDS, APP_WIDGET_IDS)).isFalse()
    }

    @Test
    fun `AppWidgetsRestoredReceiver does not set widget ids when new ids not set`() {
        // Given
        val oldIds = intArrayOf(10, 11, 12)
        val intent =
            Intent().apply {
                component = null
                `package` = TEST_PACKAGE
                action = ACTION_APPWIDGET_HOST_RESTORED
                putExtra(EXTRA_APPWIDGET_OLD_IDS, oldIds)
                putExtra(EXTRA_HOST_ID, APPWIDGET_HOST_ID)
            }

        // When
        receiverUnderTest.onReceive(DeviceHelpers.context, intent)

        // Then
        assertThat(launcherPrefs.has(OLD_APP_WIDGET_IDS, APP_WIDGET_IDS)).isFalse()
    }
}
