package app.lawnchair.ui.preferences.data.liveinfo

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.lawnchair.ui.preferences.data.liveinfo.model.LiveInformation
import com.android.launcher3.R
import com.android.launcher3.util.MainThreadInitializedObject
import com.patrykmichalik.opto.core.PreferenceManager
import kotlinx.serialization.json.Json

class LiveInformationManager private constructor(context: Context) : PreferenceManager {

    companion object {
        private val Context.preferencesDataStore by preferencesDataStore(
            name = "live-information",
        )

        @JvmField
        val INSTANCE = MainThreadInitializedObject(::LiveInformationManager)

        @JvmStatic
        fun getInstance(context: Context) = INSTANCE.get(context)!!
    }

    override val preferencesDataStore = context.preferencesDataStore

    val enabled = preference(
        key = booleanPreferencesKey(name = "enabled"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_live_information_enabled),
    )

    val showAnnouncements = preference(
        key = booleanPreferencesKey(name = "show_announcements"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_live_information_show_announcements),
    )

    val liveInformation = preference(
        key = stringPreferencesKey(name = "live_information"),
        defaultValue = LiveInformation.default,
        parse = {
            val withUnknownKeys = Json { ignoreUnknownKeys = true }
            withUnknownKeys.decodeFromString<LiveInformation>(it)
        },
        save = { Json.encodeToString(LiveInformation.serializer(), it) },
    )
}

@Composable
fun liveInformationManager() = LiveInformationManager.getInstance(LocalContext.current)
