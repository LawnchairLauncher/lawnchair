package app.lawnchair.nexuslauncher;

import static android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE;
import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import com.android.launcher3.Launcher;
import com.android.launcher3.Utilities;
import com.android.systemui.plugins.shared.LauncherOverlayManager;
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlay;
import com.google.android.libraries.launcherclient.ISerializableScrollCallback;
import com.google.android.libraries.launcherclient.LauncherClient;
import com.google.android.libraries.launcherclient.LauncherClientCallbacks;
import com.google.android.libraries.launcherclient.StaticInteger;
import com.patrykmichalik.preferencemanager.PreferenceExtensionsKt;

import app.lawnchair.FeedBridge;
import app.lawnchair.LawnchairLauncher;
import app.lawnchair.preferences2.PreferenceManager2;

/**
 * Implements {@link LauncherOverlay} and passes all the corresponding events to {@link
 * LauncherClient}. {@see setClient}
 *
 * <p>Implements {@link LauncherClientCallbacks} and sends all the corresponding callbacks to {@link
 * Launcher}.
 */
public class OverlayCallbackImpl
        implements LauncherOverlay, LauncherClientCallbacks, LauncherOverlayManager,
        ISerializableScrollCallback {

    final static String PREF_PERSIST_FLAGS = "pref_persistent_flags";

    private final Launcher mLauncher;
    private final LauncherClient mClient;
    boolean mFlagsChanged = false;
    private LauncherOverlayCallbacks mLauncherOverlayCallbacks;
    private boolean mWasOverlayAttached = false;
    private int mFlags;

    public OverlayCallbackImpl(LawnchairLauncher launcher) {
        PreferenceManager2 preferenceManager2 = PreferenceManager2.getInstance(launcher);
        Boolean enableFeed = PreferenceExtensionsKt.firstBlocking(preferenceManager2.getEnableFeed());

        mLauncher = launcher;
        mClient = new LauncherClient(mLauncher, this, new StaticInteger(
                (enableFeed ? 1 : 0) | 2 | 4 | 8));
    }

    public static boolean minusOneAvailable(Context context) {
        return FeedBridge.useBridge(context)
                || ((context.getApplicationInfo().flags & (FLAG_DEBUGGABLE | FLAG_SYSTEM)) != 0);
    }

    @Override
    public void onDeviceProvideChanged() {
        mClient.redraw();
    }

    @Override
    public void onAttachedToWindow() {
        mClient.onAttachedToWindow();
    }

    @Override
    public void onDetachedFromWindow() {
        mClient.onDetachedFromWindow();
    }

    @Override
    public void openOverlay() {
        mClient.showOverlay(true);
    }

    @Override
    public void hideOverlay(boolean animate) {
        mClient.hideOverlay(animate);
    }

    @Override
    public void hideOverlay(int duration) {
        mClient.hideOverlay(duration);
    }

    @Override
    public boolean startSearch(byte[] config, Bundle extras) {
        return false;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {
        // Not called
    }

    @Override
    public void onActivityStarted(Activity activity) {
        mClient.onStart();
    }

    @Override
    public void onActivityResumed(Activity activity) {
        mClient.onResume();
    }

    @Override
    public void onActivityPaused(Activity activity) {
        mClient.onPause();
    }

    @Override
    public void onActivityStopped(Activity activity) {
        mClient.onStop();
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        mClient.onDestroy();
        mClient.mDestroyed = true;
    }

    @Override
    public void onOverlayScrollChanged(float progress) {
        if (mLauncherOverlayCallbacks != null) {
            mLauncherOverlayCallbacks.onScrollChanged(progress);
        }
    }

    @Override
    public void onServiceStateChanged(boolean overlayAttached, boolean hotwordActive) {
        this.onServiceStateChanged(overlayAttached);
    }

    @Override
    public void onServiceStateChanged(boolean overlayAttached) {
        if (overlayAttached != mWasOverlayAttached) {
            mWasOverlayAttached = overlayAttached;
            mLauncher.setLauncherOverlay(overlayAttached ? this : null);
        }
    }

    @Override
    public void onScrollInteractionBegin() {
        mClient.startScroll();
    }

    @Override
    public void onScrollInteractionEnd() {
        mClient.endScroll();
    }

    @Override
    public void onScrollChange(float progress, boolean rtl) {
        mClient.setScroll(progress);
    }

    @Override
    public void setOverlayCallbacks(LauncherOverlayCallbacks callbacks) {
        mLauncherOverlayCallbacks = callbacks;
    }

    @Override
    public void setPersistentFlags(int flags) {
        flags &= (8 | 16);
        if (flags != mFlags) {
            mFlagsChanged = true;
            mFlags = flags;
            Utilities.getDevicePrefs(mLauncher).edit().putInt(PREF_PERSIST_FLAGS, flags).apply();
        }
    }
}