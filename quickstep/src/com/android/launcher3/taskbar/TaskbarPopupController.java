/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.taskbar;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.model.data.AppInfo.COMPONENT_KEY_COMPARATOR;
import static com.android.launcher3.util.SplitConfigurationOptions.getLogEventForPosition;

import android.content.Intent;
import android.content.pm.LauncherApps;
import android.graphics.Point;
import android.os.UserHandle;
import android.util.Pair;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.logging.InstanceId;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Flags;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.splitscreen.SplitShortcut;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.launcher3.views.ActivityContext;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.LogUtils;
import com.android.quickstep.util.SingleTask;
import com.android.systemui.shared.recents.model.Task;
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper;
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implements interfaces required to show and allow interacting with a PopupContainerWithArrow.
 * Controls the long-press menu on Taskbar and AllApps icons.
 */
public class TaskbarPopupController implements TaskbarControllers.LoggableTaskbarController {

    private static final SystemShortcut.Factory<BaseTaskbarContext>
            APP_INFO = SystemShortcut.AppInfo::new;

    private static final SystemShortcut.Factory<BaseTaskbarContext>
            BUBBLE = SystemShortcut.BubbleShortcut::new;

    private final TaskbarActivityContext mContext;
    private final PopupDataProvider mPopupDataProvider;

    // Initialized in init.
    private TaskbarControllers mControllers;
    private boolean mAllowInitialSplitSelection;
    private AppInfo[] mAppInfosList = AppInfo.EMPTY_ARRAY;
    // Saves the ItemInfos in the hotseat without the predicted items.
    private SparseArray<ItemInfo> mHotseatInfosList;
    private ManageWindowsTaskbarShortcut<BaseTaskbarContext> mManageWindowsTaskbarShortcut;


    public TaskbarPopupController(TaskbarActivityContext context) {
        mContext = context;
        mPopupDataProvider = new PopupDataProvider(mContext);
    }

    public void init(TaskbarControllers controllers) {
        mControllers = controllers;

        NotificationListener.addNotificationsChangedListener(mPopupDataProvider);
    }

    public void onDestroy() {
        NotificationListener.removeNotificationsChangedListener(mPopupDataProvider);
    }

    @NonNull
    public PopupDataProvider getPopupDataProvider() {
        return mPopupDataProvider;
    }

    public void setDeepShortcutMap(HashMap<ComponentKey, Integer> deepShortcutMapCopy) {
        mPopupDataProvider.setDeepShortcutMap(deepShortcutMapCopy);
    }

    /** Closes the multi-instance menu if it is enabled and currently open. */
    public void maybeCloseMultiInstanceMenu() {
        if (Flags.enableMultiInstanceMenuTaskbar() && mManageWindowsTaskbarShortcut != null) {
            mManageWindowsTaskbarShortcut.closeMultiInstanceMenu();
            cleanUpMultiInstanceMenuReference();
        }
    }

    /** Releases the reference to the Taskbar multi-instance menu */
    public void cleanUpMultiInstanceMenuReference() {
        mManageWindowsTaskbarShortcut = null;
    }

    public void setAllowInitialSplitSelection(boolean allowInitialSplitSelection) {
        mAllowInitialSplitSelection = allowInitialSplitSelection;
    }

