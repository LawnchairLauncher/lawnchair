package com.android.launcher3.popup;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_APP_INFO_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_WIDGETS_TAP;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.InstantAppResolver;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.WidgetsBottomSheet;
import com.patrykmichalik.opto.core.PreferenceExtensionsKt;

import java.net.URISyntaxException;
import java.util.List;

import app.lawnchair.preferences2.PreferenceManager2;

/**
 * Represents a system shortcut for a given app. The shortcut should have a label and icon, and an
 * onClickListener that depends on the item that the shortcut services.
 *
 * Example system shortcuts, defined as inner classes, include Widgets and AppInfo.
 * @param <T>
 */
public abstract class SystemShortcut<T extends Context & ActivityContext> extends ItemInfo
        implements View.OnClickListener {

    private final int mIconResId;
    protected final int mLabelResId;
    protected int mAccessibilityActionId;

    protected final T mTarget;
    protected final ItemInfo mItemInfo;

    /**
     * Indicates if it's invokable or not through some disabled UI
     */
    private boolean isEnabled = true;

    public SystemShortcut(int iconResId, int labelResId, T target, ItemInfo itemInfo) {
        mIconResId = iconResId;
        mLabelResId = labelResId;
        mAccessibilityActionId = labelResId;
        mTarget = target;
        mItemInfo = itemInfo;
    }

    public SystemShortcut(SystemShortcut<T> other) {
        mIconResId = other.mIconResId;
        mLabelResId = other.mLabelResId;
        mAccessibilityActionId = other.mAccessibilityActionId;
        mTarget = other.mTarget;
        mItemInfo = other.mItemInfo;
    }

    /**
     * Should be in the left group of icons in app's context menu header.
     */
    public boolean isLeftGroup() {
        return false;
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

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public boolean hasHandlerForAction(int action) {
        return mAccessibilityActionId == action;
    }

    public interface Factory<T extends Context & ActivityContext> {

        @Nullable SystemShortcut<T> getShortcut(T activity, ItemInfo itemInfo);
    }

    public static final Factory<Launcher> WIDGETS = (launcher, itemInfo) -> {
        if (PreferenceExtensionsKt.firstBlocking(PreferenceManager2.getInstance(launcher).getLockHomeScreen())) return null;
        if (itemInfo.getTargetComponent() == null) return null;
        final List<WidgetItem> widgets =
                launcher.getPopupDataProvider().getWidgetsForPackageUser(new PackageUserKey(
                        itemInfo.getTargetComponent().getPackageName(), itemInfo.user));
        if (widgets.isEmpty()) {
            return null;
        }
        return new Widgets(launcher, itemInfo);
    };

    public static class Widgets extends SystemShortcut<Launcher> {
        public Widgets(Launcher target, ItemInfo itemInfo) {
            super(R.drawable.ic_widget, R.string.widget_button_text, target, itemInfo);
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

    public static final Factory<BaseDraggingActivity> APP_INFO = AppInfo::new;

    public static class AppInfo<T extends Context & ActivityContext> extends SystemShortcut<T> {

        @Nullable
        private SplitAccessibilityInfo mSplitA11yInfo;

        public AppInfo(T target, ItemInfo itemInfo) {
            super(R.drawable.ic_info_no_shadow, R.string.app_info_drop_target_label, target,
                    itemInfo);
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
        public AppInfo(T target, ItemInfo itemInfo, SplitAccessibilityInfo accessibilityInfo) {
            this(target, itemInfo);
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
            dismissTaskMenuView(mTarget);
            Rect sourceBounds = Utilities.getViewBounds(view);
            new PackageManagerHelper(mTarget).startDetailsActivityForInfo(
                    mItemInfo, sourceBounds, ActivityOptions.makeBasic().toBundle());
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

    public static final Factory<BaseDraggingActivity> INSTALL = (activity, itemInfo) -> {
        boolean supportsWebUI = (itemInfo instanceof WorkspaceItemInfo)
                && ((WorkspaceItemInfo) itemInfo).hasStatusFlag(
                        WorkspaceItemInfo.FLAG_SUPPORTS_WEB_UI);
        boolean isInstantApp = false;
        if (itemInfo instanceof com.android.launcher3.model.data.AppInfo) {
            com.android.launcher3.model.data.AppInfo
                    appInfo = (com.android.launcher3.model.data.AppInfo) itemInfo;
            isInstantApp = InstantAppResolver.newInstance(activity).isInstantApp(appInfo);
        }
        boolean enabled = supportsWebUI || isInstantApp;
        if (!enabled) {
            return null;
        }
        return new Install(activity, itemInfo);
    };

    public static class Install extends SystemShortcut<BaseDraggingActivity> {

        public Install(BaseDraggingActivity target, ItemInfo itemInfo) {
            super(R.drawable.ic_install_no_shadow, R.string.install_drop_target_label,
                    target, itemInfo);
        }

        @Override
        public void onClick(View view) {
            Intent intent = new PackageManagerHelper(view.getContext()).getMarketIntent(
                    mItemInfo.getTargetComponent().getPackageName());
            mTarget.startActivitySafely(view, intent, mItemInfo);
            AbstractFloatingView.closeAllOpenViews(mTarget);
        }
    }

    public static final Factory<BaseDraggingActivity> UNINSTALL = (activity, itemInfo) -> {
        if (itemInfo.getTargetComponent() == null) {
            return null;
        }
        if (PackageManagerHelper.isSystemApp(activity,
             itemInfo.getTargetComponent().getPackageName())) {
            return null;
        }
        return new UnInstall(activity, itemInfo);
    };

    public static class UnInstall extends SystemShortcut<BaseDraggingActivity> {

        public UnInstall(BaseDraggingActivity target, ItemInfo itemInfo) {
            super(R.drawable.ic_uninstall_no_shadow, R.string.uninstall_drop_target_label,
                    target, itemInfo);
        }

        @Override
        public void onClick(View view) {
            try {
                Intent intent = Intent.parseUri(view.getContext().getString(R.string.delete_package_intent), 0)
                    .setData(Uri.fromParts("package", mItemInfo.getTargetComponent().getPackageName(),
                    mItemInfo.getTargetComponent().getClassName())).putExtra(Intent.EXTRA_USER, mItemInfo.user);
                mTarget.startActivitySafely(view, intent, mItemInfo);
                AbstractFloatingView.closeAllOpenViews(mTarget);
            } catch (URISyntaxException e) {
                // Do nothing.
            }
        }
    }

    public static <T extends Context & ActivityContext> void dismissTaskMenuView(T activity) {
        AbstractFloatingView.closeOpenViews(activity, true,
            AbstractFloatingView.TYPE_ALL & ~AbstractFloatingView.TYPE_REBIND_SAFE);
    }
}
