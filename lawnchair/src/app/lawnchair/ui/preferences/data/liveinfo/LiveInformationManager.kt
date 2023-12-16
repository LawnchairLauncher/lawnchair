package app.lawnchair.ui.preferences.data.liveinfo

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.lawnchair.ui.preferences.data.liveinfo.model.Announcement
import com.android.launcher3.util.MainThreadInitializedObject
import com.patrykmichalik.opto.core.PreferenceManager

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

    val announcements = preference(
        key = stringPreferencesKey(name = "announcements"),
        defaultValue = emptyList(),
        parse = { Announcement.fromString(it) },
        save = { it.toString() },
    )
}

@Composable
fun liveInformationManager() = LiveInformationManager.getInstance(LocalContext.current)
