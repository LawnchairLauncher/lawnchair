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
package com.android.launcher3.views;

import static android.window.SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR;

import static com.android.launcher3.BuildConfig.WIDGETS_ENABLED;
import static com.android.launcher3.LauncherSettings.Animation.DEFAULT_NO_ICON;
import static com.android.launcher3.Utilities.allowBGLaunch;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_KEYBOARD_CLOSED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_APP_LAUNCH_PENDING_INTENT;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_APP_LAUNCH_TAP;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowInsetsCompat;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.DropTargetHandler;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.celllayout.CellPosMapper;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.InstanceIdSequence;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.StringCache;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.util.ActivityOptionsWrapper;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.launcher3.util.ViewCache;

import java.util.List;

/**
 * An interface to be used along with a context for various activities in Launcher. This allows a
 * generic class to depend on Context subclass instead of an Activity.
 */
public interface ActivityContext {

    String TAG = "ActivityContext";

    default boolean finishAutoCancelActionMode() {
        return false;
    }

    default DotInfo getDotInfoForItem(ItemInfo info) {
        return null;
    }

    /**
     * For items with tree hierarchy, notifies the activity to invalidate the parent when a root
     * is invalidated
     * @param info info associated with a root node.
     */
    default void invalidateParent(ItemInfo info) { }

    default AccessibilityDelegate getAccessibilityDelegate() {
        return null;
    }

    default Rect getFolderBoundingBox() {
        return getDeviceProfile().getAbsoluteOpenFolderBounds();
    }

    /**
     * After calling {@link #getFolderBoundingBox()}, we calculate a (left, top) position for a
     * Folder of size width x height to be within those bounds. However, the chosen position may
     * not be visually ideal (e.g. uncanny valley of centeredness), so here's a chance to update it.
     * @param inOutPosition A 2-size array where the first element is the left position of the open
     *     folder and the second element is the top position. Should be updated in place if desired.
     * @param bounds The bounds that the open folder should fit inside.
     * @param width The width of the open folder.
     * @param height The height of the open folder.
     */
    default void updateOpenFolderPosition(int[] inOutPosition, Rect bounds, int width, int height) {
    }

    /**
     * Returns a LayoutInflater that is cloned in this Context, so that Views inflated by it will
     * have the same Context. (i.e. {@link #lookupContext(Context)} will find this ActivityContext.)
     */
    default LayoutInflater getLayoutInflater() {
        if (this instanceof Context) {
            Context context = (Context) this;
            return LayoutInflater.from(context).cloneInContext(context);
        }
        return null;
    }

    /** Called when the first app in split screen has been selected */
    default void startSplitSelection(
            SplitConfigurationOptions.SplitSelectSource splitSelectSource) {
        // Overridden, intentionally empty
    }

    /**
     * @return {@code true} if user has selected the first split app and is in the process of
     *         selecting the second
     */
    default boolean isSplitSelectionActive() {
        // Overridden
        return false;
    }

    /**
     * Handle user tapping on unsupported target when in split selection mode.
     * See {@link #isSplitSelectionActive()}
     *
     * @return {@code true} if this method will handle the incorrect target selection,
     *         {@code false} if it could not be handled or if not possible to handle based on
     *         current split state
     */
    default boolean handleIncorrectSplitTargetSelection() {
        // Overridden
        return false;
    }

    /**
     * The root view to support drag-and-drop and popup support.
     */
    BaseDragLayer getDragLayer();

    /**
     * The all apps container, if it exists in this context.
     */
    default ActivityAllAppsContainerView<?> getAppsView() {
        return null;
    }

    DeviceProfile getDeviceProfile();

    /** Registered {@link OnDeviceProfileChangeListener} instances. */
    List<OnDeviceProfileChangeListener> getOnDeviceProfileChangeListeners();

    /** Notifies listeners of a {@link DeviceProfile} change. */
    default void dispatchDeviceProfileChanged() {
        DeviceProfile deviceProfile = getDeviceProfile();
        List<OnDeviceProfileChangeListener> listeners = getOnDeviceProfileChangeListeners();
        for (int i = listeners.size() - 1; i >= 0; i--) {
            listeners.get(i).onDeviceProfileChanged(deviceProfile);
        }
    }

    /** Register listener for {@link DeviceProfile} changes. */
    default void addOnDeviceProfileChangeListener(OnDeviceProfileChangeListener listener) {
        getOnDeviceProfileChangeListeners().add(listener);
    }

    /** Unregister listener for {@link DeviceProfile} changes. */
    default void removeOnDeviceProfileChangeListener(OnDeviceProfileChangeListener listener) {
        getOnDeviceProfileChangeListeners().remove(listener);
    }

    default ViewCache getViewCache() {
        return new ViewCache();
    }

    /**
     * Controller for supporting item drag-and-drop
     */
    default <T extends DragController> T getDragController() {
        return null;
    }

    /**
     * Handler for actions taken on drop targets that require launcher
     */
    default DropTargetHandler getDropTargetHandler() {
        return null;
    }

