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
import static com.android.launcher3.util.DisplayController.CHANGE_ROTATION;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.app.ActivityOptions;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.app.WallpaperManager.OnColorsChangedListener;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Process;
import android.os.StrictMode;
import android.os.UserHandle;
import android.util.Log;
import android.view.ActionMode;
import android.view.Display;
import android.view.View;
import android.view.WindowInsets.Type;
import android.view.WindowMetrics;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.allapps.search.DefaultSearchAdapterProvider;
import com.android.launcher3.allapps.search.SearchAdapterProvider;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.InstanceIdSequence;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.util.ActivityOptionsWrapper;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.DisplayController.DisplayInfoChangeListener;
import com.android.launcher3.util.DisplayController.Info;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.util.WindowBounds;

/**
 * Extension of BaseActivity allowing support for drag-n-drop
 */
@SuppressWarnings("NewApi")
public abstract class BaseDraggingActivity extends BaseActivity
        implements OnColorsChangedListener, DisplayInfoChangeListener {

    private static final String TAG = "BaseDraggingActivity";

    // When starting an action mode, setting this tag will cause the action mode to be cancelled
    // automatically when user interacts with the launcher.
    public static final Object AUTO_CANCEL_ACTION_MODE = new Object();

    private ActionMode mCurrentActionMode;
    protected boolean mIsSafeModeEnabled;

    private Runnable mOnStartCallback;
    private RunnableList mOnResumeCallbacks = new RunnableList();

    private int mThemeRes = R.style.AppTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mIsSafeModeEnabled = TraceHelper.allowIpcs("isSafeMode",
                () -> getPackageManager().isSafeMode());
        DisplayController.INSTANCE.get(this).addChangeListener(this);

        // Update theme
        if (Utilities.ATLEAST_P) {
            getSystemService(WallpaperManager.class)
                    .addOnColorsChangedListener(this, MAIN_EXECUTOR.getHandler());
        }
        int themeRes = Themes.getActivityThemeRes(this);
        if (themeRes != mThemeRes) {
            mThemeRes = themeRes;
            setTheme(themeRes);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mOnResumeCallbacks.executeAllAndClear();
    }

    public void addOnResumeCallback(Runnable callback) {
        mOnResumeCallbacks.add(callback);
    }

    @Override
    public void onColorsChanged(WallpaperColors wallpaperColors, int which) {
        updateTheme();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateTheme();
    }

    private void updateTheme() {
        if (mThemeRes != Themes.getActivityThemeRes(this)) {
            recreate();
        }
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

    @NonNull
    public ActivityOptionsWrapper getActivityLaunchOptions(View v, @Nullable ItemInfo item) {
        int left = 0, top = 0;
        int width = v.getMeasuredWidth(), height = v.getMeasuredHeight();
        if (v instanceof BubbleTextView) {
            // Launch from center of icon, not entire view
            Drawable icon = ((BubbleTextView) v).getIcon();
            if (icon != null) {
                Rect bounds = icon.getBounds();
                left = (width - bounds.width()) / 2;
                top = v.getPaddingTop();
                width = bounds.width();
                height = bounds.height();
            }
        }
        ActivityOptions options =
                ActivityOptions.makeClipRevealAnimation(v, left, top, width, height);

        options.setLaunchDisplayId(
                (v != null && v.getDisplay() != null) ? v.getDisplay().getDisplayId()
                        : Display.DEFAULT_DISPLAY);
        RunnableList callback = new RunnableList();
        addOnResumeCallback(callback::executeAllAndDestroy);
        return new ActivityOptionsWrapper(options, callback);
    }

    public boolean startActivitySafely(View v, Intent intent, @Nullable ItemInfo item) {
        if (mIsSafeModeEnabled && !PackageManagerHelper.isSystemApp(this, intent)) {
            Toast.makeText(this, R.string.safemode_shortcut_error, Toast.LENGTH_SHORT).show();
            return false;
        }

        Bundle optsBundle = (v != null) ? getActivityLaunchOptions(v, item).toBundle() : null;
        UserHandle user = item == null ? null : item.user;

        // Prepare intent
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (v != null) {
            intent.setSourceBounds(Utilities.getViewBounds(v));
        }
        try {
            boolean isShortcut = (item instanceof WorkspaceItemInfo)
                    && (item.itemType == Favorites.ITEM_TYPE_SHORTCUT
                    || item.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT)
                    && !((WorkspaceItemInfo) item).isPromise();
            if (isShortcut) {
                // Shortcuts need some special checks due to legacy reasons.
                startShortcutIntentSafely(intent, optsBundle, item);
            } else if (user == null || user.equals(Process.myUserHandle())) {
                // Could be launching some bookkeeping activity
                startActivity(intent, optsBundle);
            } else {
                getSystemService(LauncherApps.class).startMainActivity(
                        intent.getComponent(), user, intent.getSourceBounds(), optsBundle);
            }
            if (item != null) {
                InstanceId instanceId = new InstanceIdSequence().newInstanceId();
                logAppLaunch(getStatsLogManager(), item, instanceId);
            }
            return true;
        } catch (NullPointerException | ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Unable to launch. tag=" + item + " intent=" + intent, e);
        }
        return false;
    }

    /**
     * Creates and logs a new app launch event.
     */
    public void logAppLaunch(StatsLogManager statsLogManager, ItemInfo info,
            InstanceId instanceId) {
        statsLogManager.logger().withItemInfo(info).withInstanceId(instanceId)
                .log(LAUNCHER_APP_LAUNCH_TAP);
    }

    private void startShortcutIntentSafely(Intent intent, Bundle optsBundle, ItemInfo info) {
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
        if (Utilities.ATLEAST_P) {
            getSystemService(WallpaperManager.class).removeOnColorsChangedListener(this);
        }
        DisplayController.INSTANCE.get(this).removeChangeListener(this);
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
    public void onDisplayInfoChanged(Context context, Info info, int flags) {
        if ((flags & CHANGE_ROTATION) != 0 && mDeviceProfile.updateIsSeascape(this)) {
            reapplyUi();
        }
    }

    @Override
    public View.OnClickListener getItemOnClickListener() {
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

    /**
     * Creates and returns {@link SearchAdapterProvider} for build variant specific search result
     * views
     */
    public SearchAdapterProvider<?> createSearchAdapterProvider(
            ActivityAllAppsContainerView<?> allApps) {
        return new DefaultSearchAdapterProvider(this);
    }
}
