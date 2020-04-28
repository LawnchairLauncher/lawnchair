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

import static com.android.launcher3.util.SystemUiController.UI_STATE_OVERVIEW;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;

import androidx.annotation.IntDef;

import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.logging.StatsLogUtils;
import com.android.launcher3.logging.StatsLogUtils.LogStateProvider;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.logging.UserEventDispatcher.UserEventDelegate;
import com.android.launcher3.uioverrides.UiFactory;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.ViewCache;
import com.android.launcher3.views.ActivityContext;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.util.ArrayList;

public abstract class BaseActivity extends Activity
        implements UserEventDelegate, LogStateProvider, ActivityContext {

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
    public @interface InvisibilityFlags{}

    private final ArrayList<OnDeviceProfileChangeListener> mDPChangeListeners = new ArrayList<>();
    private final ArrayList<MultiWindowModeChangedListener> mMultiWindowModeChangedListeners =
            new ArrayList<>();

    protected DeviceProfile mDeviceProfile;
    protected UserEventDispatcher mUserEventDispatcher;
    protected StatsLogManager mStatsLogManager;
    protected SystemUiController mSystemUiController;

    private static final int ACTIVITY_STATE_STARTED = 1 << 0;
    private static final int ACTIVITY_STATE_RESUMED = 1 << 1;
    /**
     * State flag indicating if the user is active or the actitvity when to background as a result
     * of user action.
     * @see #isUserActive()
     */
    private static final int ACTIVITY_STATE_USER_ACTIVE = 1 << 2;

    @Retention(SOURCE)
    @IntDef(
            flag = true,
            value = {ACTIVITY_STATE_STARTED, ACTIVITY_STATE_RESUMED, ACTIVITY_STATE_USER_ACTIVE})
    public @interface ActivityFlags{}

    @ActivityFlags
    private int mActivityFlags;

    // When the recents animation is running, the visibility of the Launcher is managed by the
    // animation
    @InvisibilityFlags private int mForceInvisible;

    private final ViewCache mViewCache = new ViewCache();

    public ViewCache getViewCache() {
        return mViewCache;
    }

    @Override
    public DeviceProfile getDeviceProfile() {
        return mDeviceProfile;
    }

    public int getCurrentState() { return StatsLogUtils.LAUNCHER_STATE_BACKGROUND; }

    public void modifyUserEvent(LauncherLogProto.LauncherEvent event) {}

    public final StatsLogManager getStatsLogManager() {
        if (mStatsLogManager == null) {
            mStatsLogManager = StatsLogManager.newInstance(this, this);
        }
        return mStatsLogManager;
    }

    public final UserEventDispatcher getUserEventDispatcher() {
        if (mUserEventDispatcher == null) {
            mUserEventDispatcher = UserEventDispatcher.newInstance(this, this);
        }
        return mUserEventDispatcher;
    }

    public SystemUiController getSystemUiController() {
        if (mSystemUiController == null) {
            mSystemUiController = new SystemUiController(getWindow());
        }
        return mSystemUiController;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onStart() {
        mActivityFlags |= ACTIVITY_STATE_STARTED;
        super.onStart();
    }

    @Override
    protected void onResume() {
        mActivityFlags |= ACTIVITY_STATE_RESUMED | ACTIVITY_STATE_USER_ACTIVE;
        super.onResume();
    }

    @Override
    protected void onUserLeaveHint() {
        mActivityFlags &= ~ACTIVITY_STATE_USER_ACTIVE;
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
        mActivityFlags &= ~ACTIVITY_STATE_STARTED & ~ACTIVITY_STATE_USER_ACTIVE;
        mForceInvisible = 0;
        super.onStop();

        // Reset the overridden sysui flags used for the task-swipe launch animation, this is a
        // catch all for if we do not get resumed (and therefore not paused below)
        getSystemUiController().updateUiState(UI_STATE_OVERVIEW, 0);
    }

    @Override
    protected void onPause() {
        mActivityFlags &= ~ACTIVITY_STATE_RESUMED;
        super.onPause();

        // Reset the overridden sysui flags used for the task-swipe launch animation, we do this
        // here instead of at the end of the animation because the start of the new activity does
        // not happen immediately, which would cause us to reset to launcher's sysui flags and then
        // back to the new app (causing a flash)
        getSystemUiController().updateUiState(UI_STATE_OVERVIEW, 0);
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

    public boolean isUserActive() {
        return (mActivityFlags & ACTIVITY_STATE_USER_ACTIVE) != 0;
    }

    public void addOnDeviceProfileChangeListener(OnDeviceProfileChangeListener listener) {
        mDPChangeListeners.add(listener);
    }

    public void removeOnDeviceProfileChangeListener(OnDeviceProfileChangeListener listener) {
        mDPChangeListeners.remove(listener);
    }

    protected void dispatchDeviceProfileChanged() {
        for (int i = mDPChangeListeners.size() - 1; i >= 0; i--) {
            mDPChangeListeners.get(i).onDeviceProfileChanged(mDeviceProfile);
        }
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
     * @see QuickstepAppTransitionManagerImpl#getWallpaperOpenRunner()
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

    public interface MultiWindowModeChangedListener {
        void onMultiWindowModeChanged(boolean isInMultiWindowMode);
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        if (!UiFactory.dumpActivity(this, writer)) {
            super.dump(prefix, fd, writer, args);
        }
    }

    protected void dumpMisc(String prefix, PrintWriter writer) {
        writer.println(prefix + "deviceProfile isTransposed="
                + getDeviceProfile().isVerticalBarLayout());
        writer.println(prefix + "orientation=" + getResources().getConfiguration().orientation);
        writer.println(prefix + "mSystemUiController: " + mSystemUiController);
        writer.println(prefix + "mActivityFlags: " + mActivityFlags);
        writer.println(prefix + "mForceInvisible: " + mForceInvisible);
    }

    public static <T extends BaseActivity> T fromContext(Context context) {
        if (context instanceof BaseActivity) {
            return (T) context;
        } else if (context instanceof ContextThemeWrapper) {
            return fromContext(((ContextWrapper) context).getBaseContext());
        } else {
            throw new IllegalArgumentException("Cannot find BaseActivity in parent tree");
        }
    }
}