    /**
     * Returns the FolderIcon with the given item id, if it exists.
     */
    default @Nullable FolderIcon findFolderIcon(final int folderIconId) {
        return null;
    }

    default StatsLogManager getStatsLogManager() {
        return StatsLogManager.newInstance((Context) this);
    }

    /**
     * Returns {@code true} if popups can use a range of color shades instead of a singular color.
     */
    default boolean canUseMultipleShadesForPopup() {
        return true;
    }

    /**
     * Called just before logging the given item.
     */
    default void applyOverwritesToLogItem(LauncherAtom.ItemInfo.Builder itemInfoBuilder) { }

    /** Returns {@code true} if items are currently being bound within this context. */
    default boolean isBindingItems() {
        return false;
    }

    default View.OnClickListener getItemOnClickListener() {
        return v -> {
            // No op.
        };
    }

    /** Long-click callback used for All Apps items. */
    default View.OnLongClickListener getAllAppsItemLongClickListener() {
        return v -> false;
    }

    @Nullable
    default PopupDataProvider getPopupDataProvider() {
        return null;
    }

    @Nullable
    default StringCache getStringCache() {
        return null;
    }

    /**
     * Hides the keyboard if it is visible
     */
    default void hideKeyboard() {
        View root = getDragLayer();
        if (root == null) {
            return;
        }
        Preconditions.assertUIThread();
        // Hide keyboard with WindowInsetsController if could. In case hideSoftInputFromWindow may
        // get ignored by input connection being finished when the screen is off.
        //
        // In addition, inside IMF, the keyboards are closed asynchronously that launcher no longer
        // need to post to the message queue.
        final WindowInsetsController wic = root.getWindowInsetsController();
        WindowInsets insets = root.getRootWindowInsets();
        boolean isImeShown = insets != null && insets.isVisible(WindowInsets.Type.ime());
        if (wic != null) {
            // Only hide the keyboard if it is actually showing.
            if (isImeShown) {
                // this method cannot be called cross threads
                wic.hide(WindowInsets.Type.ime());
                getStatsLogManager().logger().log(LAUNCHER_ALLAPPS_KEYBOARD_CLOSED);
            }

            // If the WindowInsetsController is not null, we end here regardless of whether we hid
            // the keyboard or not.
            return;
        }

        InputMethodManager imm = root.getContext().getSystemService(InputMethodManager.class);
        IBinder token = root.getWindowToken();
        if (imm != null && token != null) {
            UI_HELPER_EXECUTOR.execute(() -> {
                if (imm.hideSoftInputFromWindow(token, 0)) {
                    // log keyboard close event only when keyboard is actually closed
                    MAIN_EXECUTOR.execute(() ->
                            getStatsLogManager().logger().log(LAUNCHER_ALLAPPS_KEYBOARD_CLOSED));
                }
            });
        }
    }

    /**
     * Returns if the connected keyboard is a hardware keyboard.
     */
    default boolean isHardwareKeyboard() {
        return Configuration.KEYBOARD_QWERTY
                == ((Context) this).getResources().getConfiguration().keyboard;
    }

    /**
     * Returns if the software keyboard (including input toolbar) is hidden. Hardware
     * keyboards do not display on screen by default.
     */
    default boolean isSoftwareKeyboardHidden() {
        if (isHardwareKeyboard()) {
            return true;
        } else {
            View dragLayer = getDragLayer();
            WindowInsets insets = dragLayer.getRootWindowInsets();
            if (insets == null) {
                return false;
            }
            WindowInsetsCompat insetsCompat =
                    WindowInsetsCompat.toWindowInsetsCompat(insets, dragLayer);
            return !insetsCompat.isVisible(WindowInsetsCompat.Type.ime());
        }
    }

    /**
     * Sends a pending intent animating from a view.
     *
     * @param v View to animate.
     * @param intent The pending intent being launched.
     * @param item Item associated with the view.
     * @return RunnableList for listening for animation finish if the activity was properly
     *         or started, {@code null} if the launch finished
     */
    default RunnableList sendPendingIntentWithAnimation(
            @NonNull View v, PendingIntent intent, @Nullable ItemInfo item) {
        ActivityOptionsWrapper options = getActivityLaunchOptions(v, item);
        try {
            intent.send(null, 0, null, null, null, null, options.toBundle());
            if (item != null) {
                InstanceId instanceId = new InstanceIdSequence().newInstanceId();
                getStatsLogManager().logger().withItemInfo(item).withInstanceId(instanceId)
                        .log(LAUNCHER_APP_LAUNCH_PENDING_INTENT);
            }
            return options.onEndCallback;
        } catch (PendingIntent.CanceledException e) {
            Toast.makeText(v.getContext(),
                    v.getContext().getResources().getText(R.string.shortcut_not_available),
                    Toast.LENGTH_SHORT).show();
        }
        return null;
    }

