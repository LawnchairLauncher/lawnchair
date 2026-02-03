package com.android.launcher3.popup;

import static com.android.launcher3.AbstractFloatingView.TYPE_FOLDER;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_DISMISS_PREDICTION_UNDO;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_INSTALL_SYSTEM_SHORTCUT_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_UNINSTALL_SYSTEM_SHORTCUT_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_APP_INFO_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_DONT_SUGGEST_APP_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_WIDGETS_TAP;
import static com.android.launcher3.widget.picker.model.data.WidgetPickerDataUtils.findAllWidgetsForPackageUser;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.AbstractFloatingViewHelper;
import com.android.launcher3.DropTargetHandler;
import com.android.launcher3.Flags;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.SecondaryDropTarget;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.PrivateProfileManager;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.ActivityOptionsWrapper;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.InstantAppResolver;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.Snackbar;
import com.android.launcher3.widget.WidgetsBottomSheet;
import com.android.launcher3.widget.picker.model.data.WidgetPickerData;

import java.net.URISyntaxException;
import java.util.Arrays;

import com.patrykmichalik.opto.core.PreferenceExtensionsKt;
import app.lawnchair.preferences2.PreferenceManager2;

/**
 * Represents a system shortcut for a given app. The shortcut should have a label and icon, and an
 * onClickListener that depends on the item that the shortcut services.
 *
 * Example system shortcuts, defined as inner classes, include Widgets and AppInfo.
 *
 * @param <T> extends {@link ActivityContext}
 */
