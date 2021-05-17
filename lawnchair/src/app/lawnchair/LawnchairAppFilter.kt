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

    override fun shouldShowApp(app: ComponentName, user: UserHandle?): Boolean {
        if (!super.shouldShowApp(app, user)) {
            return false
        }
        val key = ComponentKey(app, user)
        if (customHideList.contains(key.toString())) {
            return false
        }
        return true
    }
}
