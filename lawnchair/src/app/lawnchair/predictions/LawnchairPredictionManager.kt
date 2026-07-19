package app.lawnchair.predictions

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.patrykmichalik.opto.core.PreferenceManager
import javax.inject.Inject

/**
 * [PreferenceManager] for prediction-related data.
 *
 * Exposes three [LawnchairPredictionStore] for hotseat, allapps usage, and dismissed apps.
 */
@LauncherAppSingleton
class LawnchairPredictionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : PreferenceManager {

    companion object {
        private const val STORE_DELIMITER = ";"

        private val Context.predictionDataStore by preferencesDataStore(
            name = "lawnchair-predictions",
        )

        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getLawnchairPredictionManager)

        @JvmStatic
        fun getInstance(context: Context) = INSTANCE.get(context)!!
    }

    override val preferencesDataStore = context.predictionDataStore

    /** Ordered store tracking hotseat app launches. */
    val hotseatStore = LawnchairPredictionStore(
        preference = preference(
            key = stringPreferencesKey("hotseat_usage"),
            defaultValue = emptyList(),
            parse = { it.split(STORE_DELIMITER).filter { s -> s.isNotEmpty() } },
            save = { it.joinToString(STORE_DELIMITER) },
        ),
        isOrdered = true,
    )

    /** Ordered store tracking allapps app launches. */
    val allAppsStore = LawnchairPredictionStore(
        preference = preference(
            key = stringPreferencesKey("all_apps_usage"),
            defaultValue = emptyList(),
            parse = { it.split(STORE_DELIMITER).filter { s -> s.isNotEmpty() } },
            save = { it.joinToString(STORE_DELIMITER) },
        ),
        isOrdered = true,
    )

    /** Unordered store of dismissed prediction app keys. */
    val dismissedAppsStore = LawnchairPredictionStore(
        preference = preference(
            key = stringPreferencesKey("dismissed_apps"),
            defaultValue = emptyList(),
            parse = { it.split(STORE_DELIMITER).filter { s -> s.isNotEmpty() } },
            save = { it.joinToString(STORE_DELIMITER) },
        ),
        isOrdered = false,
    )
}
