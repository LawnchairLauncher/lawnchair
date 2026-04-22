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

package com.android.launcher3.folder

import android.R
import android.content.ComponentName
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherSettings.Favorites.DESKTOP_ICON_FLAG
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.PreloadIconDrawable
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.FastBitmapDrawable
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.IconCache.ItemInfoUpdateReceiver
import com.android.launcher3.icons.PlaceHolderIconDrawable
import com.android.launcher3.icons.UserBadgeDrawable
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_ARCHIVED
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_INSTALL_SESSION_ACTIVE
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.ActivityContextWrapper
import com.android.launcher3.util.AllModulesForTest
import com.android.launcher3.util.Executors
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.FakePrefsModule
import com.android.launcher3.util.FlagOp
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY2
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY3
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY4
import com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.UserIconInfo
import com.google.common.truth.Truth.assertThat
import dagger.Component
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlin.annotation.AnnotationTarget.PROPERTY_SETTER
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests for [PreviewItemManager] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class PreviewItemManagerTest {

    @get:Rule val context = SandboxApplication().withModelDependency()
    @get:Rule val theseStateRule = ThemeStateRule()

    private lateinit var previewItemManager: PreviewItemManager
    private lateinit var folderItems: ArrayList<WorkspaceItemInfo>
    private lateinit var folderIcon: FolderIcon
    private lateinit var iconCache: IconCache

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        context.initDaggerComponent(DaggerPreviewItemManagerTestComponent.builder())
        theseStateRule.themeState?.let {
            LauncherPrefs.get(context).putSync(ThemeManager.THEMED_ICONS.to(it))
        }
        folderIcon = FolderIcon(ActivityContextWrapper(context))

        iconCache = LauncherAppState.INSTANCE[context].iconCache
        spyOn(iconCache)
        doReturn(null).whenever(iconCache).updateIconInBackground(any(), any(), any())

        previewItemManager = PreviewItemManager(folderIcon)

        folderIcon.mInfo =
            FolderInfo().apply {
                title = context.getString(R.string.copy)
                add(buildWorkspaceItemInfo(TEST_ACTIVITY))
                add(buildWorkspaceItemInfo(TEST_ACTIVITY2))
                add(buildWorkspaceItemInfo(TEST_ACTIVITY3))
                add(buildWorkspaceItemInfo(TEST_ACTIVITY4))
            }
        // Use getAppContents() to "cast" contents to WorkspaceItemInfo so we can set bitmaps
        folderItems = folderIcon.mInfo.getAppContents()

        // Set second icon to be non-themed.
        folderItems[1].bitmap.themedBitmap = null

        // Set third icon to be themed with badge.
        folderItems[2].bitmap =
            folderItems[2].bitmap.withFlags(profileFlagOp(UserIconInfo.TYPE_WORK))

        // Set fourth icon to be non-themed with badge.
        folderItems[3].bitmap =
            folderItems[3].bitmap.withFlags(profileFlagOp(UserIconInfo.TYPE_WORK))
        folderItems[3].bitmap.themedBitmap = null
    }

    @Test
    @MonoThemeEnabled(true)
    fun checkThemedIconWithThemingOn_iconShouldBeThemed() {
        val drawingParams = PreviewItemDrawingParams(0f, 0f, 0f)

        previewItemManager.setDrawable(drawingParams, folderItems[0])

        assert((drawingParams.drawable as FastBitmapDrawable).isThemed())
    }

    @Test
    @MonoThemeEnabled(false)
    fun checkThemedIconWithThemingOff_iconShouldNotBeThemed() {
        val drawingParams = PreviewItemDrawingParams(0f, 0f, 0f)

        previewItemManager.setDrawable(drawingParams, folderItems[0])

        assert(!(drawingParams.drawable as FastBitmapDrawable).isThemed())
    }

    @Test
    @MonoThemeEnabled(true)
    fun checkUnthemedIconWithThemingOn_iconShouldNotBeThemed() {
        val drawingParams = PreviewItemDrawingParams(0f, 0f, 0f)

        previewItemManager.setDrawable(drawingParams, folderItems[1])

        assert(!(drawingParams.drawable as FastBitmapDrawable).isThemed())
    }

    @Test
    @MonoThemeEnabled(false)
    fun checkUnthemedIconWithThemingOff_iconShouldNotBeThemed() {
        val drawingParams = PreviewItemDrawingParams(0f, 0f, 0f)

        previewItemManager.setDrawable(drawingParams, folderItems[1])

        assert(!(drawingParams.drawable as FastBitmapDrawable).isThemed())
    }

    @Test
    @MonoThemeEnabled(true)
    fun checkThemedIconWithBadgeWithThemingOn_iconAndBadgeShouldBeThemed() {
        val drawingParams = PreviewItemDrawingParams(0f, 0f, 0f)

        previewItemManager.setDrawable(drawingParams, folderItems[2])

        assert((drawingParams.drawable as FastBitmapDrawable).isThemed())
        assert(
            ((drawingParams.drawable as FastBitmapDrawable).badge as UserBadgeDrawable).mIsThemed
        )
    }

    @Test
    @MonoThemeEnabled(true)
    fun checkUnthemedIconWithBadgeWithThemingOn_badgeShouldBeThemed() {
        val drawingParams = PreviewItemDrawingParams(0f, 0f, 0f)

        previewItemManager.setDrawable(drawingParams, folderItems[3])

        assert(!(drawingParams.drawable as FastBitmapDrawable).isThemed())
        assert(
            ((drawingParams.drawable as FastBitmapDrawable).badge as UserBadgeDrawable).mIsThemed
        )
    }

    @Test
    @MonoThemeEnabled(false)
    fun checkUnthemedIconWithBadgeWithThemingOff_iconAndBadgeShouldNotBeThemed() {
        val drawingParams = PreviewItemDrawingParams(0f, 0f, 0f)

        previewItemManager.setDrawable(drawingParams, folderItems[3])

        assert(!(drawingParams.drawable as FastBitmapDrawable).isThemed())
        assert(
            !((drawingParams.drawable as FastBitmapDrawable).badge as UserBadgeDrawable).mIsThemed
        )
    }

    @Test
    fun `Inactive archived app previews are not drawn as preload icon`() {
        // Given
        val drawingParams = PreviewItemDrawingParams(0f, 0f, 0f)
        val archivedApp =
            WorkspaceItemInfo().apply {
                runtimeStatusFlags = runtimeStatusFlags or FLAG_ARCHIVED
                runtimeStatusFlags = runtimeStatusFlags and FLAG_INSTALL_SESSION_ACTIVE.inv()
            }
        // When
        previewItemManager.setDrawable(drawingParams, archivedApp)
        // Then
        assertThat(drawingParams.drawable).isNotInstanceOf(PreloadIconDrawable::class.java)
    }

    @Test
    fun `Actively installing archived app previews are drawn as preload icon`() {
        // Given
        val drawingParams = PreviewItemDrawingParams(0f, 0f, 0f)
        val archivedApp =
            WorkspaceItemInfo().apply {
                runtimeStatusFlags = runtimeStatusFlags or FLAG_ARCHIVED
                runtimeStatusFlags = runtimeStatusFlags or FLAG_INSTALL_SESSION_ACTIVE
            }
        // When
        TestUtil.runOnExecutorSync(Executors.MAIN_EXECUTOR) {
            // Run on main thread because preload drawable triggers animator
            previewItemManager.setDrawable(drawingParams, archivedApp)
        }
        // Then
        assertThat(drawingParams.drawable).isInstanceOf(PreloadIconDrawable::class.java)
    }

    @Test
    fun `Preview item loads and apply high res icon`() {
        val drawingParams = PreviewItemDrawingParams(0f, 0f, 0f)
        val originalBitmap = folderItems[3].bitmap
        folderItems[3].bitmap = BitmapInfo.LOW_RES_INFO

        previewItemManager.setDrawable(drawingParams, folderItems[3])
        assertThat(drawingParams.drawable).isInstanceOf(PlaceHolderIconDrawable::class.java)

        val callbackCaptor = argumentCaptor<ItemInfoUpdateReceiver>()
        verify(iconCache)
            .updateIconInBackground(callbackCaptor.capture(), eq(folderItems[3]), any())

        // Restore high-res icon
        folderItems[3].bitmap = originalBitmap

        // Calling with a different item info will ignore the update
        callbackCaptor.firstValue.reapplyItemInfo(folderItems[2])
        assertThat(drawingParams.drawable).isInstanceOf(PlaceHolderIconDrawable::class.java)

        // Calling with correct value will update the drawable to high-res
        callbackCaptor.firstValue.reapplyItemInfo(folderItems[3])
        assertThat(drawingParams.drawable).isNotInstanceOf(PlaceHolderIconDrawable::class.java)
        assertThat(drawingParams.drawable).isInstanceOf(FastBitmapDrawable::class.java)
    }

    private fun profileFlagOp(type: Int) =
        UserIconInfo(Process.myUserHandle(), type).applyBitmapInfoFlags(FlagOp.NO_OP)

    private fun buildWorkspaceItemInfo(targetClass: String) =
        WorkspaceItemInfo().apply {
            intent = AppInfo.makeLaunchIntent(ComponentName(TEST_PACKAGE, targetClass))
            TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
                iconCache.getTitleAndIcon(this, DESKTOP_ICON_FLAG)
            }
        }
}

class ThemeStateRule : TestRule {

    var themeState: Boolean? = null

    override fun apply(base: Statement, description: Description): Statement {
        themeState = description.getAnnotation(MonoThemeEnabled::class.java)?.value
        return base
    }
}

// Annotation for tests that need to be run with quickstep enabled and disabled.
@Retention(RUNTIME)
@Target(FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
annotation class MonoThemeEnabled(val value: Boolean = false)

@LauncherAppSingleton
@Component(modules = [AllModulesForTest::class, FakePrefsModule::class])
interface PreviewItemManagerTestComponent : LauncherAppComponent {

    @Component.Builder
    interface Builder : LauncherAppComponent.Builder {
        override fun build(): PreviewItemManagerTestComponent
    }
}
