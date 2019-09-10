package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.widget.Switch
import androidx.annotation.Keep
import ch.deletescape.lawnchair.applyColor
import ch.deletescape.lawnchair.getColorEngineAccent
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.settings.ui.search.SearchIndex
import com.android.launcher3.Utilities
import com.android.quickstep.SysUINavigationMode

class SwipeUpSwitchPreference(context: Context, attrs: AttributeSet? = null) : StyledSwitchPreferenceCompat(context, attrs) {

    private val hasWriteSecurePermission = Utilities.hasWriteSecureSettingsPermission(context)

    init {
        if (!hasWriteSecurePermission) {
            isEnabled = false
        }
        isChecked = SysUINavigationMode.INSTANCE.get(context).mode.hasGestures
    }

    override fun onSetInitialValue(restoreValue: Boolean, defaultValue: Any?) {

    }

    override fun shouldDisableDependents(): Boolean {
        return disableDependentsState == isChecked
    }

    override fun persistBoolean(value: Boolean): Boolean {
        if (hasWriteSecurePermission) {
            try {
                return Settings.Secure.putInt(context.contentResolver, securePrefName, if (value) 1 else 0)
            } catch (ignored: Exception) {
            }
        }
        return super.persistBoolean(value)
    }

    class SwipeUpSwitchSlice(context: Context, attrs: AttributeSet) : SwitchSlice(context, attrs) {

        private val hasWriteSecurePermission = Utilities.hasWriteSecureSettingsPermission(context)

        override fun createSliceView(): View {
            return Switch(context).apply {
                applyColor(context.getColorEngineAccent())
                isChecked = SysUINavigationMode.INSTANCE.get(context).mode.hasGestures
                setOnCheckedChangeListener { _, isChecked ->
                    persistBoolean(isChecked)
                }
            }
        }

        private fun persistBoolean(value: Boolean): Boolean {
            if (hasWriteSecurePermission) {
                try {
                    return Settings.Secure.putInt(context.contentResolver, securePrefName, if (value) 1 else 0)
                } catch (ignored: Exception) {
                }
            }
            context.lawnchairPrefs.swipeUpToSwitchApps = value
            return true
        }
    }

    companion object {

        private const val securePrefName = "swipe_up_to_switch_apps_enabled"

        @Keep
        @JvmStatic
        val sliceProvider = SearchIndex.SliceProvider.fromLambda(::SwipeUpSwitchSlice)
    }
}