    /**
     * Shows the notifications and deep shortcuts associated with a Taskbar {@param icon}.
     * @return the container if shown or null.
     */
    public PopupContainerWithArrow<BaseTaskbarContext> showForIcon(BubbleTextView icon) {
        BaseTaskbarContext context = ActivityContext.lookupContext(icon.getContext());
        if (PopupContainerWithArrow.getOpen(context) != null) {
            // There is already an items container open, so don't open this one.
            icon.clearFocus();
            return null;
        }

        ItemInfo itemInfo = null;
        if (icon.getTag() instanceof ItemInfo item && ShortcutUtil.supportsShortcuts(item)) {
            itemInfo = item;
        } else if (PinToTaskbarShortcut.Companion.isPinningAppWithContextMenuEnabled(mContext)
                && icon.getTag() instanceof SingleTask task) {
            Task.TaskKey key = task.getTask().getKey();
            AppInfo appInfo = getApp(
                    new ComponentKey(key.getComponent(), UserHandle.of(key.userId)));
            if (appInfo != null) {
                WorkspaceItemInfo wif = appInfo.makeWorkspaceItem(icon.getContext());
                itemInfo = SingleTask.Companion.createTaskItemInfo(task, wif);
            }
        }

        if (itemInfo == null) {
            return null;
        }

        PopupContainerWithArrow<BaseTaskbarContext> container;
        int deepShortcutCount = mPopupDataProvider.getShortcutCountForItem(itemInfo);
        // TODO(b/198438631): add support for INSTALL shortcut factory
        final ItemInfo finalInfo = itemInfo;
        List<SystemShortcut> systemShortcuts = getSystemShortcuts()
                .map(s -> s.getShortcut(context, finalInfo, icon))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // TODO(b/375648361): Revisit to see if this can be implemented within getSystemShortcuts().
        if (PinToTaskbarShortcut.Companion.isPinningAppWithContextMenuEnabled(mContext)) {
            SystemShortcut shortcut = createPinShortcut(context, itemInfo, icon);
            if (shortcut != null) {
                systemShortcuts.add(0, shortcut);
            }
        }

        container = (PopupContainerWithArrow) context.getLayoutInflater().inflate(
                R.layout.popup_container, context.getDragLayer(), false);
        container.populateAndShowRows(icon, itemInfo, deepShortcutCount, systemShortcuts);

        // TODO (b/198438631): configure for taskbar/context
        container.setPopupItemDragHandler(new TaskbarPopupItemDragHandler());
        mControllers.taskbarDragController.addDragListener(container);
        container.requestFocus();

        // Make focusable to receive back events
        context.onPopupVisibilityChanged(true);
        container.addOnCloseCallback(() -> {
            context.getDragLayer().post(() -> context.onPopupVisibilityChanged(false));
        });

        return container;
    }

    // Create a Stream of all applicable system shortcuts
    private Stream<SystemShortcut.Factory> getSystemShortcuts() {
        // append split options to APP_INFO shortcut if not in Desktop Windowing mode, the order
        // here will reflect in the popup
        ArrayList<SystemShortcut.Factory> shortcuts = new ArrayList<>();
        shortcuts.add(APP_INFO);
        if (!mControllers.taskbarDesktopModeController
                .isInDesktopModeAndNotInOverview(mContext.getDisplayId())) {
            shortcuts.addAll(mControllers.uiController.getSplitMenuOptions().toList());
        }
        if (BubbleAnythingFlagHelper.enableCreateAnyBubble()) {
            shortcuts.add(BUBBLE);
        }

        if (Flags.enableMultiInstanceMenuTaskbar()
                && DesktopModeStatus.canEnterDesktopMode(mContext)
                && !mControllers.taskbarStashController.isInOverview()) {
            maybeCloseMultiInstanceMenu();
            shortcuts.addAll(getMultiInstanceMenuOptions().toList());
        }
        return shortcuts.stream();
    }

    @Nullable
    @VisibleForTesting
    SystemShortcut createPinShortcut(BaseTaskbarContext target, ItemInfo itemInfo,
            BubbleTextView originalView) {
        // Predicted items use {@code HotseatPredictionController.PinPrediction} shortcut to pin.
        if (itemInfo.isPredictedItem()) {
            return null;
        }
        if (itemInfo.container == CONTAINER_HOTSEAT) {
            return new PinToTaskbarShortcut<>(target, itemInfo, originalView, false,
                    mHotseatInfosList);
        }

        if (itemInfo.container == CONTAINER_ALL_APPS) {
            // If the target ItemInfo is already pinned on taskbar. Show the unpin option instead.
            for (int i = 0; i < mHotseatInfosList.size(); i++) {
                if (Objects.equals(mHotseatInfosList.valueAt(i).getComponentKey(),
                        itemInfo.getComponentKey())) {
                    return new PinToTaskbarShortcut<>(target, itemInfo, originalView, false,
                            mHotseatInfosList);
                }
            }
        }

        if (mHotseatInfosList.size()
                < mContext.getTaskbarSpecsEvaluator().getNumShownHotseatIcons()) {
            return new PinToTaskbarShortcut<>(target, itemInfo, originalView, true,
                    mHotseatInfosList);
        }

        return null;
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarPopupController:");

        mPopupDataProvider.dump(prefix + "\t", pw);
    }

