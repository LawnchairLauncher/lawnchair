package com.android.launcher3;

import static android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID;
import static android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE;

import static com.android.launcher3.Launcher.REQUEST_RECONFIGURE_APPWIDGET;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.accessibility.LauncherAccessibilityDelegate.DISMISS_PREDICTION;
import static com.android.launcher3.accessibility.LauncherAccessibilityDelegate.INVALID;
import static com.android.launcher3.accessibility.LauncherAccessibilityDelegate.RECONFIGURE;
import static com.android.launcher3.accessibility.LauncherAccessibilityDelegate.UNINSTALL;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_DISMISS_PREDICTION_UNDO;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ITEM_DROPPED_ON_DONT_SUGGEST;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ITEM_DROPPED_ON_UNINSTALL;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ITEM_UNINSTALL_CANCELLED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ITEM_UNINSTALL_COMPLETED;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_SYSTEM_MASK;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_SYSTEM_NO;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.InstanceIdSequence;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.logging.StatsLogManager.StatsLogger;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PendingRequestArgs;
import com.android.launcher3.views.Snackbar;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;

import java.net.URISyntaxException;

/**
 * Drop target which provides a secondary option for an item.
 *    For app targets: shows as uninstall
 *    For configurable widgets: shows as setup
 *    For predicted app icons: don't suggest app
 */
public class SecondaryDropTarget extends ButtonDropTarget implements OnAlarmListener {

    private static final String TAG = "SecondaryDropTarget";

    private static final long CACHE_EXPIRE_TIMEOUT = 5000;
    private final ArrayMap<UserHandle, Boolean> mUninstallDisabledCache = new ArrayMap<>(1);
    private final StatsLogManager mStatsLogManager;
    private final Alarm mCacheExpireAlarm;
    private boolean mHadPendingAlarm;

    protected int mCurrentAccessibilityAction = -1;

