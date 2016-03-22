package com.android.launcher3;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.UserManager;
import android.util.AttributeSet;
import android.util.Pair;
import android.widget.Toast;

import com.android.launcher3.compat.UserHandleCompat;

public class UninstallDropTarget extends ButtonDropTarget {

    public UninstallDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public UninstallDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // Get the hover color
        mHoverColor = getResources().getColor(R.color.uninstall_target_hover_tint);

        setDrawable(R.drawable.ic_uninstall_launcher);
    }

    @Override
    protected boolean supportsDrop(DragSource source, ItemInfo info) {
        return supportsDrop(getContext(), info);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public static boolean supportsDrop(Context context, Object info) {
        if (Utilities.ATLEAST_JB_MR2) {
            UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
            Bundle restrictions = userManager.getUserRestrictions();
            if (restrictions.getBoolean(UserManager.DISALLOW_APPS_CONTROL, false)
                    || restrictions.getBoolean(UserManager.DISALLOW_UNINSTALL_APPS, false)) {
                return false;
            }
        }

        Pair<ComponentName, Integer> componentInfo = getAppInfoFlags(info);
        return componentInfo != null && (componentInfo.second & AppInfo.DOWNLOADED_FLAG) != 0;
    }

    /**
     * @return the component name and flags if {@param info} is an AppInfo or an app shortcut.
     */
    private static Pair<ComponentName, Integer> getAppInfoFlags(Object item) {
        if (item instanceof AppInfo) {
            AppInfo info = (AppInfo) item;
            return Pair.create(info.componentName, info.flags);
        } else if (item instanceof ShortcutInfo) {
            ShortcutInfo info = (ShortcutInfo) item;
            ComponentName component = info.getTargetComponent();
            if (info.itemType == LauncherSettings.BaseLauncherColumns.ITEM_TYPE_APPLICATION
                    && component != null) {
                return Pair.create(component, info.flags);
            }
        }
        return null;
    }

    @Override
    public void onDrop(DragObject d) {
        // Differ item deletion
        if (d.dragSource instanceof DropTargetSource) {
            ((DropTargetSource) d.dragSource).deferCompleteDropAfterUninstallActivity();
        }
        super.onDrop(d);
    }

    @Override
    void completeDrop(final DragObject d) {
        DropTargetResultCallback callback = d.dragSource instanceof DropTargetResultCallback
                ? (DropTargetResultCallback) d.dragSource : null;
        startUninstallActivity(mLauncher, d.dragInfo, callback);
    }

    public static boolean startUninstallActivity(Launcher launcher, ItemInfo info) {
        return startUninstallActivity(launcher, info, null);
    }

    public static boolean startUninstallActivity(
            final Launcher launcher, ItemInfo info, DropTargetResultCallback callback) {
        Pair<ComponentName, Integer> componentInfo = getAppInfoFlags(info);
        ComponentName cn = componentInfo.first;

        final boolean isUninstallable;
        if ((componentInfo.second & AppInfo.DOWNLOADED_FLAG) == 0) {
            // System applications cannot be installed. For now, show a toast explaining that.
            // We may give them the option of disabling apps this way.
            Toast.makeText(launcher, R.string.uninstall_system_app_text, Toast.LENGTH_SHORT).show();
            isUninstallable = false;
        } else {
            Intent intent = new Intent(Intent.ACTION_DELETE,
                    Uri.fromParts("package", cn.getPackageName(), cn.getClassName()))
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            info.user.addToIntent(intent, Intent.EXTRA_USER);
            launcher.startActivity(intent);
            isUninstallable = true;
        }
        if (callback != null) {
            sendUninstallResult(
                    launcher, isUninstallable, componentInfo.first, info.user, callback);
        }
        return isUninstallable;
    }

    /**
     * Notifies the {@param callback} whether the uninstall was successful or not.
     *
     * Since there is no direct callback for an uninstall request, we check the package existence
     * when the launch resumes next time. This assumes that the uninstall activity will finish only
     * after the task is completed
     */
    protected static void sendUninstallResult(
            final Launcher launcher, boolean activityStarted,
            final ComponentName cn, final UserHandleCompat user,
            final DropTargetResultCallback callback) {
        if (activityStarted)  {
            final Runnable checkIfUninstallWasSuccess = new Runnable() {
                @Override
                public void run() {
                    String packageName = cn.getPackageName();
                    boolean uninstallSuccessful = !AllAppsList.packageHasActivities(
                            launcher, packageName, user);
                    callback.onDragObjectRemoved(uninstallSuccessful);
                }
            };
            launcher.addOnResumeCallback(checkIfUninstallWasSuccess);
        } else {
            callback.onDragObjectRemoved(false);
        }
    }

    public interface DropTargetResultCallback {
        /**
         * A drag operation was complete.
         * @param isRemoved true if the drag object should be removed, false otherwise.
         */
        void onDragObjectRemoved(boolean isRemoved);
    }

    /**
     * Interface defining an object that can provide uninstallable drag objects.
     */
    public interface DropTargetSource extends DropTargetResultCallback {

        /**
         * Indicates that an uninstall request are made and the actual result may come
         * after some time.
         */
        void deferCompleteDropAfterUninstallActivity();
    }
}
