package app.lawnchair

import android.content.ComponentName
import android.content.Context
import android.os.UserHandle
import androidx.annotation.Keep
import app.lawnchair.util.preferences.PreferenceManager
import com.android.launcher3.util.ComponentKey

@Keep
class LawnchairAppFilter(context: Context) : DefaultAppFilter() {
    private val prefs = PreferenceManager.getInstance(context)
    private val customHideList get() = prefs.hiddenAppSet.get()

    override fun shouldShowApp(app: ComponentName, user: UserHandle?): Boolean =
        when {
            !super.shouldShowApp(app, user) -> false
            customHideList.contains(ComponentKey(app, user).toString()) -> false
            else -> true
        }
}