    private class TaskbarPopupItemDragHandler implements
            PopupContainerWithArrow.PopupItemDragHandler {

        protected final Point mIconLastTouchPos = new Point();

        TaskbarPopupItemDragHandler() {}

        @Override
        public boolean onTouch(View view, MotionEvent ev) {
            // Touched a shortcut, update where it was touched so we can drag from there on
            // long click.
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    mIconLastTouchPos.set((int) ev.getX(), (int) ev.getY());
                    break;
            }
            return false;
        }

        @Override
        public boolean onLongClick(View v) {
            // Return early if not the correct view
            if (!(v.getParent() instanceof DeepShortcutView)) return false;

            DeepShortcutView sv = (DeepShortcutView) v.getParent();
            sv.setWillDrawIcon(false);

            // Move the icon to align with the center-top of the touch point
            Point iconShift = new Point();
            iconShift.x = mIconLastTouchPos.x - sv.getIconCenter().x;
            iconShift.y = mIconLastTouchPos.y
                    - mContext.getDeviceProfile()
                    .getTaskbarProfile()
                    .getIconSize();

            ((TaskbarDragController) ActivityContext.lookupContext(
                    v.getContext()).getDragController()).startDragOnLongClick(sv, iconShift);

            return false;
        }
    }

    /**
     * Creates a factory function representing a single "split position" menu item ("Split left,"
     * "Split right," or "Split top").
     * @param position A SplitPositionOption representing whether we are splitting top, left, or
     *                 right.
     * @return A factory function to be used in populating the long-press menu.
     */
    SystemShortcut.Factory<BaseTaskbarContext> createSplitShortcutFactory(
            SplitPositionOption position) {
        return (context, itemInfo, originalView) -> new TaskbarSplitShortcut(context, itemInfo,
                originalView, position, mAllowInitialSplitSelection);
    }

    /**
     * Set the list of AppInfos to be able to pull from later
     */
    public void setApps(AppInfo[] apps) {
        mAppInfosList = apps;
    }

    /**
     * Finds and returns an AppInfo object from a list, using its ComponentKey for identification.
     * Based off of {@link com.android.launcher3.allapps.AllAppsStore#getApp(ComponentKey)}
     * since we cannot access AllAppsStore from here.
     */
    public AppInfo getApp(ComponentKey key) {
        if (key == null) {
            return null;
        }
        AppInfo tempInfo = new AppInfo();
        tempInfo.componentName = key.componentName;
        tempInfo.user = key.user;
        int index = Arrays.binarySearch(mAppInfosList, tempInfo, COMPONENT_KEY_COMPARATOR);
        return index < 0 ? null : mAppInfosList[index];
    }

    public void setHotseatInfosList(SparseArray<ItemInfo> info) {
        mHotseatInfosList = info;
    }

    /**
     * Returns a stream of Multi Instance menu options if an app supports it.
     */
    Stream<SystemShortcut.Factory<BaseTaskbarContext>> getMultiInstanceMenuOptions() {
        SystemShortcut.Factory<BaseTaskbarContext> f1 = createNewWindowShortcutFactory();
        SystemShortcut.Factory<BaseTaskbarContext> f2 = createManageWindowsShortcutFactory();
        return f1 != null ? Stream.of(f1, f2) : Stream.empty();
    }

    /**
     * Creates a factory function representing a "New Window" menu item only if the calling app
     * supports multi-instance.
     * @return A factory function to be used in populating the long-press menu.
     */
    SystemShortcut.Factory<BaseTaskbarContext> createNewWindowShortcutFactory() {
        return (context, itemInfo, originalView) -> {
            if (shouldShowMultiInstanceOptions(itemInfo)) {
                return new NewWindowTaskbarShortcut<>(context, itemInfo, originalView);
            }
            return null;
        };
    }

    /**
     * Creates a factory function representing a "Manage Windows" menu item only if the calling app
     * supports multi-instance. This menu item shows the open instances of the calling app.
     * @return A factory function to be used in populating the long-press menu.
     */
    public SystemShortcut.Factory<BaseTaskbarContext> createManageWindowsShortcutFactory() {
        return (context, itemInfo, originalView) -> {
            if (shouldShowMultiInstanceOptions(itemInfo)) {
                mManageWindowsTaskbarShortcut = new ManageWindowsTaskbarShortcut<>(
                        context, itemInfo, originalView, mControllers);
                return mManageWindowsTaskbarShortcut;
            }
            return null;
        };
    }

    /**
     * Determines whether to show multi-instance options for a given item.
     */
    private boolean shouldShowMultiInstanceOptions(ItemInfo itemInfo) {
        ComponentKey key = itemInfo.getComponentKey();
        AppInfo app = getApp(key);
        return app != null && app.supportsMultiInstance()
                && itemInfo.container != CONTAINER_ALL_APPS;
    }

    /**
     * A single menu item ("Split left," "Split right," or "Split top") that executes a split
     * from the taskbar, as if the user performed a drag and drop split.
     * Includes an onClick method that initiates the actual split.
     */
    private static class TaskbarSplitShortcut extends
             SplitShortcut<BaseTaskbarContext> {
         /**
          * If {@code true}, clicking this shortcut will not attempt to start a split app directly,
          * but be the first app in split selection mode
          */
         private final boolean mAllowInitialSplitSelection;

         TaskbarSplitShortcut(BaseTaskbarContext context, ItemInfo itemInfo, View originalView,
                SplitPositionOption position, boolean allowInitialSplitSelection) {
             super(position.iconResId, position.textResId, context, itemInfo, originalView,
                     position);
             mAllowInitialSplitSelection = allowInitialSplitSelection;
         }

        @Override
        public void onClick(View view) {
            // Add callbacks depending on what type of Taskbar context we're in (Taskbar or AllApps)
            mTarget.onSplitScreenMenuButtonClicked();
            AbstractFloatingView.closeAllOpenViews(mTarget);

            // Depending on what app state we're in, we either want to initiate the split screen
            // staging process or immediately launch a split with an existing app.
            // - Initiate the split screen staging process
             if (mAllowInitialSplitSelection) {
                 super.onClick(view);
                 return;
             }

            // - Immediately launch split with the running app
            Pair<InstanceId, com.android.launcher3.logging.InstanceId> instanceIds =
                    LogUtils.getShellShareableInstanceId();
            mTarget.getStatsLogManager().logger()
                    .withItemInfo(mItemInfo)
                    .withInstanceId(instanceIds.second)
                    .log(getLogEventForPosition(getPosition().stagePosition));

            if (mItemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                WorkspaceItemInfo workspaceItemInfo = (WorkspaceItemInfo) mItemInfo;
                SystemUiProxy.INSTANCE.get(mTarget).startShortcut(
                        workspaceItemInfo.getIntent().getPackage(),
                        workspaceItemInfo.getDeepShortcutId(),
                        getPosition().stagePosition,
                        null,
                        workspaceItemInfo.user,
                        instanceIds.first);
            } else {
                SystemUiProxy.INSTANCE.get(mTarget).startIntent(
                        mTarget.getSystemService(LauncherApps.class).getMainActivityLaunchIntent(
                                mItemInfo.getIntent().getComponent(),
                                null,
                                mItemInfo.user),
                        mItemInfo.user.getIdentifier(),
                        new Intent(),
                        getPosition().stagePosition,
                        null,
                        instanceIds.first);
            }
        }
    }
}