    /**
     * Safely starts an activity.
     *
     * @param v View starting the activity.
     * @param intent Base intent being launched.
     * @param item Item associated with the view.
     * @return RunnableList for listening for animation finish if the activity was properly
     *         or started, {@code null} if the launch finished
     */
    default RunnableList startActivitySafely(
            View v, Intent intent, @Nullable ItemInfo item) {
        Preconditions.assertUIThread();
        Context context = (Context) this;
        if (isAppBlockedForSafeMode() && !PackageManagerHelper.isSystemApp(context, intent)) {
            Toast.makeText(context, R.string.safemode_shortcut_error, Toast.LENGTH_SHORT).show();
            return null;
        }

        boolean isShortcut = (item instanceof WorkspaceItemInfo)
                && item.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
                && !((WorkspaceItemInfo) item).isPromise();
        if (isShortcut && !WIDGETS_ENABLED) {
            return null;
        }
        ActivityOptionsWrapper options = v != null ? getActivityLaunchOptions(v, item)
                : makeDefaultActivityOptions(item != null && item.animationType == DEFAULT_NO_ICON
                        ? SPLASH_SCREEN_STYLE_SOLID_COLOR : -1 /* SPLASH_SCREEN_STYLE_UNDEFINED */);
        UserHandle user = item == null ? null : item.user;
        Bundle optsBundle = options.toBundle();
        // Prepare intent
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (v != null) {
            intent.setSourceBounds(Utilities.getViewBounds(v));
        }
        try {
            if (isShortcut) {
                String id = ((WorkspaceItemInfo) item).getDeepShortcutId();
                String packageName = intent.getPackage();
                ((Context) this).getSystemService(LauncherApps.class).startShortcut(
                        packageName, id, intent.getSourceBounds(), optsBundle, user);
            } else if (user == null || user.equals(Process.myUserHandle())) {
                // Could be launching some bookkeeping activity
                context.startActivity(intent, optsBundle);
            } else {
                context.getSystemService(LauncherApps.class).startMainActivity(
                        intent.getComponent(), user, intent.getSourceBounds(), optsBundle);
            }
            if (item != null) {
                InstanceId instanceId = new InstanceIdSequence().newInstanceId();
                logAppLaunch(getStatsLogManager(), item, instanceId);
            }
            return options.onEndCallback;
        } catch (NullPointerException | ActivityNotFoundException | SecurityException e) {
            Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Unable to launch. tag=" + item + " intent=" + intent, e);
        }
        return null;
    }

    /** Returns {@code true} if an app launch is blocked due to safe mode. */
    default boolean isAppBlockedForSafeMode() {
        return false;
    }

    /**
     * Creates and logs a new app launch event.
     */
    default void logAppLaunch(StatsLogManager statsLogManager, ItemInfo info,
            InstanceId instanceId) {
        statsLogManager.logger().withItemInfo(info).withInstanceId(instanceId)
                .log(LAUNCHER_APP_LAUNCH_TAP);
    }

    /**
     * Returns launch options for an Activity.
     *
     * @param v View initiating a launch.
     * @param item Item associated with the view.
     */
    default ActivityOptionsWrapper getActivityLaunchOptions(View v, @Nullable ItemInfo item) {
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
                allowBGLaunch(ActivityOptions.makeClipRevealAnimation(v, left, top, width, height));
        options.setLaunchDisplayId(
                (v != null && v.getDisplay() != null) ? v.getDisplay().getDisplayId()
                        : Display.DEFAULT_DISPLAY);
        RunnableList callback = new RunnableList();
        return new ActivityOptionsWrapper(options, callback);
    }

    /**
     * Creates a default activity option and we do not want association with any launcher element.
     */
    default ActivityOptionsWrapper makeDefaultActivityOptions(int splashScreenStyle) {
        ActivityOptions options = allowBGLaunch(ActivityOptions.makeBasic());
        if (Utilities.ATLEAST_T) {
            options.setSplashScreenStyle(splashScreenStyle);
        }
        return new ActivityOptionsWrapper(options, new RunnableList());
    }

    default CellPosMapper getCellPosMapper() {
        DeviceProfile dp = getDeviceProfile();
        return new CellPosMapper(dp.isVerticalBarLayout(), dp.numShownHotseatIcons);
    }

    /** Whether bubbles are enabled. */
    default boolean isBubbleBarEnabled() {
        return false;
    }

    /** Whether the bubble bar has bubbles. */
    default boolean hasBubbles() {
        return false;
    }

    /**
     * Returns the ActivityContext associated with the given Context, or throws an exception if
     * the Context is not associated with any ActivityContext.
     */
    static <T extends Context & ActivityContext> T lookupContext(Context context) {
        T activityContext = lookupContextNoThrow(context);
        if (activityContext == null) {
            throw new IllegalArgumentException("Cannot find ActivityContext in parent tree");
        }
        return activityContext;
    }

    /**
     * Returns the ActivityContext associated with the given Context, or null if
     * the Context is not associated with any ActivityContext.
     */
    static <T extends Context & ActivityContext> T lookupContextNoThrow(Context context) {
        if (context instanceof ActivityContext) {
            return (T) context;
        } else if (context instanceof ContextWrapper) {
            return lookupContextNoThrow(((ContextWrapper) context).getBaseContext());
        } else {
            return null;
        }
    }
}
