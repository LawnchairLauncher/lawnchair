/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3;

import static com.android.launcher3.util.FlagDebugUtils.appendFlag;
import static com.android.launcher3.util.FlagDebugUtils.formatFlagChange;
import static com.android.launcher3.util.SystemUiController.UI_STATE_FULLSCREEN_TASK;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.IntDef;

import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.ViewCache;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.ScrimView;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Launcher BaseActivity
 */
public abstract class BaseActivity extends Activity implements ActivityContext {

    private static final String TAG = "BaseActivity";
    static final boolean DEBUG = false;

    public static final int INVISIBLE_BY_STATE_HANDLER = 1 << 0;
    public static final int INVISIBLE_BY_APP_TRANSITIONS = 1 << 1;
    public static final int INVISIBLE_BY_PENDING_FLAGS = 1 << 2;

    // This is not treated as invisibility flag, but adds as a hint for an incomplete transition.
    // When the wallpaper animation runs, it replaces this flag with a proper invisibility
    // flag, INVISIBLE_BY_PENDING_FLAGS only for the duration of that animation.
    public static final int PENDING_INVISIBLE_BY_WALLPAPER_ANIMATION = 1 << 3;

    private static final int INVISIBLE_FLAGS =
            INVISIBLE_BY_STATE_HANDLER | INVISIBLE_BY_APP_TRANSITIONS | INVISIBLE_BY_PENDING_FLAGS;
    public static final int STATE_HANDLER_INVISIBILITY_FLAGS =
            INVISIBLE_BY_STATE_HANDLER | PENDING_INVISIBLE_BY_WALLPAPER_ANIMATION;
    public static final int INVISIBLE_ALL =
            INVISIBLE_FLAGS | PENDING_INVISIBLE_BY_WALLPAPER_ANIMATION;

    @Retention(SOURCE)
    @IntDef(
            flag = true,
            value = {INVISIBLE_BY_STATE_HANDLER, INVISIBLE_BY_APP_TRANSITIONS,
                    INVISIBLE_BY_PENDING_FLAGS, PENDING_INVISIBLE_BY_WALLPAPER_ANIMATION})
    public @interface InvisibilityFlags {
    }

    private final ArrayList<OnDeviceProfileChangeListener> mDPChangeListeners = new ArrayList<>();
    private final ArrayList<MultiWindowModeChangedListener> mMultiWindowModeChangedListeners =
            new ArrayList<>();

    protected DeviceProfile mDeviceProfile;
    protected SystemUiController mSystemUiController;
    private StatsLogManager mStatsLogManager;


    public static final int ACTIVITY_STATE_STARTED = 1 << 0;
    public static final int ACTIVITY_STATE_RESUMED = 1 << 1;

    /**
     * State flags indicating that the activity has received one frame after resume, and was
     * not immediately paused.
     */
    public static final int ACTIVITY_STATE_DEFERRED_RESUMED = 1 << 2;

    public static final int ACTIVITY_STATE_WINDOW_FOCUSED = 1 << 3;

    /**
     * State flag indicating if the user is active or the activity when to background as a result
     * of user action.
     *
     * @see #isUserActive()
     */
    public static final int ACTIVITY_STATE_USER_ACTIVE = 1 << 4;

    /**
     * State flag indicating if the user will be active shortly.
     */
    public static final int ACTIVITY_STATE_USER_WILL_BE_ACTIVE = 1 << 5;

    /**
     * State flag indicating that a state transition is in progress
     */
    public static final int ACTIVITY_STATE_TRANSITION_ACTIVE = 1 << 6;

    @Retention(SOURCE)
    @IntDef(
            flag = true,
            value = {ACTIVITY_STATE_STARTED,
                    ACTIVITY_STATE_RESUMED,
                    ACTIVITY_STATE_DEFERRED_RESUMED,
                    ACTIVITY_STATE_WINDOW_FOCUSED,
                    ACTIVITY_STATE_USER_ACTIVE,
                    ACTIVITY_STATE_TRANSITION_ACTIVE})
    public @interface ActivityFlags {
    }

