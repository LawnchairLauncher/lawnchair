package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.support.annotation.Keep
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.view.View
import android.widget.Switch
import ch.deletescape.lawnchair.applyColor
import ch.deletescape.lawnchair.getColorEngineAccent
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.settings.ui.search.SearchIndex
import ch.deletescape.lawnchair.util.extensions.d
import com.android.launcher3.Utilities
import com.android.quickstep.OverviewInteractionState
import com.android.systemui.shared.system.SettingsCompat

class SwipeUpSwitchPreference(context: Context, attrs: AttributeSet? = null) : StyledSwitchPreferenceCompat(context, attrs) {

    private val secureOverrideMode = OverviewInteractionState.isSwipeUpSettingsAvailable()
    private val hasWriteSecurePermission = Utilities.hasWriteSecureSettingsPermission(context)

    init {
        if (secureOverrideMode && !hasWriteSecurePermission) {
            isEnabled = false
        }
        isChecked = OverviewInteractionState.getInstance(context).isSwipeUpGestureEnabled
    }

    override fun onSetInitialValue(restoreValue: Boolean, defaultValue: Any?) {

    }

    override fun shouldDisableDependents(): Boolean {
        return disableDependentsState == isChecked
    }

    override fun persistBoolean(value: Boolean): Boolean {
        if (hasWriteSecurePermission && secureOverrideMode) {
            try {
                return Settings.Secure.putInt(context.contentResolver, securePrefName, if (value) 1 else 0)
            } catch (ignored: Exception) {
            }
        }
        return super.persistBoolean(value)
    }

    class SwipeUpSwitchSlice(context: Context, attrs: AttributeSet) : SwitchSlice(context, attrs) {

        private val secureOverrideMode = OverviewInteractionState.isSwipeUpSettingsAvailable()
        private val hasWriteSecurePermission = Utilities.hasWriteSecureSettingsPermission(context)

        override fun createSliceView(): View {
            return Switch(context).apply {
                applyColor(context.getColorEngineAccent())
                isChecked = OverviewInteractionState.getInstance(context).isSwipeUpGestureEnabled
                setOnCheckedChangeListener { _, isChecked ->
                    persistBoolean(isChecked)
                }
            }
        }

        private fun persistBoolean(value: Boolean): Boolean {
            if (hasWriteSecurePermission && secureOverrideMode) {
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

        private const val securePrefName = SettingsCompat.SWIPE_UP_SETTING_NAME

        @Keep
        @JvmStatic
        val sliceProvider = SearchIndex.SliceProvider.fromLambda(::SwipeUpSwitchSlice)
    }
}
