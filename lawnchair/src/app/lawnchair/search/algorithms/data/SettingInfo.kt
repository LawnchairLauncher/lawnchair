package app.lawnchair.search.algorithms.data

import android.content.Intent

data class SettingInfo(
    val id: String,
    val name: String,
    val action: String,
    val requiresUri: Boolean = false,
    // LC: set for the launcher's own settings, opened directly instead of by [action].
    val intent: Intent? = null,
)