    /** Returns a human-readable string for the specified {@link ActivityFlags}. */
    public static String getActivityStateString(@ActivityFlags int flags) {
        StringJoiner result = new StringJoiner("|");
        appendFlag(result, flags, ACTIVITY_STATE_STARTED, "state_started");
        appendFlag(result, flags, ACTIVITY_STATE_RESUMED, "state_resumed");
        appendFlag(result, flags, ACTIVITY_STATE_DEFERRED_RESUMED, "state_deferred_resumed");
        appendFlag(result, flags, ACTIVITY_STATE_WINDOW_FOCUSED, "state_window_focused");
        appendFlag(result, flags, ACTIVITY_STATE_USER_ACTIVE, "state_user_active");
        appendFlag(result, flags, ACTIVITY_STATE_TRANSITION_ACTIVE, "state_transition_active");
        return result.toString();
    }

    @ActivityFlags
    private int mActivityFlags;

    // When the recents animation is running, the visibility of the Launcher is managed by the
    // animation
    @InvisibilityFlags
    private int mForceInvisible;

    private final ViewCache mViewCache = new ViewCache();

    @Retention(SOURCE)
    @IntDef({EVENT_STARTED, EVENT_RESUMED, EVENT_STOPPED, EVENT_DESTROYED})
    public @interface ActivityEvent { }
    public static final int EVENT_STARTED = 0;
    public static final int EVENT_RESUMED = 1;
    public static final int EVENT_STOPPED = 2;
    public static final int EVENT_DESTROYED = 3;

    // Callback array that corresponds to events defined in @ActivityEvent
    private final RunnableList[] mEventCallbacks =
            {new RunnableList(), new RunnableList(), new RunnableList(), new RunnableList()};

    @Override
    public ViewCache getViewCache() {
        return mViewCache;
    }

    @Override
    public DeviceProfile getDeviceProfile() {
        return mDeviceProfile;
    }

    @Override
    public List<OnDeviceProfileChangeListener> getOnDeviceProfileChangeListeners() {
        return mDPChangeListeners;
    }

    /**
     * Returns {@link StatsLogManager} for user event logging.
     */
    @Override
    public StatsLogManager getStatsLogManager() {
        if (mStatsLogManager == null) {
            mStatsLogManager = StatsLogManager.newInstance(this);
        }
        return mStatsLogManager;
    }

    public SystemUiController getSystemUiController() {
        if (mSystemUiController == null) {
            mSystemUiController = new SystemUiController(getWindow());
        }
        return mSystemUiController;
    }

    public ScrimView getScrimView() {
        return null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerBackDispatcher();
    }

    @Override
    protected void onStart() {
        addActivityFlags(ACTIVITY_STATE_STARTED);
        super.onStart();
        mEventCallbacks[EVENT_STARTED].executeAllAndClear();
    }

    @Override
    protected void onResume() {
        setResumed();
        super.onResume();
        mEventCallbacks[EVENT_RESUMED].executeAllAndClear();
    }

    @Override
    protected void onUserLeaveHint() {
        removeActivityFlags(ACTIVITY_STATE_USER_ACTIVE);
        super.onUserLeaveHint();
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        for (int i = mMultiWindowModeChangedListeners.size() - 1; i >= 0; i--) {
            mMultiWindowModeChangedListeners.get(i).onMultiWindowModeChanged(isInMultiWindowMode);
        }
    }

    @Override
    protected void onStop() {
        removeActivityFlags(ACTIVITY_STATE_STARTED | ACTIVITY_STATE_USER_ACTIVE);
        mForceInvisible = 0;
        super.onStop();
        mEventCallbacks[EVENT_STOPPED].executeAllAndClear();


        // Reset the overridden sysui flags used for the task-swipe launch animation, this is a
        // catch all for if we do not get resumed (and therefore not paused below)
        getSystemUiController().updateUiState(UI_STATE_FULLSCREEN_TASK, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mEventCallbacks[EVENT_DESTROYED].executeAllAndClear();
    }

    @Override
    protected void onPause() {
        setPaused();
        super.onPause();

        // Reset the overridden sysui flags used for the task-swipe launch animation, we do this
        // here instead of at the end of the animation because the start of the new activity does
        // not happen immediately, which would cause us to reset to launcher's sysui flags and then
        // back to the new app (causing a flash)
        getSystemUiController().updateUiState(UI_STATE_FULLSCREEN_TASK, 0);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            addActivityFlags(ACTIVITY_STATE_WINDOW_FOCUSED);
        } else {
            removeActivityFlags(ACTIVITY_STATE_WINDOW_FOCUSED);
        }
    }

