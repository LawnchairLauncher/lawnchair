package app.lawnchair.smartspace.provider

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import androidx.core.content.edit
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceScores
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.util.getApkVersionComparison
import com.android.launcher3.LauncherPrefs.Companion.getPrefs
import com.android.launcher3.R
import com.android.launcher3.util.OnboardingPrefs
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

class OnboardingProvider(context: Context) :
    SmartspaceDataSource(
        context,
        R.string.smartspace_onboarding,
        { smartspaceOnboarding },
    ) {

    companion object {
        const val PREF_LAWNCHAIR_MAJOR_VERSION = "pref_lawnchairMajorVersion"
        const val PREF_HAS_OPENED_SETTINGS = "pref_hasOpenedSettings"

        private val HOME_BOUNCE_KEY = OnboardingPrefs.HOME_BOUNCE_SEEN.sharedPrefKey
        private val PREF_KEYS = setOf(
            PREF_LAWNCHAIR_MAJOR_VERSION,
            PREF_HAS_OPENED_SETTINGS,
            HOME_BOUNCE_KEY,
        )

        private const val REQUEST_CODE_SETTINGS = 1
    }
    private val prefs = getPrefs(context)

    private val lawnSettingsIntent: Intent = Intent(Intent.ACTION_APPLICATION_PREFERENCES)
        .setPackage(context.packageName)

    private val lawnSettingsPendingIntent: PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CODE_SETTINGS,
        lawnSettingsIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** No-op. */
    private val lawnOnboardingPendingIntent: PendingIntent = lawnSettingsPendingIntent

    override val internalTargets = callbackFlow {
        val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener {
                sharedPreferences,
                key,
            ->
            if (key == null) return@OnSharedPreferenceChangeListener

            val isRelevant = when (sharedPreferences) {
                prefs -> key in PREF_KEYS
                else -> false
            }
            if (!isRelevant) return@OnSharedPreferenceChangeListener

            trySend(listOfNotNull(getSmartspaceTarget()))
        }

        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        trySend(listOfNotNull(getSmartspaceTarget()))

        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        }
    }

    private fun hasSeenHomeBounce(): Boolean {
        return prefs.getBoolean(HOME_BOUNCE_KEY, false)
    }

    private fun hasSeenSettings(): Boolean {
        return prefs.getBoolean(PREF_HAS_OPENED_SETTINGS, false)
    }

    private fun hasRecentlyUpgradedMajor(): Boolean {
        return context.getApkVersionComparison().first[0] > prefs.getInt(PREF_LAWNCHAIR_MAJOR_VERSION, 123456)
    }

    private fun getSmartspaceTarget(): SmartspaceTarget? {
        return when {
            !hasSeenHomeBounce() -> {
                SmartspaceTarget(
                    id = "onboarding-swipe",
                    headerAction = SmartspaceAction(
                        id = "onboarding-swipe-action",
                        icon = null,
                        title = context.getString(R.string.onboarding_welcome),
                        subtitle = context.getString(R.string.onboarding_swipe_up),
                        pendingIntent = null,
                    ),
                    score = SmartspaceScores.SCORE_ONBOARDING,
                    featureType = SmartspaceTarget.FeatureType.FEATURE_ONBOARDING,
                )
            }

            !hasSeenSettings() -> {
                SmartspaceTarget(
                    id = "onboarding-settings",
                    headerAction = SmartspaceAction(
                        id = "onboarding-settings-action",
                        icon = Icon.createWithResource(context, R.drawable.ic_lightbulb),
                        title = context.getString(R.string.onboarding_open_settings_title),
                        subtitle = context.getString(R.string.onboarding_open_settings_subtitle),
                        pendingIntent = lawnSettingsPendingIntent,
                    ),
                    score = SmartspaceScores.SCORE_ONBOARDING,
                    featureType = SmartspaceTarget.FeatureType.FEATURE_ONBOARDING,
                )
            }

            hasRecentlyUpgradedMajor() -> {
                if (true) return null // pE-TODO(FeatureFlags): upgraded onboarding
                prefs.edit { putInt(PREF_LAWNCHAIR_MAJOR_VERSION, context.getApkVersionComparison().first[0]) }
                SmartspaceTarget(
                    id = "onboarding-upgrade",
                    headerAction = SmartspaceAction(
                        id = "onboarding-upgrade-action",
                        icon = Icon.createWithResource(context, R.drawable.ic_lightbulb),
                        title = context.getString(R.string.onboarding_major_upgrade_title),
                        subtitle = context.getString(R.string.onboarding_major_upgrade_subtitle),
                        pendingIntent = lawnOnboardingPendingIntent,
                    ),
                    score = SmartspaceScores.SCORE_ONBOARDING,
                    featureType = SmartspaceTarget.FeatureType.FEATURE_ONBOARDING,
                )
            }

            else -> null
        }
    }
}
