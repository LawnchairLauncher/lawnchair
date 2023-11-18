package app.lawnchair

import android.content.Context
import android.content.pm.LauncherActivityInfo
import androidx.annotation.Keep
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.icons.LauncherActivityCachingLogic as BaseLogic
import com.android.launcher3.util.ComponentKey

@Keep
class LauncherActivityCachingLogic(context: Context) : BaseLogic() {

    private val prefs = PreferenceManager.getInstance(context)

    override fun getLabel(info: LauncherActivityInfo): CharSequence {
        val key = ComponentKey(info.componentName, info.user)
        val customLabel = prefs.customAppName[key]
        if (!customLabel.isNullOrEmpty()) {
            return customLabel
        }
        return super.getLabel(info)
    }
}
