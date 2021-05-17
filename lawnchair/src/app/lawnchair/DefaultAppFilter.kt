package app.lawnchair

import android.content.ComponentName
import android.os.UserHandle
import com.android.launcher3.AppFilter
import com.android.launcher3.BuildConfig

open class DefaultAppFilter : AppFilter() {
    private val defaultHideList = setOf(
        // Voice search
        ComponentName.unflattenFromString("com.google.android.googlequicksearchbox/.VoiceSearchActivity"),
        // Wallpapers
        ComponentName.unflattenFromString("com.google.android.apps.wallpaper/.picker.CategoryPickerActivity"),
        // GNL
        ComponentName.unflattenFromString("com.google.android.launcher/.StubApp"),
        // Actions Services
        ComponentName.unflattenFromString("com.google.android.as/com.google.android.apps.miphone.aiai.allapps.main.MainDummyActivity"),
        // Lawnchair
        ComponentName(BuildConfig.APPLICATION_ID, LawnchairLauncherQuickstep::class.java.name),
    )

    override fun shouldShowApp(app: ComponentName, user: UserHandle?): Boolean = !defaultHideList.contains(app)
}
