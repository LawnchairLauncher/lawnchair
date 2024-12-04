/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.wm.shell.common.pip

import android.annotation.DrawableRes
import android.annotation.StringRes
import android.app.PendingIntent
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.HandlerExecutor
import android.os.UserHandle
import com.android.wm.shell.R
import java.util.function.Consumer

/**
 * Interfaces with the [MediaSessionManager] to compose the right set of actions to show (only
 * if there are no actions from the PiP activity itself). The active media controller is only set
 * when there is a media session from the top PiP activity.
 */
class PipMediaController(private val mContext: Context, private val mMainHandler: Handler) {
    /**
     * A listener interface to receive notification on changes to the media actions.
     */
    interface ActionListener {
        /**
         * Called when the media actions changed.
         */
        fun onMediaActionsChanged(actions: List<RemoteAction?>?)
    }

    /**
     * A listener interface to receive notification on changes to the media metadata.
     */
    interface MetadataListener {
        /**
         * Called when the media metadata changed.
         */
        fun onMediaMetadataChanged(metadata: MediaMetadata?)
    }

    /**
     * A listener interface to receive notification on changes to the media session token.
     */
    interface TokenListener {
        /**
         * Called when the media session token changed.
         */
        fun onMediaSessionTokenChanged(token: MediaSession.Token?)
    }

