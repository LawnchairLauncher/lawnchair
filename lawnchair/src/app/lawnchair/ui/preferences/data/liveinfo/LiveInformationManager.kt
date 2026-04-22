package app.lawnchair.ui.preferences.data.liveinfo

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.lawnchair.ui.preferences.data.liveinfo.model.AnnouncementId
import app.lawnchair.ui.preferences.data.liveinfo.model.LiveInformation
import com.android.launcher3.R
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.patrykmichalik.opto.core.PreferenceManager
import javax.inject.Inject
import kotlinx.serialization.json.Json

@LauncherAppSingleton
class LiveInformationManager@Inject constructor(
    @ApplicationContext private val context: Context,
) : PreferenceManager {

    companion object {
        private val Context.preferencesDataStore by preferencesDataStore(
            name = "live-information",
        )

        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getLiveInformationManager)

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
        defaultValue = LiveInformation(),
        parse = { string ->
            val withUnknownKeys = Json { ignoreUnknownKeys = true }
            runCatching { withUnknownKeys.decodeFromString<LiveInformation>(string) }
                .getOrNull() ?: LiveInformation()
        },
        save = { liveInformation ->
            Json.encodeToString(
                LiveInformation.serializer(),
                liveInformation,
            )
        },
    )

    val dismissedAnnouncementIds = preference(
        key = stringPreferencesKey(name = "dismissed_announcement_ids"),
        defaultValue = emptySet(),
        parse = {
            val withUnknownKeys = Json { ignoreUnknownKeys = true }
            withUnknownKeys.decodeFromString<Set<AnnouncementId>>(it)
        },
        save = {
            Json.encodeToString(it)
        },
    )
}

@Composable
fun liveInformationManager() = LiveInformationManager.getInstance(LocalContext.current)
