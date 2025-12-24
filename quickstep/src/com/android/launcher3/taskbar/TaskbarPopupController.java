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
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
import static com.android.launcher3.model.data.AppInfo.COMPONENT_KEY_COMPARATOR;
import static com.android.launcher3.model.data.AppInfo.PACKAGE_KEY_COMPARATOR;
import static com.android.launcher3.util.SplitConfigurationOptions.getLogEventForPosition;
import static com.android.window.flags.Flags.enableOverflowButtonForTaskbarPinnedItems;

import android.content.Intent;
import android.content.pm.LauncherApps;
import android.graphics.Point;
import android.os.UserHandle;
import android.util.Pair;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.window.DesktopExperienceFlags;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.logging.InstanceId;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Flags;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.Popup;
import com.android.launcher3.popup.PopupContainer;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.popup.PopupController;
import com.android.launcher3.popup.PopupItemDragHandler;
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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implements interfaces required to show and allow interacting with a PopupContainerWithArrow.
 * Controls the long-press menu on Taskbar and AllApps icons.
 */
public class TaskbarPopupController implements TaskbarControllers.LoggableTaskbarController,
        PopupController {

    private static final SystemShortcut.Factory<BaseTaskbarContext>
            APP_INFO = SystemShortcut.AppInfo::new;

    private static final SystemShortcut.Factory<BaseTaskbarContext>
            BUBBLE = SystemShortcut.BubbleShortcut::new;

    private final TaskbarActivityContext mContext;

    // Initialized in init.
    private TaskbarControllers mControllers;
    private boolean mAllowInitialSplitSelection;
    private AppInfo[] mAppInfosList = AppInfo.EMPTY_ARRAY;
    // Saves the ItemInfos in the hotseat without the predicted items.
    private SparseArray<ItemInfo> mTaskbarInfoList;
    private ManageWindowsTaskbarShortcut<BaseTaskbarContext> mManageWindowsTaskbarShortcut;
    // Whether the popup is currently open. This is reset to false when the close animation is
    // complete.
    private boolean mIsPopupOpened = false;


    public TaskbarPopupController(TaskbarActivityContext context) {
        mContext = context;
    }

    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
    }

    public void onDestroy() {
        cleanUpMultiInstanceMenuReference();
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

    public boolean isPopupOpened() {
        return mIsPopupOpened;
    }

    // Create a Stream of all applicable system shortcuts
    private Stream<SystemShortcut.Factory<BaseTaskbarContext>> getSystemShortcuts() {
        // append split options to APP_INFO shortcut if not in Desktop Windowing mode, the order
        // here will reflect in the popup
        ArrayList<SystemShortcut.Factory<BaseTaskbarContext>> shortcuts = new ArrayList<>();
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

        if (mControllers.taskbarDesktopModeController
                .isInDesktopModeAndNotInOverview(mContext.getDisplayId())) {
            shortcuts.add(createCloseAppTaskbarShortcutFactory());
        }
        return shortcuts.stream();
    }

    @Nullable
    @VisibleForTesting
    SystemShortcut<BaseTaskbarContext> createPinShortcut(BaseTaskbarContext target,
            ItemInfo itemInfo, BubbleTextView originalView) {
        // Predicted items use {@code HotseatPredictionController.PinPrediction} shortcut to pin.
        if (itemInfo.container == CONTAINER_HOTSEAT_PREDICTION) {
            return null;
        }
        if (itemInfo.container == CONTAINER_HOTSEAT) {
            return new PinToTaskbarShortcut<>(target, itemInfo, originalView, false,
                    mTaskbarInfoList);
        }

        if (itemInfo.isInAllApps()) {
            // If the target ItemInfo is already pinned on taskbar. Show the unpin option instead.
            for (int i = 0; i < mTaskbarInfoList.size(); i++) {
                if (Objects.equals(mTaskbarInfoList.valueAt(i).getComponentKey(),
                        itemInfo.getComponentKey())) {
                    return new PinToTaskbarShortcut<>(target, itemInfo, originalView, false,
                            mTaskbarInfoList);
                }
            }
        }

        if (mTaskbarInfoList.size()
                < mContext.getTaskbarSpecsEvaluator().getMaxPinnableCount()) {
            return new PinToTaskbarShortcut<>(target, itemInfo, originalView, true,
                    mTaskbarInfoList);
        }

        return null;
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarPopupController:");
    }

    @Nullable
    @Override
    public Popup show(@NonNull View view) {
        BubbleTextView icon = (BubbleTextView) view;
        BaseTaskbarContext context = ActivityContext.lookupContext(icon.getContext());
        if (PopupContainer.getOpen(context) != null) {
            // There is already an items container open, so don't open this one.
            icon.clearFocus();
            return null;
        }

        ItemInfo itemInfo = null;
        if (icon.getTag() instanceof ItemInfo item && ShortcutUtil.supportsShortcuts(item)) {
            itemInfo = item;
        } else if (canPinAppWithContextMenu(mContext)
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
        int deepShortcutCount = mContext.getActivityComponent()
                .getPopupDataProvider().getShortcutCountForItem(itemInfo);
        // TODO(b/198438631): add support for INSTALL shortcut factory
        final ItemInfo finalInfo = itemInfo;
        List<SystemShortcut<BaseTaskbarContext>> systemShortcuts = getSystemShortcuts()
                .map(s -> s.getShortcut(context, finalInfo, icon))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // TODO(b/375648361): Revisit to see if this can be implemented within getSystemShortcuts().
        if (canPinAppWithContextMenu(mContext)) {
            SystemShortcut<BaseTaskbarContext> shortcut =
                    createPinShortcut(context, itemInfo, icon);
            if (shortcut != null) {
                systemShortcuts.add(0, shortcut);
            }
        }

        container = PopupContainerWithArrow.create(context, /* originalView */ icon,
                /*itemInfo */ itemInfo,
                /* updateIconUi */ false);
        // TODO (b/198438631): configure for taskbar/context
        container.populateAndShowRows(deepShortcutCount, systemShortcuts);
        container.setPopupItemDragHandler(new TaskbarPopupItemDragHandler());
        context.getDragController().addDragListener(container);
        container.requestFocus();

        // Make focusable to receive back events
        context.onPopupVisibilityChanged(true);
        container.addOnCloseCallback(() -> {
            context.getDragLayer().post(() -> context.onPopupVisibilityChanged(false));
            mIsPopupOpened = false;
        });
        mIsPopupOpened = true;

        return container;
    }

    @Override
    public void dismiss() {

    }

    private class TaskbarPopupItemDragHandler implements
            PopupItemDragHandler {

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
        if (index < 0) {
            index = Arrays.binarySearch(mAppInfosList, tempInfo, PACKAGE_KEY_COMPARATOR);
        }
        return index < 0 ? null : mAppInfosList[index];
    }

    public void setTaskbarInfoList(SparseArray<ItemInfo> info) {
        mTaskbarInfoList = info;
    }

    public SparseArray<ItemInfo> getTaskbarInfoList() {
        return mTaskbarInfoList.clone();
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
     * Creates a factory function representing a "Close" menu item only if the calling app
     * is in Desktop Mode.
     * @return A factory function to be used in populating the long-press menu.
     */
    private SystemShortcut.Factory<BaseTaskbarContext> createCloseAppTaskbarShortcutFactory() {
        return (context, itemInfo, originalView) -> new CloseAppTaskbarShortcut<>(
                context, itemInfo, originalView, mControllers);
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

    protected static boolean canPinAppWithContextMenu(TaskbarActivityContext context) {
        return DesktopExperienceFlags.ENABLE_PINNING_APP_WITH_CONTEXT_MENU.isTrue()
                && context.isTaskbarShowingDesktopTasks();
    }

    /**
     * @return whether the taskbar can have the overflow icon to accommodate pinned apps that
     * can't fit in taskbar.
     */
    public static boolean canPinAppsOverflow() {
        return enableOverflowButtonForTaskbarPinnedItems();
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

