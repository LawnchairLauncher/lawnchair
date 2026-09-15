package app.mica.icons

import android.content.Context
import app.mica.preferences.PreferenceManager
import app.mica.preferences2.PreferenceManager2
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.LooperExecutor
import dagger.Module
import dagger.Provides

@Module
class ThemeManagerModule {

    @Provides
    @LauncherAppSingleton
    fun provideThemeManager(
        @ApplicationContext context: Context,
        @Ui uiExecutor: LooperExecutor,
        prefs: LauncherPrefs,
        lifecycle: DaggerSingletonTracker,
        iconControllerFactory: ThemeManager.IconControllerFactory,
        prefs2: PreferenceManager2,
        prefs1: PreferenceManager,
    ): ThemeManager {
        return MicaThemeManager(
            context = context,
            uiExecutor = uiExecutor,
            prefs = prefs,
            lifecycle = lifecycle,
            iconControllerFactory = iconControllerFactory,
            prefs2 = prefs2,
            prefs1 = prefs1,
        )
    }
}
