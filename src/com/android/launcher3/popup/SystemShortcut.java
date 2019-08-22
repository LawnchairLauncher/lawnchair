package com.android.launcher3.popup;

import static com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import static com.android.launcher3.userevent.nano.LauncherLogProto.ControlType;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.WorkspaceItemInfo;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.util.InstantAppResolver;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.widget.WidgetsBottomSheet;

import java.util.List;

/**
 * Represents a system shortcut for a given app. The shortcut should have a label and icon, and an
 * onClickListener that depends on the item that the shortcut services.
 *
 * Example system shortcuts, defined as inner classes, include Widgets and AppInfo.
 */
public abstract class SystemShortcut<T extends BaseDraggingActivity> extends ItemInfo {
    private final int mIconResId;
    private final int mLabelResId;
    private final Icon mIcon;
    private final CharSequence mLabel;
    private final CharSequence mContentDescription;
    private final int mAccessibilityActionId;

    public SystemShortcut(int iconResId, int labelResId) {
        mIconResId = iconResId;
        mLabelResId = labelResId;
        mAccessibilityActionId = labelResId;
        mIcon = null;
        mLabel = null;
        mContentDescription = null;
    }

    public SystemShortcut(Icon icon, CharSequence label, CharSequence contentDescription,
            int accessibilityActionId) {
        mIcon = icon;
        mLabel = label;
        mContentDescription = contentDescription;
        mAccessibilityActionId = accessibilityActionId;
        mIconResId = 0;
        mLabelResId = 0;
    }

    public SystemShortcut(SystemShortcut other) {
        mIconResId = other.mIconResId;
        mLabelResId = other.mLabelResId;
        mIcon = other.mIcon;
        mLabel = other.mLabel;
        mContentDescription = other.mContentDescription;
        mAccessibilityActionId = other.mAccessibilityActionId;
    }

    /**
     * Should be in the left group of icons in app's context menu header.
     */
    public boolean isLeftGroup() {
        return false;
    }

    public void setIconAndLabelFor(View iconView, TextView labelView) {
        if (mIcon != null) {
            mIcon.loadDrawableAsync(iconView.getContext(),
                    iconView::setBackground,
                    new Handler(Looper.getMainLooper()));
        } else {
            iconView.setBackgroundResource(mIconResId);
        }

        if (mLabel != null) {
            labelView.setText(mLabel);
        } else {
            labelView.setText(mLabelResId);
        }
    }

    public void setIconAndContentDescriptionFor(ImageView view) {
        if (mIcon != null) {
            mIcon.loadDrawableAsync(view.getContext(),
                    view::setImageDrawable,
                    new Handler(Looper.getMainLooper()));
        } else {
            view.setImageResource(mIconResId);
        }

        view.setContentDescription(getContentDescription(view.getContext()));
    }

    private CharSequence getContentDescription(Context context) {
        return mContentDescription != null ? mContentDescription : context.getText(mLabelResId);
    }

    public AccessibilityNodeInfo.AccessibilityAction createAccessibilityAction(Context context) {
        return new AccessibilityNodeInfo.AccessibilityAction(mAccessibilityActionId,
                getContentDescription(context));
    }

    public boolean hasHandlerForAction(int action) {
        return mAccessibilityActionId == action;
    }

    public abstract View.OnClickListener getOnClickListener(T activity, ItemInfo itemInfo);

    public static class Widgets extends SystemShortcut<Launcher> {

        public Widgets() {
            super(R.drawable.ic_widget, R.string.widget_button_text);
        }

        @Override
        public View.OnClickListener getOnClickListener(final Launcher launcher,
                final ItemInfo itemInfo) {
            if (itemInfo.getTargetComponent() == null) return null;
            final List<WidgetItem> widgets =
                    launcher.getPopupDataProvider().getWidgetsForPackageUser(new PackageUserKey(
                            itemInfo.getTargetComponent().getPackageName(), itemInfo.user));
            if (widgets == null) {
                return null;
            }
            return (view) -> {
                AbstractFloatingView.closeAllOpenViews(launcher);
                WidgetsBottomSheet widgetsBottomSheet =
                        (WidgetsBottomSheet) launcher.getLayoutInflater().inflate(
                                R.layout.widgets_bottom_sheet, launcher.getDragLayer(), false);
                widgetsBottomSheet.populateAndShow(itemInfo);
                launcher.getUserEventDispatcher().logActionOnControl(Action.Touch.TAP,
                        ControlType.WIDGETS_BUTTON, view);
            };
        }
    }

    public static class AppInfo extends SystemShortcut {
        public AppInfo() {
            super(R.drawable.ic_info_no_shadow, R.string.app_info_drop_target_label);
        }

        @Override
        public View.OnClickListener getOnClickListener(
                BaseDraggingActivity activity, ItemInfo itemInfo) {
            return (view) -> {
                dismissTaskMenuView(activity);
                Rect sourceBounds = activity.getViewBounds(view);
                new PackageManagerHelper(activity).startDetailsActivityForInfo(
                        itemInfo, sourceBounds, ActivityOptions.makeBasic().toBundle());
                activity.getUserEventDispatcher().logActionOnControl(Action.Touch.TAP,
                        ControlType.APPINFO_TARGET, view);
            };
        }
    }

    public static class Install extends SystemShortcut {
        public Install() {
            super(R.drawable.ic_install_no_shadow, R.string.install_drop_target_label);
        }

        @Override
        public View.OnClickListener getOnClickListener(
                BaseDraggingActivity activity, ItemInfo itemInfo) {
            boolean supportsWebUI = (itemInfo instanceof WorkspaceItemInfo) &&
                    ((WorkspaceItemInfo) itemInfo).hasStatusFlag(WorkspaceItemInfo.FLAG_SUPPORTS_WEB_UI);
            boolean isInstantApp = false;
            if (itemInfo instanceof com.android.launcher3.AppInfo) {
                com.android.launcher3.AppInfo appInfo = (com.android.launcher3.AppInfo) itemInfo;
                isInstantApp = InstantAppResolver.newInstance(activity).isInstantApp(appInfo);
            }
            boolean enabled = supportsWebUI || isInstantApp;
            if (!enabled) {
                return null;
            }
            return createOnClickListener(activity, itemInfo);
        }

        public View.OnClickListener createOnClickListener(
                BaseDraggingActivity activity, ItemInfo itemInfo) {
            return view -> {
                Intent intent = new PackageManagerHelper(view.getContext()).getMarketIntent(
                        itemInfo.getTargetComponent().getPackageName());
                activity.startActivitySafely(view, intent, itemInfo, null);
                AbstractFloatingView.closeAllOpenViews(activity);
            };
        }
    }

    protected static void dismissTaskMenuView(BaseDraggingActivity activity) {
        AbstractFloatingView.closeOpenViews(activity, true,
            AbstractFloatingView.TYPE_ALL & ~AbstractFloatingView.TYPE_REBIND_SAFE);
    }
}
