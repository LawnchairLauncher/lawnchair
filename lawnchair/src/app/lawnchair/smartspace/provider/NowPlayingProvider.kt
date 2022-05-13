package app.lawnchair.smartspace.provider

import android.content.Context
import android.graphics.drawable.Icon
import android.text.TextUtils
import app.lawnchair.getAppName
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceScores
import app.lawnchair.smartspace.model.SmartspaceTarget
import com.android.launcher3.R
import kotlinx.coroutines.flow.MutableStateFlow

class NowPlayingProvider(private val context: Context) : SmartspaceDataSource {

    private val media = MediaListener(context, this::reload).also { it.onResume() }
    private val defaultIcon = Icon.createWithResource(context, R.drawable.ic_music_note)

    private val targetsFlow = MutableStateFlow(emptyList<SmartspaceTarget>())
    override val targets get() = targetsFlow

    private fun getSmartspaceTarget(): SmartspaceTarget? {
        val tracking = media.tracking ?: return null
        val title = tracking.info.title ?: return null

        val sbn = tracking.sbn
        val icon = sbn.notification.smallIcon ?: defaultIcon

        val mediaInfo = tracking.info
        val artistAndAlbum = listOf(mediaInfo.artist, mediaInfo.album)
            .filter { !TextUtils.isEmpty(it) }
            .joinToString(" – ")
        val subtitle = if (!TextUtils.isEmpty(artistAndAlbum)) {
            artistAndAlbum
        } else sbn?.getAppName(context) ?: context.getAppName(tracking.packageName)
        val intent = sbn?.notification?.contentIntent
        return SmartspaceTarget(
            id = "nowPlaying-${mediaInfo.hashCode()}",
            headerAction = SmartspaceAction(
                id = "nowPlayingAction-${mediaInfo.hashCode()}",
                icon = icon,
                title = title,
                subtitle = subtitle,
                pendingIntent = intent,
                onClick = if (intent == null) Runnable { media.toggle(true) } else null
            ),
            score = SmartspaceScores.SCORE_MEDIA,
            featureType = SmartspaceTarget.FeatureType.FEATURE_MEDIA
        )
    }

    private fun reload() {
        targetsFlow.value = listOfNotNull(getSmartspaceTarget())
    }

    override fun destroy() {
        media.onPause()
    }
}
