package ch.deletescape.lawnchair;

import android.animation.TimeInterpolator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import ch.deletescape.lawnchair.dragndrop.DragLayer;
import ch.deletescape.lawnchair.util.FlingAnimation;

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

        setDrawable(R.drawable.ic_uninstall_no_shadow);
    }

    @Override
    protected boolean supportsDrop(DragSource source, ItemInfo info) {
        return supportsDrop(getContext(), info);
    }

    public static boolean supportsDrop(Context context, Object info) {
        UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        Bundle restrictions = userManager.getUserRestrictions();
        if (restrictions.getBoolean(UserManager.DISALLOW_APPS_CONTROL, false)
                || restrictions.getBoolean(UserManager.DISALLOW_UNINSTALL_APPS, false)) {
            return false;
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

    @Override
    public void onFlingToDelete(final DragObject d, PointF vel) {
        // Don't highlight the icon as it's animating
        d.dragView.setColor(0);

        final DragLayer dragLayer = mLauncher.getDragLayer();
        FlingAnimation fling = new FlingAnimation(d, vel,
                getIconRect(d.dragView.getMeasuredWidth(), d.dragView.getMeasuredHeight(),
                        mDrawable.getIntrinsicWidth(), mDrawable.getIntrinsicHeight()),
                dragLayer);

        final int duration = fling.getDuration();
        final long startTime = AnimationUtils.currentAnimationTimeMillis();

        // NOTE: Because it takes time for the first frame of animation to actually be
        // called and we expect the animation to be a continuation of the fling, we have
        // to account for the time that has elapsed since the fling finished.  And since
        // we don't have a startDelay, we will always get call to update when we call
        // start() (which we want to ignore).
        final TimeInterpolator tInterpolator = new TimeInterpolator() {
            private int mCount = -1;
            private float mOffset = 0f;

            @Override
            public float getInterpolation(float t) {
                if (mCount < 0) {
                    mCount++;
                } else if (mCount == 0) {
                    mOffset = Math.min(0.5f, (float) (AnimationUtils.currentAnimationTimeMillis() -
                            startTime) / duration);
                    mCount++;
                }
                return Math.min(1f, mOffset + t);
            }
        };

        Runnable onAnimationEndRunnable = new Runnable() {
            @Override
            public void run() {
                mLauncher.exitSpringLoadedDragMode();
                completeDrop(d);
                mLauncher.getDragController().onDeferredEndFling(d);
            }
        };

        dragLayer.animateView(d.dragView, fling, duration, tInterpolator, onAnimationEndRunnable,
                DragLayer.ANIMATION_END_DISAPPEAR, null);
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
            intent.putExtra(Intent.EXTRA_USER, info.user);
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
     * <p>
     * Since there is no direct callback for an uninstall request, we check the package existence
     * when the launch resumes next time. This assumes that the uninstall activity will finish only
     * after the task is completed
     */
    protected static void sendUninstallResult(
            final Launcher launcher, boolean activityStarted,
            final ComponentName cn, final UserHandle user,
            final DropTargetResultCallback callback) {
        if (activityStarted) {
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
         *
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
