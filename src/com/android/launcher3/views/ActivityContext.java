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

import static com.android.launcher3.logging.KeyboardStateManager.KeyboardState.HIDE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_KEYBOARD_CLOSED;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.allapps.search.SearchAdapterProvider;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.StringCache;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.util.OnboardingPrefs;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.ViewCache;

/**
 * An interface to be used along with a context for various activities in Launcher. This allows a
 * generic class to depend on Context subclass instead of an Activity.
 */
public interface ActivityContext {

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
     * Returns the FolderIcon with the given item id, if it exists.
     */
    default @Nullable FolderIcon findFolderIcon(final int folderIconId) {
        return null;
    }

    default StatsLogManager getStatsLogManager() {
        return StatsLogManager.newInstance((Context) this);
    }

    /**
     * Returns {@code true} if popups should use color extraction.
     */
    default boolean shouldUseColorExtractionForPopup() {
        return true;
    }

    /**
     * Called just before logging the given item.
     */
    default void applyOverwritesToLogItem(LauncherAtom.ItemInfo.Builder itemInfoBuilder) { }

    /** Onboarding preferences for any onboarding data within this context. */
    @Nullable
    default OnboardingPrefs<?> getOnboardingPrefs() {
        return null;
    }

    /** Returns {@code true} if items are currently being bound within this context. */
    default boolean isBindingItems() {
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

    default View.OnClickListener getItemOnClickListener() {
        return v -> {
            // No op.
        };
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
     * Creates and returns {@link SearchAdapterProvider} for build variant specific search result
     * views.
     */
    @Nullable
    default SearchAdapterProvider<?> createSearchAdapterProvider(
            ActivityAllAppsContainerView<?> appsView) {
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
        if (Utilities.ATLEAST_R) {
            Preconditions.assertUIThread();
            //  Hide keyboard with WindowInsetsController if could. In case
            //  hideSoftInputFromWindow may get ignored by input connection being finished
            //  when the screen is off.
            //
            // In addition, inside IMF, the keyboards are closed asynchronously that launcher no
            // longer need to post to the message queue.
            final WindowInsetsController wic = root.getWindowInsetsController();
            WindowInsets insets = root.getRootWindowInsets();
            boolean isImeShown = insets != null && insets.isVisible(WindowInsets.Type.ime());
            if (wic != null && isImeShown) {
                StatsLogManager slm  = getStatsLogManager();
                slm.keyboardStateManager().setKeyboardState(HIDE);

                // this method cannot be called cross threads
                wic.hide(WindowInsets.Type.ime());
                slm.logger().log(LAUNCHER_ALLAPPS_KEYBOARD_CLOSED);
                return;
            }
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
}