public abstract class SystemShortcut<T extends ActivityContext> extends ItemInfo
        implements View.OnClickListener {
    private static final String TAG = "SystemShortcut";

    private final int mIconResId;
    protected final int mLabelResId;
    protected int mAccessibilityActionId;

    protected final T mTarget;
    protected final ItemInfo mItemInfo;
    protected final View mOriginalView;
    protected final boolean mIsCollapsible;

    private final AbstractFloatingViewHelper mAbstractFloatingViewHelper;

    public SystemShortcut(int iconResId, int labelResId, T target, ItemInfo itemInfo,
            View originalView) {
        this(iconResId, labelResId, target, itemInfo, originalView,
                new AbstractFloatingViewHelper(), /* isCollapsible */ true);
    }

    public SystemShortcut(int iconResId, int labelResId, T target, ItemInfo itemInfo,
            View originalView, boolean isCollapsible) {
        this(iconResId, labelResId, target, itemInfo, originalView,
                new AbstractFloatingViewHelper(), isCollapsible);
    }

    public SystemShortcut(int iconResId, int labelResId, T target, ItemInfo itemInfo,
            View originalView, AbstractFloatingViewHelper abstractFloatingViewHelper) {
        this(iconResId, labelResId, target, itemInfo, originalView, abstractFloatingViewHelper,
                /* isCollapsible */ true);
    }

    public SystemShortcut(int iconResId, int labelResId, T target, ItemInfo itemInfo,
            View originalView, AbstractFloatingViewHelper abstractFloatingViewHelper,
            boolean isCollapsible) {
        mIconResId = iconResId;
        mLabelResId = labelResId;
        mAccessibilityActionId = labelResId;
        mTarget = target;
        mItemInfo = itemInfo;
        mOriginalView = originalView;
        mAbstractFloatingViewHelper = abstractFloatingViewHelper;
        mIsCollapsible = isCollapsible;
    }

    public void setIconAndLabelFor(View iconView, TextView labelView) {
        iconView.setBackgroundResource(mIconResId);
        labelView.setText(mLabelResId);
    }

    public void setIconAndContentDescriptionFor(ImageView view) {
        view.setImageResource(mIconResId);
        view.setContentDescription(view.getContext().getText(mLabelResId));
    }

    public AccessibilityNodeInfo.AccessibilityAction createAccessibilityAction(Context context) {
        return new AccessibilityNodeInfo.AccessibilityAction(
                mAccessibilityActionId, context.getText(mLabelResId));
    }

    public boolean hasHandlerForAction(int action) {
        return mAccessibilityActionId == action;
    }

    public interface Factory<T extends ActivityContext> {

        @Nullable
        SystemShortcut<T> getShortcut(T context, ItemInfo itemInfo, @NonNull View originalView);
    }

    public static final Factory<ActivityContext> WIDGETS = (context, itemInfo, originalView) -> {
        final PackageUserKey packageUserKey = PackageUserKey.fromItemInfo(itemInfo);
        if (packageUserKey == null) return null;

        final WidgetPickerData data = context.getWidgetPickerDataProvider().get();
        if (findAllWidgetsForPackageUser(data, packageUserKey).isEmpty()) {
            // hides widget picker shortcut if there are no widgets for the package.
            return null;
        }
        return new Widgets(context, itemInfo, originalView);
    };

    public static class Widgets<T extends ActivityContext> extends SystemShortcut<T> {

        public Widgets(T target, ItemInfo itemInfo, @NonNull View originalView) {
            super(getDrawableId(), R.string.widget_button_text, target, itemInfo, originalView,
                    false);
        }

        /**
         * @return drawable for Widget shortcut icon
         */
        public static int getDrawableId() {
            if (Flags.enableLauncherVisualRefresh()) {
                return R.drawable.widgets_24px;
            } else {
                return R.drawable.ic_widget;
            }
        }

        @Override
        public void onClick(View view) {
            AbstractFloatingView.closeAllOpenViews(mTarget);
            WidgetsBottomSheet widgetsBottomSheet =
                    (WidgetsBottomSheet) mTarget.getLayoutInflater().inflate(
                            R.layout.widgets_bottom_sheet, mTarget.getDragLayer(), false);
            widgetsBottomSheet.populateAndShow(mItemInfo);
            mTarget.getStatsLogManager().logger().withItemInfo(mItemInfo)
                    .log(LAUNCHER_SYSTEM_SHORTCUT_WIDGETS_TAP);
        }
    }

    public static final Factory<ActivityContext> APP_INFO = AppInfo::new;

    public static class AppInfo<T extends ActivityContext> extends SystemShortcut<T> {

        @Nullable
        private SplitAccessibilityInfo mSplitA11yInfo;

        public AppInfo(T target, ItemInfo itemInfo, @NonNull View originalView) {
            super(getDrawableId(), R.string.app_info_drop_target_label, target,
                    itemInfo, originalView);
        }

        /**
         * @return drawable for App Info shortcut icon
         */
        public static int getDrawableId() {
            if (Flags.enableLauncherVisualRefresh()) {
                return R.drawable.info_24px;
            } else {
                return R.drawable.ic_info_no_shadow;
            }
        }
        /**
         * Constructor used by overview for staged split to provide custom A11y information.
         *
         * Future improvements considerations:
         * Have the logic in {@link #createAccessibilityAction(Context)} be moved to super
         * call in {@link SystemShortcut#createAccessibilityAction(Context)} by having
         * SystemShortcut be aware of TaskContainers and staged split.
         * That way it could directly create the correct node info for any shortcut that supports
         * split, but then we'll need custom resIDs for each pair of shortcuts.
         */
        public AppInfo(T target, ItemInfo itemInfo, View originalView,
                SplitAccessibilityInfo accessibilityInfo) {
            this(target, itemInfo, originalView);
            mSplitA11yInfo = accessibilityInfo;
            mAccessibilityActionId = accessibilityInfo.nodeId;
        }

        @Override
        public AccessibilityNodeInfo.AccessibilityAction createAccessibilityAction(
                Context context) {
            if (mSplitA11yInfo != null && mSplitA11yInfo.containsMultipleTasks) {
                String accessibilityLabel = context.getString(R.string.split_app_info_accessibility,
                        mSplitA11yInfo.taskTitle);
                return new AccessibilityNodeInfo.AccessibilityAction(mAccessibilityActionId,
                        accessibilityLabel);
            } else {
                return super.createAccessibilityAction(context);
            }
        }

        @Override
        public void onClick(View view) {
            Rect sourceBounds = Utilities.getViewBounds(view);
            ActivityOptionsWrapper options = mTarget.getActivityLaunchOptions(view, mItemInfo);
            // Dismiss the taskMenu when the app launch animation is complete
            options.onEndCallback.add(this::dismissTaskMenuView);
            PackageManagerHelper.startDetailsActivityForInfo(view.getContext(), mItemInfo,
                    sourceBounds, options.toBundle());
            mTarget.getStatsLogManager().logger().withItemInfo(mItemInfo)
                    .log(LAUNCHER_SYSTEM_SHORTCUT_APP_INFO_TAP);
        }

        public static class SplitAccessibilityInfo {
            public final boolean containsMultipleTasks;
            public final CharSequence taskTitle;
            public final int nodeId;

            public SplitAccessibilityInfo(boolean containsMultipleTasks,
                    CharSequence taskTitle, int nodeId) {
                this.containsMultipleTasks = containsMultipleTasks;
                this.taskTitle = taskTitle;
                this.nodeId = nodeId;
            }
        }
    }

    public static final Factory<ActivityContext> REMOVE = RemoveApp::new;

    public static class RemoveApp<T extends ActivityContext> extends SystemShortcut<T> {

        public RemoveApp(T target, ItemInfo itemInfo, @NonNull View originalView) {
            super(R.drawable.ic_remove_no_shadow, R.string.remove_drop_target_label, target,
                    itemInfo, originalView, false);
        }

        @Override
        public void onClick(View view) {
            AbstractFloatingView.closeAllOpenViewsExcept(mTarget, TYPE_FOLDER);
            DropTargetHandler dropTargetHandler =
                    ActivityContext.lookupContext(view.getContext()).getDropTargetHandler();
            dropTargetHandler.prepareToUndoDelete();
            dropTargetHandler.onDeleteComplete(mItemInfo, mOriginalView);
        }
    }

    public static final Factory<ActivityContext> PRIVATE_PROFILE_INSTALL =
            (context, itemInfo, originalView) -> {
                if (originalView == null) {
                    return null;
                }
                if (itemInfo.getTargetComponent() == null
                        || !(itemInfo instanceof com.android.launcher3.model.data.AppInfo)
                        || !itemInfo.getContainerInfo().hasAllAppsContainer()
                        || !Process.myUserHandle().equals(itemInfo.user)) {
                    return null;
                }

                PrivateProfileManager privateProfileManager =
                        context.getAppsView().getPrivateProfileManager();
                if (privateProfileManager == null || !privateProfileManager.isEnabled()) {
                    return null;
                }

                UserHandle privateProfileUser = privateProfileManager.getProfileUser();
                if (privateProfileUser == null) {
                    return null;
                }
                // Do not show shortcut if an app is already installed to the space
                ComponentName targetComponent = itemInfo.getTargetComponent();
                if (context.getAppsView().getAppsStore().getApp(
                        new ComponentKey(targetComponent, privateProfileUser)) != null) {
                    return null;
                }

                // Do not show shortcut for settings
                String[] packagesToSkip =
                        originalView.getContext().getResources()
                                .getStringArray(R.array.skip_private_profile_shortcut_packages);
                if (Arrays.asList(packagesToSkip).contains(targetComponent.getPackageName())) {
                    return null;
                }

                return new InstallToPrivateProfile<>(
                        context, itemInfo, originalView, privateProfileUser);
            };

    static class InstallToPrivateProfile<T extends ActivityContext> extends SystemShortcut<T> {
        UserHandle mSpaceUser;

        InstallToPrivateProfile(T target, ItemInfo itemInfo, @NonNull View originalView,
                UserHandle spaceUser) {
            // TODO(b/302666597): update icon once available
            super(
                    R.drawable.ic_install_to_private,
                    R.string.install_private_system_shortcut_label,
                    target,
                    itemInfo,
                    originalView);
            mSpaceUser = spaceUser;
        }

        @Override
        public void onClick(View view) {
            Intent intent =
                    ApiWrapper.INSTANCE.get(view.getContext()).getAppMarketActivityIntent(
                            mItemInfo.getTargetComponent().getPackageName(), mSpaceUser);
            mTarget.startActivitySafely(view, intent, mItemInfo);
            AbstractFloatingView.closeAllOpenViews(mTarget);
            mTarget.getStatsLogManager()
                    .logger()
                    .withItemInfo(mItemInfo)
                    .log(LAUNCHER_PRIVATE_SPACE_INSTALL_SYSTEM_SHORTCUT_TAP);
        }
    }

    public static final Factory<ActivityContext> INSTALL =
            (activity, itemInfo, originalView) -> {
                if (originalView == null) {
                    return null;
                }
                boolean supportsWebUI = (itemInfo instanceof WorkspaceItemInfo)
                        && ((WorkspaceItemInfo) itemInfo).hasStatusFlag(
                        WorkspaceItemInfo.FLAG_SUPPORTS_WEB_UI);
                boolean isInstantApp = false;
                if (itemInfo instanceof com.android.launcher3.model.data.AppInfo appInfo) {
                    isInstantApp = InstantAppResolver.newInstance(
                            originalView.getContext()).isInstantApp(appInfo);
                }
                boolean enabled = supportsWebUI || isInstantApp;
                if (!enabled) {
                    return null;
                }
                return new Install(activity, itemInfo, originalView);
            };

    public static class Install<T extends ActivityContext> extends SystemShortcut<T> {

        public Install(T target, ItemInfo itemInfo, @NonNull View originalView) {
            super(R.drawable.ic_install_no_shadow, R.string.install_drop_target_label,
                    target, itemInfo, originalView);
        }

        @Override
        public void onClick(View view) {
            Intent intent = ApiWrapper.INSTANCE.get(view.getContext()).getAppMarketActivityIntent(
                    mItemInfo.getTargetComponent().getPackageName(), Process.myUserHandle());
            mTarget.startActivitySafely(view, intent, mItemInfo);
            AbstractFloatingView.closeAllOpenViews(mTarget);
        }
    }

    public static final Factory<ActivityContext> DONT_SUGGEST_APP =
            (activity, itemInfo, originalView) -> {
                if (!itemInfo.isPredictedItem()) {
                    return null;
                }
                return new DontSuggestApp<>(activity, itemInfo, originalView);
            };

    private static class DontSuggestApp<T extends ActivityContext> extends SystemShortcut<T> {
        DontSuggestApp(T target, ItemInfo itemInfo, View originalView) {
            super(R.drawable.ic_block_no_shadow, R.string.dismiss_prediction_label, target,
                    itemInfo, originalView);
        }

        @Override
        public void onClick(View view) {
            dismissTaskMenuView();
            mTarget.getStatsLogManager().logger()
                    .withItemInfo(mItemInfo)
                    .log(LAUNCHER_SYSTEM_SHORTCUT_DONT_SUGGEST_APP_TAP);
            if (Flags.enableDismissPredictionUndo()) {
                Snackbar.show(mTarget,
                        view.getContext().getString(R.string.item_removed), R.string.undo,
                        () -> { }, () ->
                            mTarget.getStatsLogManager().logger()
                                    .withItemInfo(mItemInfo)
                                    .log(LAUNCHER_DISMISS_PREDICTION_UNDO));
            }
        }
    }

    public static final Factory<ActivityContext> UNINSTALL_APP =
            (activityContext, itemInfo, originalView) -> {
                if (originalView == null) {
                    return null;
                }
                if (!Flags.enablePrivateSpace()) {
                    return null;
                }
                if (!UserCache.INSTANCE.get(originalView.getContext()).getUserInfo(
                        itemInfo.user).isPrivate()) {
                    // If app is not Private Space app.
                    return null;
                }
                ComponentName cn = SecondaryDropTarget.getUninstallTarget(originalView.getContext(),
                        itemInfo);
                if (cn == null) {
                    // If component name is null, don't show uninstall shortcut.
                    // System apps will have component name as null.
                    return null;
                }
                return new UninstallApp(activityContext, itemInfo, originalView, cn);
            };

    private static class UninstallApp<T extends ActivityContext> extends SystemShortcut<T> {
        @NonNull ComponentName mComponentName;

        UninstallApp(T target, ItemInfo itemInfo, @NonNull View originalView,
                @NonNull ComponentName cn) {
            super(R.drawable.ic_uninstall_no_shadow,
                    R.string.uninstall_private_system_shortcut_label, target,
                    itemInfo, originalView);
            mComponentName = cn;

        }

        @Override
        public void onClick(View view) {
            dismissTaskMenuView();
            SecondaryDropTarget.performUninstall(view.getContext(), mComponentName, mItemInfo);
            mTarget.getStatsLogManager()
                    .logger()
                    .withItemInfo(mItemInfo)
                    .log(LAUNCHER_PRIVATE_SPACE_UNINSTALL_SYSTEM_SHORTCUT_TAP);
        }
    }

    protected void dismissTaskMenuView() {
        mAbstractFloatingViewHelper.closeOpenViews(mTarget, true,
                AbstractFloatingView.TYPE_ALL & ~AbstractFloatingView.TYPE_REBIND_SAFE);
    }

    public static final Factory<ActivityContext> BUBBLE_SHORTCUT =
            (activity, itemInfo, originalView) -> {
                if ((itemInfo.itemType != LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT)
                        && (itemInfo.itemType != LauncherSettings.Favorites.ITEM_TYPE_APPLICATION)
                        && !(itemInfo instanceof WorkspaceItemInfo)) {
                    return null;
                }
                if (itemInfo instanceof WorkspaceItemInfo) {
                    // Don't show bubble shortcut option for non-resizeable apps on small screens.
                    // TODO(b/411558731): isPhone just checks for smallest width < 600dp, so it
                    // basically is a check for small screens including Foldables when folded.
                    // However, the name is a bit misleading, so considering renaming.
                    WorkspaceItemInfo wsItemInfo = (WorkspaceItemInfo) itemInfo;
                    if (wsItemInfo.isNonResizeable()
                            && activity.getDeviceProfile().getDeviceProperties().isPhone()) {
                        return null;
                    }
                }
                return new BubbleShortcut<>(activity, itemInfo, originalView);
            };

    public interface BubbleActivityStarter {
        /** Tell SysUI to show the provided shortcut in a bubble. */
        void showShortcutBubble(ShortcutInfo info);

        /** Tell SysUI to show the provided intent in a bubble. */
        void showAppBubble(Intent intent, UserHandle user);
    }

    public static class BubbleShortcut<T extends ActivityContext> extends SystemShortcut<T> {

        private BubbleActivityStarter mStarter;

        public BubbleShortcut(T target, ItemInfo itemInfo, View originalView) {
            super(R.drawable.ic_bubble_button, R.string.bubble, target,
                    itemInfo, originalView);
            if (target instanceof BubbleActivityStarter) {
                mStarter = (BubbleActivityStarter) target;
            }
        }

        @Override
        public void onClick(View view) {
            dismissTaskMenuView();
            if (mStarter == null) {
                Log.w(TAG, "starter null!");
                return;
            }
            // TODO: handle GroupTask (single) items so that recent items in taskbar work
            if (mItemInfo instanceof WorkspaceItemInfo) {
                WorkspaceItemInfo workspaceItemInfo = (WorkspaceItemInfo) mItemInfo;
                ShortcutInfo shortcutInfo = workspaceItemInfo.getDeepShortcutInfo();
                if (shortcutInfo != null) {
                    mStarter.showShortcutBubble(shortcutInfo);
                    return;
                }
            }
            // If we're here check for an intent
            Intent intent = mItemInfo.getIntent();
            if (intent != null) {
                if (intent.getPackage() == null) {
                    intent.setPackage(mItemInfo.getTargetPackage());
                }
                mStarter.showAppBubble(intent, mItemInfo.user);
            } else {
                Log.w(TAG, "unable to bubble, no intent: " + mItemInfo);
            }
        }
    }
}