    protected void registerBackDispatcher() {
        if (Utilities.ATLEAST_T) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> {
                        onBackPressed();
                        TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "onBackInvoked");
                    });
        }
    }

    public boolean isStarted() {
        return (mActivityFlags & ACTIVITY_STATE_STARTED) != 0;
    }

    /**
     * isResumed in already defined as a hidden final method in Activity.java
     */
    public boolean hasBeenResumed() {
        return (mActivityFlags & ACTIVITY_STATE_RESUMED) != 0;
    }

    /**
     * Sets the activity to appear as paused.
     */
    public void setPaused() {
        removeActivityFlags(ACTIVITY_STATE_RESUMED | ACTIVITY_STATE_DEFERRED_RESUMED);
    }

    /**
     * Sets the activity to appear as resumed.
     */
    public void setResumed() {
        addActivityFlags(ACTIVITY_STATE_RESUMED | ACTIVITY_STATE_USER_ACTIVE);
        removeActivityFlags(ACTIVITY_STATE_USER_WILL_BE_ACTIVE);
    }

    public boolean isUserActive() {
        return (mActivityFlags & ACTIVITY_STATE_USER_ACTIVE) != 0;
    }

    public int getActivityFlags() {
        return mActivityFlags;
    }

    protected void addActivityFlags(int toAdd) {
        final int oldFlags = mActivityFlags;
        mActivityFlags |= toAdd;
        if (DEBUG) {
            Log.d(TAG, "Launcher flags updated: " + formatFlagChange(mActivityFlags, oldFlags,
                    BaseActivity::getActivityStateString));
        }
        onActivityFlagsChanged(toAdd);
    }

    protected void removeActivityFlags(int toRemove) {
        final int oldFlags = mActivityFlags;
        mActivityFlags &= ~toRemove;
        if (DEBUG) {
            Log.d(TAG, "Launcher flags updated: " + formatFlagChange(mActivityFlags, oldFlags,
                    BaseActivity::getActivityStateString));
        }

        onActivityFlagsChanged(toRemove);
    }

    protected void onActivityFlagsChanged(int changeBits) {
    }

    public void addMultiWindowModeChangedListener(MultiWindowModeChangedListener listener) {
        mMultiWindowModeChangedListeners.add(listener);
    }

    public void removeMultiWindowModeChangedListener(MultiWindowModeChangedListener listener) {
        mMultiWindowModeChangedListeners.remove(listener);
    }

    /**
     * Used to set the override visibility state, used only to handle the transition home with the
     * recents animation.
     *
     * @see QuickstepTransitionManager#createWallpaperOpenRunner
     */
    public void addForceInvisibleFlag(@InvisibilityFlags int flag) {
        mForceInvisible |= flag;
    }

    public void clearForceInvisibleFlag(@InvisibilityFlags int flag) {
        mForceInvisible &= ~flag;
    }

    /**
     * @return Wether this activity should be considered invisible regardless of actual visibility.
     */
    public boolean isForceInvisible() {
        return hasSomeInvisibleFlag(INVISIBLE_FLAGS);
    }

    public boolean hasSomeInvisibleFlag(int mask) {
        return (mForceInvisible & mask) != 0;
    }

    /**
     * Adds a callback for the provided activity event
     */
    public void addEventCallback(@ActivityEvent int event, Runnable callback) {
        mEventCallbacks[event].add(callback);
    }

    /** Removes a previously added callback */
    public void removeEventCallback(@ActivityEvent int event, Runnable callback) {
        mEventCallbacks[event].remove(callback);
    }

    public interface MultiWindowModeChangedListener {
        void onMultiWindowModeChanged(boolean isInMultiWindowMode);
    }

    protected void dumpMisc(String prefix, PrintWriter writer) {
        writer.println(prefix + "deviceProfile isTransposed="
                + getDeviceProfile().isVerticalBarLayout());
        writer.println(prefix + "orientation=" + getResources().getConfiguration().orientation);
        writer.println(prefix + "mSystemUiController: " + mSystemUiController);
        writer.println(prefix + "mActivityFlags: " + getActivityStateString(mActivityFlags));
        writer.println(prefix + "mForceInvisible: " + mForceInvisible);
    }

    public static <T extends BaseActivity> T fromContext(Context context) {
        if (context instanceof BaseActivity) {
            return (T) context;
        } else if (context instanceof ContextWrapper) {
            return fromContext(((ContextWrapper) context).getBaseContext());
        } else {
            throw new IllegalArgumentException("Cannot find BaseActivity in parent tree");
        }
    }
}
