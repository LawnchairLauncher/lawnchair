/*
 * Copyright 2026, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.icons

import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.util.DisplayMetrics
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.FakeLauncherPrefs
import com.android.launcher3.Flags
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.BaseIconFactory
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.mono.MonoIconThemeControllerTest.Companion.ensureBitmapSerializationSupported
import com.android.launcher3.util.AllModulesForTest
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.FakePrefsModule
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import dagger.Component
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class LawnchairThemeManagerTest {

    @get:Rule val context = SandboxApplication()
    @get:Rule val setFlagsRule = SetFlagsRule()

    private lateinit var themeManager: ThemeManager
    private lateinit var prefs: PreferenceManager

    @Before
    fun setUp() {
        context.initDaggerComponent(DaggerLawnchairThemeManagerTestComponent.builder())
        themeManager = ThemeManager.INSTANCE[context]
        prefs = PreferenceManager.INSTANCE[context]
        themeManager.isMonoThemeEnabled = false
        prefs.forceIconMonochrome.set(false)
        waitForPreferenceChanges()
    }

    @Test
    @EnableFlags(Flags.FLAG_FORCE_MONOCHROME_APP_ICONS)
    fun `force monochrome themes unsupported adaptive icons`() {
        ensureBitmapSerializationSupported()
        prefs.forceIconMonochrome.set(true)
        themeManager.isMonoThemeEnabled = true
        waitForPreferenceChanges()

        val themedBitmap = themeManager.themeController!!.createThemedBitmap(
            adaptiveIcon(monochrome = null),
            BitmapInfo.LOW_RES_INFO,
            BaseIconFactory(context, DisplayMetrics.DENSITY_MEDIUM, 30),
        )

        assertThat(themedBitmap).isNotNull()
    }

    @Test
    fun `disabled force monochrome only themes supported adaptive icons`() {
        themeManager.isMonoThemeEnabled = true
        waitForPreferenceChanges()
        val iconFactory = BaseIconFactory(context, DisplayMetrics.DENSITY_MEDIUM, 30)

        assertThat(
            themeManager.themeController!!.createThemedBitmap(
                adaptiveIcon(monochrome = ColorDrawable(Color.RED)),
                BitmapInfo.LOW_RES_INFO,
                iconFactory,
            )
        ).isNotNull()
        assertThat(
            themeManager.themeController!!.createThemedBitmap(
                adaptiveIcon(monochrome = null),
                BitmapInfo.LOW_RES_INFO,
                iconFactory,
            )
        ).isNull()
    }

    @Test
    fun `force monochrome does not enable themed icons`() {
        prefs.forceIconMonochrome.set(true)
        waitForPreferenceChanges()
        assertThat(themeManager.themeController).isNull()

        prefs.forceIconMonochrome.set(false)
        waitForPreferenceChanges()
        assertThat(themeManager.themeController).isNull()
    }

    @Test
    fun `force monochrome change updates icon state and notifies listeners`() {
        themeManager.isMonoThemeEnabled = true
        waitForPreferenceChanges()
        val previousState = themeManager.iconState
        var callbackCalled = false
        themeManager.addChangeListener { callbackCalled = true }

        prefs.forceIconMonochrome.set(true)
        waitForPreferenceChanges()

        assertThat(themeManager.iconState).isNotEqualTo(previousState)
        assertThat(callbackCalled).isTrue()
    }

    private fun adaptiveIcon(monochrome: ColorDrawable?) =
        AdaptiveIconDrawable(ColorDrawable(Color.BLACK), null, monochrome)

    private fun waitForPreferenceChanges() {
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
    }
}

@LauncherAppSingleton
@Component(
    modules = [AllModulesForTest::class, FakePrefsModule::class, ThemeManagerModule::class]
)
interface LawnchairThemeManagerTestComponent : LauncherAppComponent {

    override fun getLauncherPrefs(): FakeLauncherPrefs

    @Component.Builder
    interface Builder : LauncherAppComponent.Builder {

        override fun build(): LawnchairThemeManagerTestComponent
    }
}
