package app.lawnchair.search.algorithms

import app.lawnchair.search.adapter.SearchTargetCompat
import com.android.launcher3.R

internal enum class SearchScope(val label: Int) {
    ALL(R.string.omni_filter_all),
    APPS(R.string.omni_filter_apps),
    PEOPLE(R.string.omni_filter_people),
    FILES(R.string.omni_filter_files),
    ACTIONS(R.string.omni_filter_actions),
    ;

    fun includes(target: SearchTargetCompat): Boolean = when (this) {
        ALL -> true
        APPS -> target.resultType == SearchTargetCompat.RESULT_TYPE_APPLICATION
        PEOPLE -> target.resultType == SearchTargetCompat.RESULT_TYPE_CONTACT_TILE
        FILES -> target.resultType == SearchTargetCompat.RESULT_TYPE_FILE_TILE
        ACTIONS -> target.resultType in setOf(SearchTargetCompat.RESULT_TYPE_SHORTCUT, SearchTargetCompat.RESULT_TYPE_SETTING_TILE, SearchTargetCompat.RESULT_TYPE_CALCULATOR) || target.packageName in setOf("omni_launcher_action", "omni_assistant")
    }
}
