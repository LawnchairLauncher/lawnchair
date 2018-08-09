package ch.deletescape.lawnchair.globalsearch

import android.content.*
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.support.annotation.Keep
import android.view.View
import ch.deletescape.lawnchair.LawnchairLauncher
import ch.deletescape.lawnchair.colors.ColorEngine
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherState
import com.android.launcher3.R

@Keep
class AppSearchSearchProvider(context: Context) : SearchProvider(context) {

    override val name = context.getString(R.string.search_provider_appsearch)!!
    override val supportsVoiceSearch: Boolean
        get() = false
    override val supportsAssistant: Boolean
        get() = false

    override fun startSearch(callback: (intent: Intent) -> Unit){
        val launcher = LauncherAppState.getInstanceNoCreate().launcher
        launcher.stateManager.goToState(LauncherState.ALL_APPS, true) {
            launcher.appsView.searchUiManager.startSearch()
        }
    }

    override fun getIcon(colored: Boolean): Drawable = context.getDrawable(R.drawable.ic_search).apply {
             setTint(if(colored) ColorEngine.getInstance(context).accent else Color.WHITE)
    }

}
