/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_APP_LAUNCH_TAP;
import static com.android.launcher3.util.DefaultDisplay.CHANGE_ROTATION;

import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Process;
import android.os.StrictMode;
import android.os.UserHandle;
import android.util.Log;
import android.view.ActionMode;
import android.view.Display;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowInsets.Type;
import android.view.WindowMetrics;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.launcher3.Launcher.OnResumeCallback;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.InstanceIdSequence;
import com.android.launcher3.model.AppLaunchTracker;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.uioverrides.WallpaperColorInfo;
import com.android.launcher3.util.DefaultDisplay;
import com.android.launcher3.util.DefaultDisplay.DisplayInfoChangeListener;
import com.android.launcher3.util.DefaultDisplay.Info;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.util.WindowBounds;

/**
 * Extension of BaseActivity allowing support for drag-n-drop
 */
public abstract class BaseDraggingActivity extends BaseActivity
        implements WallpaperColorInfo.OnChangeListener, DisplayInfoChangeListener {

    private static final String TAG = "BaseDraggingActivity";

    // When starting an action mode, setting this tag will cause the action mode to be cancelled
    // automatically when user interacts with the launcher.
    public static final Object AUTO_CANCEL_ACTION_MODE = new Object();

    private ActionMode mCurrentActionMode;
    protected boolean mIsSafeModeEnabled;

    private Runnable mOnStartCallback;

    private int mThemeRes = R.style.AppTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        mIsSafeModeEnabled = TraceHelper.allowIpcs("isSafeMode",
                () -> getPackageManager().isSafeMode());
        DefaultDisplay.INSTANCE.get(this).addChangeListener(this);

        // Update theme
        WallpaperColorInfo.INSTANCE.get(this).addOnChangeListener(this);
        int themeRes = Themes.getActivityThemeRes(this);
        if (themeRes != mThemeRes) {
            mThemeRes = themeRes;
            setTheme(themeRes);
        }
    }

    @Override
    public void onExtractedColorsChanged(WallpaperColorInfo wallpaperColorInfo) {
        updateTheme();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateTheme();
    }

    private void updateTheme() {
        if (mThemeRes != Themes.getActivityThemeRes(this)) {
            // Workaround (b/162812884): The system currently doesn't allow recreating an activity
            // when it is not resumed, in such a case defer recreation until it is possible
            if (hasBeenResumed()) {
                recreate();
            } else {
                addOnResumeCallback(this::recreate);
            }
        }
    }

    protected void addOnResumeCallback(OnResumeCallback callback) {
        // To be overridden
    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        super.onActionModeStarted(mode);
        mCurrentActionMode = mode;
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        super.onActionModeFinished(mode);
        mCurrentActionMode = null;
    }

    @Override
    public boolean finishAutoCancelActionMode() {
        if (mCurrentActionMode != null && AUTO_CANCEL_ACTION_MODE == mCurrentActionMode.getTag()) {
            mCurrentActionMode.finish();
            return true;
        }
        return false;
    }

    public abstract <T extends View> T getOverviewPanel();

    public abstract View getRootView();

    public void returnToHomescreen() {
        // no-op
    }

    public Rect getViewBounds(View v) {
        int[] pos = new int[2];
        v.getLocationOnScreen(pos);
        return new Rect(pos[0], pos[1], pos[0] + v.getWidth(), pos[1] + v.getHeight());
    }

    public final Bundle getActivityLaunchOptionsAsBundle(View v) {
        ActivityOptions activityOptions = getActivityLaunchOptions(v);
        return activityOptions == null ? null : activityOptions.toBundle();
    }

    public abstract ActivityOptions getActivityLaunchOptions(View v);

    public boolean startActivitySafely(View v, Intent intent, @Nullable ItemInfo item,
            @Nullable String sourceContainer) {
        if (mIsSafeModeEnabled && !PackageManagerHelper.isSystemApp(this, intent)) {
            Toast.makeText(this, R.string.safemode_shortcut_error, Toast.LENGTH_SHORT).show();
            return false;
        }

        Bundle optsBundle = (v != null) ? getActivityLaunchOptionsAsBundle(v) : null;
        UserHandle user = item == null ? null : item.user;

        // Prepare intent
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (v != null) {
            intent.setSourceBounds(getViewBounds(v));
        }
        try {
            boolean isShortcut = (item instanceof WorkspaceItemInfo)
                    && (item.itemType == Favorites.ITEM_TYPE_SHORTCUT
                    || item.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT)
                    && !((WorkspaceItemInfo) item).isPromise();
            if (isShortcut) {
                // Shortcuts need some special checks due to legacy reasons.
                startShortcutIntentSafely(intent, optsBundle, item, sourceContainer);
            } else if (user == null || user.equals(Process.myUserHandle())) {
                // Could be launching some bookkeeping activity
                startActivity(intent, optsBundle);
                AppLaunchTracker.INSTANCE.get(this).onStartApp(intent.getComponent(),
                        Process.myUserHandle(), sourceContainer);
            } else {
                getSystemService(LauncherApps.class).startMainActivity(
                        intent.getComponent(), user, intent.getSourceBounds(), optsBundle);
                AppLaunchTracker.INSTANCE.get(this).onStartApp(intent.getComponent(), user,
                        sourceContainer);
            }
            getUserEventDispatcher().logAppLaunch(v, intent, user);
            if (item != null) {
                InstanceId instanceId = new InstanceIdSequence().newInstanceId();
                logAppLaunch(item, instanceId);
            }
            return true;
        } catch (NullPointerException|ActivityNotFoundException|SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Unable to launch. tag=" + item + " intent=" + intent, e);
        }
        return false;
    }

    protected void logAppLaunch(ItemInfo info, InstanceId instanceId) {
        getStatsLogManager().logger().withItemInfo(info).withInstanceId(instanceId)
                .log(LAUNCHER_APP_LAUNCH_TAP);
    }

    private void startShortcutIntentSafely(Intent intent, Bundle optsBundle, ItemInfo info,
            @Nullable String sourceContainer) {
        try {
            StrictMode.VmPolicy oldPolicy = StrictMode.getVmPolicy();
            try {
                // Temporarily disable deathPenalty on all default checks. For eg, shortcuts
                // containing file Uri's would cause a crash as penaltyDeathOnFileUriExposure
                // is enabled by default on NYC.
                StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll()
                        .penaltyLog().build());

                if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                    String id = ((WorkspaceItemInfo) info).getDeepShortcutId();
                    String packageName = intent.getPackage();
                    startShortcut(packageName, id, intent.getSourceBounds(), optsBundle, info.user);
                    AppLaunchTracker.INSTANCE.get(this).onStartShortcut(packageName, id, info.user,
                            sourceContainer);
                } else {
                    // Could be launching some bookkeeping activity
                    startActivity(intent, optsBundle);
                }
            } finally {
                StrictMode.setVmPolicy(oldPolicy);
            }
        } catch (SecurityException e) {
            if (!onErrorStartingShortcut(intent, info)) {
                throw e;
            }
        }
    }

    protected boolean onErrorStartingShortcut(Intent intent, ItemInfo info) {
        return false;
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (mOnStartCallback != null) {
            mOnStartCallback.run();
            mOnStartCallback = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WallpaperColorInfo.INSTANCE.get(this).removeOnChangeListener(this);
        DefaultDisplay.INSTANCE.get(this).removeChangeListener(this);
    }

    public void runOnceOnStart(Runnable action) {
        mOnStartCallback = action;
    }

    public void clearRunOnceOnStartCallback() {
        mOnStartCallback = null;
    }

    protected void onDeviceProfileInitiated() {
        if (mDeviceProfile.isVerticalBarLayout()) {
            mDeviceProfile.updateIsSeascape(this);
        }
    }

    @Override
    public void onDisplayInfoChanged(Info info, int flags) {
        if ((flags & CHANGE_ROTATION) != 0 && mDeviceProfile.updateIsSeascape(this)) {
            reapplyUi();
        }
    }

    public OnClickListener getItemOnClickListener() {
        return ItemClickHandler.INSTANCE;
    }

    protected abstract void reapplyUi();

    protected WindowBounds getMultiWindowDisplaySize() {
        if (Utilities.ATLEAST_R) {
            WindowMetrics wm = getWindowManager().getCurrentWindowMetrics();

            Insets insets = wm.getWindowInsets().getInsets(Type.systemBars());
            return new WindowBounds(wm.getBounds(),
                    new Rect(insets.left, insets.top, insets.right, insets.bottom));
        }
        // Note: Calls to getSize() can't rely on our cached DefaultDisplay since it can return
        // the app window size
        Display display = getWindowManager().getDefaultDisplay();
        Point mwSize = new Point();
        display.getSize(mwSize);
        return new WindowBounds(new Rect(0, 0, mwSize.x, mwSize.y), new Rect());
    }
}