    private val mHandlerExecutor: HandlerExecutor = HandlerExecutor(mMainHandler)
    private val mMediaSessionManager: MediaSessionManager?
    private var mMediaController: MediaController? = null
    private val mPauseAction: RemoteAction
    private val mPlayAction: RemoteAction
    private val mNextAction: RemoteAction
    private val mPrevAction: RemoteAction
    private val mMediaActionReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (mMediaController == null) {
                // no active media session, bail early.
                return
            }
            when (intent.action) {
                ACTION_PLAY -> mMediaController!!.transportControls.play()
                ACTION_PAUSE -> mMediaController!!.transportControls.pause()
                ACTION_NEXT -> mMediaController!!.transportControls.skipToNext()
                ACTION_PREV -> mMediaController!!.transportControls.skipToPrevious()
            }
        }
    }
    private val mPlaybackChangedListener: MediaController.Callback =
        object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                notifyActionsChanged()
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                notifyMetadataChanged(metadata)
            }
        }
    private val mSessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers: List<MediaController>? ->
            resolveActiveMediaController(controllers)
        }
    private val mActionListeners = ArrayList<ActionListener>()
    private val mMetadataListeners = ArrayList<MetadataListener>()
    private val mTokenListeners = ArrayList<TokenListener>()

    init {
        val mediaControlFilter = IntentFilter()
        mediaControlFilter.addAction(ACTION_PLAY)
        mediaControlFilter.addAction(ACTION_PAUSE)
        mediaControlFilter.addAction(ACTION_NEXT)
        mediaControlFilter.addAction(ACTION_PREV)
        mContext.registerReceiverForAllUsers(
            mMediaActionReceiver, mediaControlFilter,
            SYSTEMUI_PERMISSION, mMainHandler, Context.RECEIVER_EXPORTED
        )

        // Creates the standard media buttons that we may show.
        mPauseAction = getDefaultRemoteAction(
            R.string.pip_pause,
            R.drawable.pip_ic_pause_white, ACTION_PAUSE
        )
        mPlayAction = getDefaultRemoteAction(
            R.string.pip_play,
            R.drawable.pip_ic_play_arrow_white, ACTION_PLAY
        )
        mNextAction = getDefaultRemoteAction(
            R.string.pip_skip_to_next,
            R.drawable.pip_ic_skip_next_white, ACTION_NEXT
        )
        mPrevAction = getDefaultRemoteAction(
            R.string.pip_skip_to_prev,
            R.drawable.pip_ic_skip_previous_white, ACTION_PREV
        )
        mMediaSessionManager = mContext.getSystemService(
            MediaSessionManager::class.java
        )
    }

    /**
     * Handles when an activity is pinned.
     */
    fun onActivityPinned() {
        // Once we enter PiP, try to find the active media controller for the top most activity
        resolveActiveMediaController(
            mMediaSessionManager!!.getActiveSessionsForUser(
                null,
                UserHandle.CURRENT
            )
        )
    }

    /**
     * Adds a new media action listener.
     */
    fun addActionListener(listener: ActionListener) {
        if (!mActionListeners.contains(listener)) {
            mActionListeners.add(listener)
            listener.onMediaActionsChanged(mediaActions)
        }
    }

    /**
     * Removes a media action listener.
     */
    fun removeActionListener(listener: ActionListener) {
        listener.onMediaActionsChanged(emptyList<RemoteAction>())
        mActionListeners.remove(listener)
    }

    /**
     * Adds a new media metadata listener.
     */
    fun addMetadataListener(listener: MetadataListener) {
        if (!mMetadataListeners.contains(listener)) {
            mMetadataListeners.add(listener)
            listener.onMediaMetadataChanged(mediaMetadata)
        }
    }

    /**
     * Removes a media metadata listener.
     */
    fun removeMetadataListener(listener: MetadataListener) {
        listener.onMediaMetadataChanged(null)
        mMetadataListeners.remove(listener)
    }

    /**
     * Adds a new token listener.
     */
    fun addTokenListener(listener: TokenListener) {
        if (!mTokenListeners.contains(listener)) {
            mTokenListeners.add(listener)
            listener.onMediaSessionTokenChanged(token)
        }
    }

    /**
     * Removes a token listener.
     */
    fun removeTokenListener(listener: TokenListener) {
        listener.onMediaSessionTokenChanged(null)
        mTokenListeners.remove(listener)
    }

    private val token: MediaSession.Token?
        get() = if (mMediaController == null) {
            null
        } else mMediaController!!.sessionToken
    private val mediaMetadata: MediaMetadata?
        get() = if (mMediaController != null) mMediaController!!.metadata else null

    private val mediaActions: List<RemoteAction?>
        /**
         * Gets the set of media actions currently available.
         */
        get() {
            if (mMediaController == null) {
                return emptyList<RemoteAction>()
            }
            // Cache the PlaybackState since it's a Binder call.
            // Safe because mMediaController is guaranteed non-null here.
            val playbackState: PlaybackState = mMediaController!!.playbackState
                ?: return emptyList<RemoteAction>()
            val mediaActions = ArrayList<RemoteAction?>()
            val isPlaying = playbackState.isActive
            val actions = playbackState.actions

            // Prev action
            mPrevAction.isEnabled =
                actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L
            mediaActions.add(mPrevAction)

            // Play/pause action
            if (!isPlaying && actions and PlaybackState.ACTION_PLAY != 0L) {
                mediaActions.add(mPlayAction)
            } else if (isPlaying && actions and PlaybackState.ACTION_PAUSE != 0L) {
                mediaActions.add(mPauseAction)
            }

            // Next action
            mNextAction.isEnabled =
                actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L
            mediaActions.add(mNextAction)
            return mediaActions
        }

    /** @return Default [RemoteAction] sends broadcast back to SysUI.
     */
    private fun getDefaultRemoteAction(
        @StringRes titleAndDescription: Int,
        @DrawableRes icon: Int,
        action: String
    ): RemoteAction {
        val titleAndDescriptionStr = mContext.getString(titleAndDescription)
        val intent = Intent(action)
        intent.setPackage(mContext.packageName)
        return RemoteAction(
            Icon.createWithResource(mContext, icon),
            titleAndDescriptionStr, titleAndDescriptionStr,
            PendingIntent.getBroadcast(
                mContext, 0 /* requestCode */, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    /**
     * Re-registers the session listener for the current user.
     */
    fun registerSessionListenerForCurrentUser() {
        mMediaSessionManager!!.removeOnActiveSessionsChangedListener(mSessionsChangedListener)
        mMediaSessionManager.addOnActiveSessionsChangedListener(
            null, UserHandle.CURRENT,
            mHandlerExecutor, mSessionsChangedListener
        )
    }

    /**
     * Tries to find and set the active media controller for the top PiP activity.
     */
    private fun resolveActiveMediaController(controllers: List<MediaController>?) {
        if (controllers != null) {
            val topActivity = PipUtils.getTopPipActivity(mContext).first
            if (topActivity != null) {
                for (i in controllers.indices) {
                    val controller = controllers[i]
                    if (controller.packageName == topActivity.packageName) {
                        setActiveMediaController(controller)
                        return
                    }
                }
            }
        }
        setActiveMediaController(null)
    }

    /**
     * Sets the active media controller for the top PiP activity.
     */
    private fun setActiveMediaController(controller: MediaController?) {
        if (controller != mMediaController) {
            if (mMediaController != null) {
                mMediaController!!.unregisterCallback(mPlaybackChangedListener)
            }
            mMediaController = controller
            controller?.registerCallback(mPlaybackChangedListener, mMainHandler)
            notifyActionsChanged()
            notifyMetadataChanged(mediaMetadata)
            notifyTokenChanged(token)

            // TODO(winsonc): Consider if we want to close the PIP after a timeout (like on TV)
        }
    }

    /**
     * Notifies all listeners that the actions have changed.
     */
    private fun notifyActionsChanged() {
        if (mActionListeners.isNotEmpty()) {
            val actions = mediaActions
            mActionListeners.forEach(
                Consumer { l: ActionListener -> l.onMediaActionsChanged(actions) })
        }
    }

    /**
     * Notifies all listeners that the metadata have changed.
     */
    private fun notifyMetadataChanged(metadata: MediaMetadata?) {
        if (mMetadataListeners.isNotEmpty()) {
            mMetadataListeners.forEach(Consumer { l: MetadataListener ->
                l.onMediaMetadataChanged(
                    metadata
                )
            })
        }
    }

    private fun notifyTokenChanged(token: MediaSession.Token?) {
        if (mTokenListeners.isNotEmpty()) {
            mTokenListeners.forEach(Consumer { l: TokenListener ->
                l.onMediaSessionTokenChanged(
                    token
                )
            })
        }
    }

    companion object {
        private const val SYSTEMUI_PERMISSION = "com.android.systemui.permission.SELF"
        private const val ACTION_PLAY = "com.android.wm.shell.pip.PLAY"
        private const val ACTION_PAUSE = "com.android.wm.shell.pip.PAUSE"
        private const val ACTION_NEXT = "com.android.wm.shell.pip.NEXT"
        private const val ACTION_PREV = "com.android.wm.shell.pip.PREV"
    }
}