    public SecondaryDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SecondaryDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mCacheExpireAlarm = new Alarm();
        mStatsLogManager = StatsLogManager.newInstance(context);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mHadPendingAlarm) {
            mCacheExpireAlarm.setAlarm(CACHE_EXPIRE_TIMEOUT);
            mCacheExpireAlarm.setOnAlarmListener(this);
            mHadPendingAlarm = false;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mCacheExpireAlarm.alarmPending()) {
            mCacheExpireAlarm.cancelAlarm();
            mCacheExpireAlarm.setOnAlarmListener(null);
            mHadPendingAlarm = true;
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setupUi(UNINSTALL);
    }

    protected void setupUi(int action) {
        if (action == mCurrentAccessibilityAction) {
            return;
        }
        mCurrentAccessibilityAction = action;

        if (action == UNINSTALL) {
            setDrawable(R.drawable.ic_uninstall_no_shadow);
            updateText(R.string.uninstall_drop_target_label);
        } else if (action == DISMISS_PREDICTION) {
            setDrawable(R.drawable.ic_block_no_shadow);
            updateText(R.string.dismiss_prediction_label);
        } else if (action == RECONFIGURE) {
            setDrawable(R.drawable.ic_setting);
            updateText(R.string.gadget_setup_text);
        }
    }

    @Override
    public void onAlarm(Alarm alarm) {
        mUninstallDisabledCache.clear();
    }

    @Override
    public int getAccessibilityAction() {
        return mCurrentAccessibilityAction;
    }

    @Override
    protected void setupItemInfo(ItemInfo info) {
        int buttonType = getButtonType(info, getViewUnderDrag(info));
        if (buttonType != INVALID) {
            setupUi(buttonType);
        }
    }

    @Override
    protected boolean supportsDrop(ItemInfo info) {
        return getButtonType(info, getViewUnderDrag(info)) != INVALID;
    }

    @Override
    public boolean supportsAccessibilityDrop(ItemInfo info, View view) {
        return getButtonType(info, view) != INVALID;
    }

    private int getButtonType(ItemInfo info, View view) {
        if (view instanceof AppWidgetHostView) {
            if (getReconfigurableWidgetId(view) != INVALID_APPWIDGET_ID) {
                return RECONFIGURE;
            }
            return INVALID;
        } else if (info.isPredictedItem()) {
            return DISMISS_PREDICTION;
        }

        Boolean uninstallDisabled = mUninstallDisabledCache.get(info.user);
        if (uninstallDisabled == null) {
            UserManager userManager =
                    (UserManager) getContext().getSystemService(Context.USER_SERVICE);
            Bundle restrictions = userManager.getUserRestrictions(info.user);
            uninstallDisabled = restrictions.getBoolean(UserManager.DISALLOW_APPS_CONTROL, false)
                    || restrictions.getBoolean(UserManager.DISALLOW_UNINSTALL_APPS, false);
            mUninstallDisabledCache.put(info.user, uninstallDisabled);
        }
        // Cancel any pending alarm and set cache expiry after some time
        mCacheExpireAlarm.setAlarm(CACHE_EXPIRE_TIMEOUT);
        mCacheExpireAlarm.setOnAlarmListener(this);
        if (uninstallDisabled) {
            return INVALID;
        }

        if (info instanceof ItemInfoWithIcon) {
            ItemInfoWithIcon iconInfo = (ItemInfoWithIcon) info;
            if ((iconInfo.runtimeStatusFlags & FLAG_SYSTEM_MASK) != 0
                    && (iconInfo.runtimeStatusFlags & FLAG_SYSTEM_NO) == 0) {
                return INVALID;
            }
        }
        if (getUninstallTarget(info) == null) {
            return INVALID;
        }
        return UNINSTALL;
    }

    /**
     * @return the component name that should be uninstalled or null.
     */
    private ComponentName getUninstallTarget(ItemInfo item) {
        Intent intent = null;
        UserHandle user = null;
        if (item != null &&
                item.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
            intent = item.getIntent();
            user = item.user;
        }
        if (intent != null) {
            LauncherActivityInfo info = mLauncher.getSystemService(LauncherApps.class)
                    .resolveActivity(intent, user);
            if (info != null
                    && (info.getApplicationInfo().flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                return info.getComponentName();
            }
        }
        return null;
    }

    @Override
    public void onDrop(DragObject d, DragOptions options) {
        // Defer onComplete
        d.dragSource = new DeferredOnComplete(d.dragSource, getContext());

        super.onDrop(d, options);
        doLog(d.logInstanceId, d.originalDragInfo);
    }

    private void doLog(InstanceId logInstanceId, ItemInfo itemInfo) {
        StatsLogger logger = mStatsLogManager.logger().withInstanceId(logInstanceId);
        if (itemInfo != null) {
            logger.withItemInfo(itemInfo);
        }
        if (mCurrentAccessibilityAction == UNINSTALL) {
            logger.log(LAUNCHER_ITEM_DROPPED_ON_UNINSTALL);
        } else if (mCurrentAccessibilityAction == DISMISS_PREDICTION) {
            logger.log(LAUNCHER_ITEM_DROPPED_ON_DONT_SUGGEST);
        }
    }

    @Override
    public void completeDrop(final DragObject d) {
        ComponentName target = performDropAction(getViewUnderDrag(d.dragInfo), d.dragInfo,
                d.logInstanceId);
        if (d.dragSource instanceof DeferredOnComplete) {
            DeferredOnComplete deferred = (DeferredOnComplete) d.dragSource;
            if (target != null) {
                deferred.mPackageName = target.getPackageName();
                mLauncher.addOnResumeCallback(deferred::onLauncherResume);
            } else {
                deferred.sendFailure();
            }
        }
    }

    private View getViewUnderDrag(ItemInfo info) {
        if (info instanceof LauncherAppWidgetInfo && info.container == CONTAINER_DESKTOP &&
                mLauncher.getWorkspace().getDragInfo() != null) {
            return mLauncher.getWorkspace().getDragInfo().cell;
        }
        return null;
    }

    /**
     * Verifies that the view is an reconfigurable widget and returns the corresponding widget Id,
     * otherwise return {@code INVALID_APPWIDGET_ID}
     */
    private int getReconfigurableWidgetId(View view) {
        if (!(view instanceof AppWidgetHostView)) {
            return INVALID_APPWIDGET_ID;
        }
        AppWidgetHostView hostView = (AppWidgetHostView) view;
        AppWidgetProviderInfo widgetInfo = hostView.getAppWidgetInfo();
        if (widgetInfo == null || widgetInfo.configure == null) {
            return INVALID_APPWIDGET_ID;
        }
        if ( (LauncherAppWidgetProviderInfo.fromProviderInfo(getContext(), widgetInfo)
                .getWidgetFeatures() & WIDGET_FEATURE_RECONFIGURABLE) == 0) {
            return INVALID_APPWIDGET_ID;
        }
        return hostView.getAppWidgetId();
    }

    /**
     * Performs the drop action and returns the target component for the dragObject or null if
     * the action was not performed.
     */
    protected ComponentName performDropAction(View view, ItemInfo info, InstanceId instanceId) {
        if (mCurrentAccessibilityAction == RECONFIGURE) {
            int widgetId = getReconfigurableWidgetId(view);
            if (widgetId != INVALID_APPWIDGET_ID) {
                mLauncher.setWaitingForResult(
                        PendingRequestArgs.forWidgetInfo(widgetId, null, info));
                mLauncher.getAppWidgetHolder().startConfigActivity(mLauncher, widgetId,
                        REQUEST_RECONFIGURE_APPWIDGET);
            }
            return null;
        }
        if (mCurrentAccessibilityAction == DISMISS_PREDICTION) {
            if (FeatureFlags.ENABLE_DISMISS_PREDICTION_UNDO.get()) {
                mLauncher.getDragLayer()
                        .announceForAccessibility(getContext().getString(R.string.item_removed));
                Snackbar.show(mLauncher, R.string.item_removed, R.string.undo, () -> { }, () -> {
                    mStatsLogManager.logger()
                            .withInstanceId(instanceId)
                            .withItemInfo(info)
                            .log(LAUNCHER_DISMISS_PREDICTION_UNDO);
                });
            }
            return null;
        }
        // else: mCurrentAccessibilityAction == UNINSTALL

        ComponentName cn = getUninstallTarget(info);
        if (cn == null) {
            // System applications cannot be installed. For now, show a toast explaining that.
            // We may give them the option of disabling apps this way.
            Toast.makeText(mLauncher, R.string.uninstall_system_app_text, Toast.LENGTH_SHORT).show();
            return null;
        }
        try {
            Intent i = Intent.parseUri(mLauncher.getString(R.string.delete_package_intent), 0)
                    .setData(Uri.fromParts("package", cn.getPackageName(), cn.getClassName()))
                    .putExtra(Intent.EXTRA_USER, info.user);
            mLauncher.startActivity(i);
            FileLog.d(TAG, "start uninstall activity " + cn.getPackageName());
            return cn;
        } catch (URISyntaxException e) {
            Log.e(TAG, "Failed to parse intent to start uninstall activity for item=" + info);
            return null;
        }
    }

    @Override
    public void onAccessibilityDrop(View view, ItemInfo item) {
        InstanceId instanceId = new InstanceIdSequence().newInstanceId();
        doLog(instanceId, item);
        performDropAction(view, item, instanceId);
    }

    /**
     * A wrapper around {@link DragSource} which delays the {@link #onDropCompleted} action until
     * {@link #onLauncherResume}
     */
    private class DeferredOnComplete implements DragSource {

        private final DragSource mOriginal;
        private final Context mContext;

        private String mPackageName;
        private DragObject mDragObject;

        public DeferredOnComplete(DragSource original, Context context) {
            mOriginal = original;
            mContext = context;
        }

        @Override
        public void onDropCompleted(View target, DragObject d,
                boolean success) {
            mDragObject = d;
        }

        public void onLauncherResume() {
            // We use MATCH_UNINSTALLED_PACKAGES as the app can be on SD card as well.
            if (new PackageManagerHelper(mContext).getApplicationInfo(mPackageName,
                    mDragObject.dragInfo.user, PackageManager.MATCH_UNINSTALLED_PACKAGES) == null) {
                mDragObject.dragSource = mOriginal;
                mOriginal.onDropCompleted(SecondaryDropTarget.this, mDragObject, true);
                mStatsLogManager.logger().withInstanceId(mDragObject.logInstanceId)
                        .log(LAUNCHER_ITEM_UNINSTALL_COMPLETED);
            } else {
                sendFailure();
                mStatsLogManager.logger().withInstanceId(mDragObject.logInstanceId)
                        .log(LAUNCHER_ITEM_UNINSTALL_CANCELLED);
            }
        }

        public void sendFailure() {
            mDragObject.dragSource = mOriginal;
            mDragObject.cancelled = true;
            mOriginal.onDropCompleted(SecondaryDropTarget.this, mDragObject, false);
        }
    }
}
