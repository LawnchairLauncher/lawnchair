package ch.deletescape.lawnchair.smartspace;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.MediaSessionManager.OnActiveSessionsChangedListener;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;

import ch.deletescape.lawnchair.smartspace.NotificationsManager.OnChangeListener;
import com.android.launcher3.Utilities;
import com.android.launcher3.notification.NotificationListener;

import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Paused mode is not supported on Marshmallow because the MediaSession is missing
 * notifications. Without this information, it is impossible to hide on stop.
 */
public class MediaListener extends MediaController.Callback
        implements OnActiveSessionsChangedListener, OnChangeListener {
    private static final String TAG = "MediaListener";

    private final ComponentName mComponent;
    private final MediaSessionManager mManager;
    private final Runnable mOnChange;
    private final NotificationsManager mNotificationsManager;
    private List<MediaController> mControllers = Collections.emptyList();
    private MediaNotificationController mTracking;
    private final Handler mHandler = new Handler();
    private final Handler mWorkHandler;

    MediaListener(Context context, Runnable onChange, Handler handler) {
        mComponent = new ComponentName(context, NotificationListener.class);
        mManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        mOnChange = onChange;
        mNotificationsManager = NotificationsManager.getInstance();
        mWorkHandler = handler;
    }

    void onResume() {
        try {
            mManager.addOnActiveSessionsChangedListener(this, mComponent);
        } catch (SecurityException ignored) {
        }
        onActiveSessionsChanged(null); // Bind all current controllers.
        mNotificationsManager.addListener(this);
    }

    void onPause() {
        mManager.removeOnActiveSessionsChangedListener(this);
        onActiveSessionsChanged(Collections.emptyList()); // Unbind all previous controllers.
        mNotificationsManager.removeListener(this);
    }

    MediaNotificationController getTracking() {
        return mTracking;
    }

    String getPackage() {
        return mTracking.controller.getPackageName();
    }

    private void updateControllers(List<MediaController> controllers) {
        for (MediaController mc : mControllers) {
            mc.unregisterCallback(this);
        }
        for (MediaController mc : controllers) {
            mc.registerCallback(this);
        }
        mControllers = controllers;
    }

    @Override
    public void onActiveSessionsChanged(List<MediaController> controllers) {
        mWorkHandler.post(() -> updateTracking(controllers));
    }

    private void updateTracking(List<MediaController> controllers) {
        if (controllers == null) {
            try {
                controllers = mManager.getActiveSessions(mComponent);
            } catch (SecurityException ignored) {
                controllers = Collections.emptyList();
            }
        }
        updateControllers(controllers);

        if (mTracking != null) {
            mTracking.reloadInfo();
        }

        // If the current controller is not paused or playing, stop tracking it.
        if (mTracking != null
                && (!controllers.contains(mTracking.controller) || !mTracking.isPausedOrPlaying())) {
            mTracking = null;
        }

        for (MediaController mc : controllers) {
            MediaNotificationController mnc = new MediaNotificationController(mc);
            // Either we are not tracking a controller and this one is valid,
            // or this one is playing while the one we track is not.
            if ((mTracking == null && mnc.isPausedOrPlaying())
                    || (mTracking != null && mnc.isPlaying() && !mTracking.isPlaying())) {
                mTracking = mnc;
            }
        }

        mHandler.removeCallbacks(mOnChange);
        mHandler.post(mOnChange);
    }

    private void pressButton(int keyCode) {
        if (mTracking != null) {
            mTracking.pressButton(keyCode);
        }
    }

    void toggle(boolean finalClick) {
        if (!finalClick) {
            Log.d(TAG, "Toggle");
            pressButton(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        }
    }

    void next(boolean finalClick) {
        if (finalClick) {
            Log.d(TAG, "Next");
            pressButton(KeyEvent.KEYCODE_MEDIA_NEXT);
            pressButton(KeyEvent.KEYCODE_MEDIA_PLAY);
        }
    }

    void previous(boolean finalClick) {
        if (finalClick) {
            Log.d(TAG, "Previous");
            pressButton(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            pressButton(KeyEvent.KEYCODE_MEDIA_PLAY);
        }
    }

    // If there is no notification, consider the state to be stopped.
    private boolean hasNotification(@Nullable MediaController mc) {
        return findNotification(mc) != null;
    }

    private StatusBarNotification findNotification(@Nullable MediaController mc) {
        if (mc == null) return null;
        MediaSession.Token controllerToken = mc.getSessionToken();
        for (StatusBarNotification notif : mNotificationsManager.getNotifications()) {
            Bundle extras = notif.getNotification().extras;
            MediaSession.Token notifToken = extras.getParcelable(Notification.EXTRA_MEDIA_SESSION);
            if (controllerToken.equals(notifToken)) {
                return notif;
            }
        }
        return null;
    }

    /**
     * Events that refresh the current handler.
     */
    public void onPlaybackStateChanged(PlaybackState state) {
        super.onPlaybackStateChanged(state);
        onActiveSessionsChanged(null);
    }

    public void onMetadataChanged(MediaMetadata metadata) {
        super.onMetadataChanged(metadata);
        onActiveSessionsChanged(null);
    }

    @Override
    public void onNotificationsChanged() {
        onActiveSessionsChanged(null);
    }

    public class MediaInfo {

        private CharSequence title;
        private CharSequence artist;
        private CharSequence album;

        public CharSequence getTitle() {
            return title;
        }

        public CharSequence getArtist() {
            return artist;
        }

        public CharSequence getAlbum() {
            return album;
        }
    }

    class MediaNotificationController {

        private MediaController controller;
        private StatusBarNotification sbn;
        private MediaInfo info;

        private MediaNotificationController(MediaController controller) {
            this.controller = controller;
            this.sbn = findNotification(controller);
            reloadInfo();
        }

        private boolean hasNotification() {
            return sbn != null;
        }

        private boolean hasTitle() {
            return info != null && info.title != null;
        }

        private boolean isPlaying() {
            return (!Utilities.ATLEAST_NOUGAT || hasNotification())
                    && hasTitle()
                    && controller.getPlaybackState() != null
                    && controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING;
        }

        private boolean isPausedOrPlaying() {
            if (Utilities.ATLEAST_NOUGAT) {
                if (!hasNotification() || !hasTitle() || controller.getPlaybackState() == null) {
                    return false;
                }
                int state = controller.getPlaybackState().getState();
                return state == PlaybackState.STATE_PAUSED
                        || state == PlaybackState.STATE_PLAYING;
            }
            return isPlaying();
        }

        private void pressButton(int keyCode) {
            controller.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            controller.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        }

        private void reloadInfo() {
            MediaMetadata metadata = controller.getMetadata();
            if (metadata != null) {
                info = new MediaInfo();
                info.title = metadata.getText(MediaMetadata.METADATA_KEY_TITLE);
                info.artist = metadata.getText(MediaMetadata.METADATA_KEY_ARTIST);
                info.album = metadata.getText(MediaMetadata.METADATA_KEY_ALBUM);
            } else if (sbn != null) {
                info = new MediaInfo();
                info.title = sbn.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE);
            }
        }

        public String getPackageName() {
            return controller.getPackageName();
        }

        public StatusBarNotification getSbn() {
            return sbn;
        }

        public MediaInfo getInfo() {
            return info;
        }
    }
}
