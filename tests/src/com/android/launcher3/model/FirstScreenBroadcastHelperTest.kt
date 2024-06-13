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

package com.android.launcher3.model

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageInstaller.SessionInfo
import android.os.UserHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.model.FirstScreenBroadcastHelper.MAX_BROADCAST_SIZE
import com.android.launcher3.model.FirstScreenBroadcastHelper.getTotalItemCount
import com.android.launcher3.model.FirstScreenBroadcastHelper.truncateModelForBroadcast
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.PackageManagerHelper
import com.android.launcher3.util.PackageUserKey
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FirstScreenBroadcastHelperTest {
    private val context = spy(InstrumentationRegistry.getInstrumentation().targetContext)
    private val mockPmHelper = mock<PackageManagerHelper>()
    private val expectedAppPackage = "appPackageExpected"
    private val expectedComponentName = ComponentName(expectedAppPackage, "expectedClass")
    private val expectedInstallerPackage = "installerPackage"
    private val expectedIntent =
        Intent().apply {
            component = expectedComponentName
            setPackage(expectedAppPackage)
        }
    private val unexpectedAppPackage = "appPackageUnexpected"
    private val unexpectedComponentName = ComponentName(expectedAppPackage, "unexpectedClass")
    private val firstScreenItems =
        listOf(
            WorkspaceItemInfo().apply {
                container = CONTAINER_DESKTOP
                intent = expectedIntent
            },
            WorkspaceItemInfo().apply {
                container = CONTAINER_HOTSEAT
                intent = expectedIntent
            },
            LauncherAppWidgetInfo().apply { providerName = expectedComponentName }
        )

    @Test
    fun `Broadcast Models are created with Pending Items from first screen`() {
        // Given
        val sessionInfoExpected =
            SessionInfo().apply {
                installerPackageName = expectedInstallerPackage
                appPackageName = expectedAppPackage
            }
        val sessionInfoUnexpected =
            SessionInfo().apply {
                installerPackageName = expectedInstallerPackage
                appPackageName = unexpectedAppPackage
            }
        val sessionInfoMap: HashMap<PackageUserKey, SessionInfo> =
            hashMapOf(
                PackageUserKey(unexpectedAppPackage, UserHandle(0)) to sessionInfoExpected,
                PackageUserKey(expectedAppPackage, UserHandle(0)) to sessionInfoUnexpected
            )

        // When
        val actualResult =
            FirstScreenBroadcastHelper.createModelsForFirstScreenBroadcast(
                packageManagerHelper = mockPmHelper,
                firstScreenItems = firstScreenItems,
                userKeyToSessionMap = sessionInfoMap,
                allWidgets = listOf()
            )

        // Then
        val expectedResult =
            listOf(
                FirstScreenBroadcastModel(
                    installerPackage = expectedInstallerPackage,
                    pendingWorkspaceItems = mutableSetOf(expectedAppPackage),
                    pendingHotseatItems = mutableSetOf(expectedAppPackage),
                    pendingWidgetItems = mutableSetOf(expectedAppPackage)
                )
            )

        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `Broadcast Models are created with Installed Items from first screen`() {
        // Given
        whenever(mockPmHelper.getAppInstallerPackage(expectedAppPackage))
            .thenReturn(expectedInstallerPackage)

        // When
        val actualResult =
            FirstScreenBroadcastHelper.createModelsForFirstScreenBroadcast(
                packageManagerHelper = mockPmHelper,
                firstScreenItems = firstScreenItems,
                userKeyToSessionMap = hashMapOf(),
                allWidgets =
                    listOf(
                        LauncherAppWidgetInfo().apply {
                            providerName = expectedComponentName
                            screenId = 0
                        }
                    )
            )

        // Then
        val expectedResult =
            listOf(
                FirstScreenBroadcastModel(
                    installerPackage = expectedInstallerPackage,
                    installedHotseatItems = mutableSetOf(expectedAppPackage),
                    installedWorkspaceItems = mutableSetOf(expectedAppPackage),
                    firstScreenInstalledWidgets = mutableSetOf(expectedAppPackage)
                )
            )
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `Broadcast Models are created with Installed Widgets from every screen`() {
        // Given
        val expectedAppPackage2 = "appPackageExpected2"
        val expectedComponentName2 = ComponentName(expectedAppPackage2, "expectedClass2")
        whenever(mockPmHelper.getAppInstallerPackage(expectedAppPackage))
            .thenReturn(expectedInstallerPackage)
        whenever(mockPmHelper.getAppInstallerPackage(expectedAppPackage2))
            .thenReturn(expectedInstallerPackage)

        // When
        val actualResult =
            FirstScreenBroadcastHelper.createModelsForFirstScreenBroadcast(
                packageManagerHelper = mockPmHelper,
                firstScreenItems = listOf(),
                userKeyToSessionMap = hashMapOf(),
                allWidgets =
                    listOf(
                        LauncherAppWidgetInfo().apply {
                            providerName = expectedComponentName
                            screenId = 0
                        },
                        LauncherAppWidgetInfo().apply {
                            providerName = expectedComponentName2
                            screenId = 1
                        },
                        LauncherAppWidgetInfo().apply {
                            providerName = unexpectedComponentName
                            screenId = 0
                        }
                    )
            )

        // Then
        val expectedResult =
            listOf(
                FirstScreenBroadcastModel(
                    installerPackage = expectedInstallerPackage,
                    installedHotseatItems = mutableSetOf(),
                    installedWorkspaceItems = mutableSetOf(),
                    firstScreenInstalledWidgets = mutableSetOf(expectedAppPackage),
                    secondaryScreenInstalledWidgets = mutableSetOf(expectedAppPackage2)
                )
            )
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `Broadcast Models are created with Pending Items in Collections from the first screen`() {
        // Given
        val sessionInfoExpected =
            SessionInfo().apply {
                installerPackageName = expectedInstallerPackage
                appPackageName = expectedAppPackage
            }
        val sessionInfoUnexpected =
            SessionInfo().apply {
                installerPackageName = expectedInstallerPackage
                appPackageName = unexpectedAppPackage
            }
        val sessionInfoMap: HashMap<PackageUserKey, SessionInfo> =
            hashMapOf(
                PackageUserKey(unexpectedAppPackage, UserHandle(0)) to sessionInfoExpected,
                PackageUserKey(expectedAppPackage, UserHandle(0)) to sessionInfoUnexpected,
            )
        val expectedItemInfo = WorkspaceItemInfo().apply { intent = expectedIntent }
        val expectedFolderInfo = FolderInfo().apply { add(expectedItemInfo) }
        val firstScreenItems = listOf(expectedFolderInfo)

        // When
        val actualResult =
            FirstScreenBroadcastHelper.createModelsForFirstScreenBroadcast(
                packageManagerHelper = mockPmHelper,
                firstScreenItems = firstScreenItems,
                userKeyToSessionMap = sessionInfoMap,
                allWidgets = listOf()
            )

        // Then
        val expectedResult =
            listOf(
                FirstScreenBroadcastModel(
                    installerPackage = expectedInstallerPackage,
                    pendingCollectionItems = mutableSetOf(expectedAppPackage)
                )
            )
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `Models with too many items get truncated to max Broadcast size`() {
        // given
        val broadcastModel =
            FirstScreenBroadcastModel(
                installerPackage = expectedInstallerPackage,
                pendingCollectionItems =
                    mutableSetOf<String>().apply { repeat(20) { add(it.toString()) } },
                pendingWorkspaceItems =
                    mutableSetOf<String>().apply { repeat(20) { add(it.toString()) } },
                pendingHotseatItems =
                    mutableSetOf<String>().apply { repeat(20) { add(it.toString()) } },
                pendingWidgetItems =
                    mutableSetOf<String>().apply { repeat(20) { add(it.toString()) } },
                installedWorkspaceItems =
                    mutableSetOf<String>().apply { repeat(20) { add(it.toString()) } },
                installedHotseatItems =
                    mutableSetOf<String>().apply { repeat(20) { add(it.toString()) } },
                firstScreenInstalledWidgets =
                    mutableSetOf<String>().apply { repeat(20) { add(it.toString()) } },
                secondaryScreenInstalledWidgets =
                    mutableSetOf<String>().apply { repeat(20) { add(it.toString()) } }
            )

        // When
        broadcastModel.truncateModelForBroadcast()

        // Then
        assertEquals(MAX_BROADCAST_SIZE, broadcastModel.getTotalItemCount())
    }

    @Test
    fun `Broadcast truncates installed Hotseat items before other installed items`() {
        // Given
        val broadcastModel =
            FirstScreenBroadcastModel(
                installerPackage = expectedInstallerPackage,
                installedWorkspaceItems =
                    mutableSetOf<String>().apply { repeat(50) { add(it.toString()) } },
                firstScreenInstalledWidgets =
                    mutableSetOf<String>().apply { repeat(10) { add(it.toString()) } },
                secondaryScreenInstalledWidgets =
                    mutableSetOf<String>().apply { repeat(10) { add((it + 10).toString()) } },
                installedHotseatItems =
                    mutableSetOf<String>().apply { repeat(10) { add(it.toString()) } },
            )

        // When
        broadcastModel.truncateModelForBroadcast()

        // Then
        assertEquals(MAX_BROADCAST_SIZE, broadcastModel.getTotalItemCount())
        assertEquals(50, broadcastModel.installedWorkspaceItems.size)
        assertEquals(10, broadcastModel.firstScreenInstalledWidgets.size)
        assertEquals(10, broadcastModel.secondaryScreenInstalledWidgets.size)
        assertEquals(0, broadcastModel.installedHotseatItems.size)
    }

    @Test
    fun `Broadcast truncates Widgets before the rest of the first screen items`() {
        // Given
        val broadcastModel =
            FirstScreenBroadcastModel(
                installerPackage = expectedInstallerPackage,
                installedWorkspaceItems =
                    mutableSetOf<String>().apply { repeat(70) { add(it.toString()) } },
                firstScreenInstalledWidgets =
                    mutableSetOf<String>().apply { repeat(20) { add(it.toString()) } },
                secondaryScreenInstalledWidgets =
                    mutableSetOf<String>().apply { repeat(20) { add(it.toString()) } },
            )

        // When
        broadcastModel.truncateModelForBroadcast()

        // Then
        assertEquals(MAX_BROADCAST_SIZE, broadcastModel.getTotalItemCount())
        assertEquals(70, broadcastModel.installedWorkspaceItems.size)
        assertEquals(0, broadcastModel.firstScreenInstalledWidgets.size)
        assertEquals(0, broadcastModel.secondaryScreenInstalledWidgets.size)
    }

    @Test
    fun `Broadcasts are correctly formed with Extras for each Installer`() {
        // Given
        val broadcastModels: List<FirstScreenBroadcastModel> =
            listOf(
                FirstScreenBroadcastModel(
                    installerPackage = expectedInstallerPackage,
                    pendingCollectionItems = mutableSetOf("pendingCollectionItem"),
                    pendingWorkspaceItems = mutableSetOf("pendingWorkspaceItem"),
                    pendingHotseatItems = mutableSetOf("pendingHotseatItems"),
                    pendingWidgetItems = mutableSetOf("pendingWidgetItems"),
                    installedWorkspaceItems = mutableSetOf("installedWorkspaceItems"),
                    installedHotseatItems = mutableSetOf("installedHotseatItems"),
                    firstScreenInstalledWidgets = mutableSetOf("firstScreenInstalledWidgetItems"),
                    secondaryScreenInstalledWidgets = mutableSetOf("secondaryInstalledWidgetItems")
                )
            )
        val expectedPendingIntent =
            PendingIntent.getActivity(
                context,
                0 /* requestCode */,
                Intent(),
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

        // When
        FirstScreenBroadcastHelper.sendBroadcastsForModels(context, broadcastModels)

        // Then
        val argumentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(context).sendBroadcast(argumentCaptor.capture())

        assertEquals(
            "com.android.launcher3.action.FIRST_SCREEN_ACTIVE_INSTALLS",
            argumentCaptor.value.action
        )
        assertEquals(expectedInstallerPackage, argumentCaptor.value.`package`)
        assertEquals(
            expectedPendingIntent,
            argumentCaptor.value.getParcelableExtra("verificationToken")
        )
        assertEquals(
            arrayListOf("pendingCollectionItem"),
            argumentCaptor.value.getStringArrayListExtra("folderItem")
        )
        assertEquals(
            arrayListOf("pendingWorkspaceItem"),
            argumentCaptor.value.getStringArrayListExtra("workspaceItem")
        )
        assertEquals(
            arrayListOf("pendingHotseatItems"),
            argumentCaptor.value.getStringArrayListExtra("hotseatItem")
        )
        assertEquals(
            arrayListOf("pendingWidgetItems"),
            argumentCaptor.value.getStringArrayListExtra("widgetItem")
        )
        assertEquals(
            arrayListOf("installedWorkspaceItems"),
            argumentCaptor.value.getStringArrayListExtra("workspaceInstalledItems")
        )
        assertEquals(
            arrayListOf("installedHotseatItems"),
            argumentCaptor.value.getStringArrayListExtra("hotseatInstalledItems")
        )
        assertEquals(
            arrayListOf("firstScreenInstalledWidgetItems", "secondaryInstalledWidgetItems"),
            argumentCaptor.value.getStringArrayListExtra("widgetInstalledItems")
        )
    }
}
