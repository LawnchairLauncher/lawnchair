package com.android.launcher3.widget;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import com.android.launcher3.AppWidgetResizeFrame;
import com.android.launcher3.DragLayer;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.compat.AppWidgetManagerCompat;

public class WidgetHostViewLoader {

    private static final boolean DEBUG = false;
    private static final String TAG = "WidgetHostViewLoader";

    /* constants used for widget loading state. */
    private static final int WIDGET_NO_CLEANUP_REQUIRED = -1;
    private static final int WIDGET_PRELOAD_PENDING = 0;
    private static final int WIDGET_BOUND = 1;
    private static final int WIDGET_INFLATED = 2;

    int mState = WIDGET_NO_CLEANUP_REQUIRED;

    /* Runnables to handle inflation and binding. */
    private Runnable mInflateWidgetRunnable = null;
    private Runnable mBindWidgetRunnable = null;

    /* Id of the widget being handled. */
    int mWidgetLoadingId = -1;
    PendingAddWidgetInfo mCreateWidgetInfo = null;

    // TODO: technically, this class should not have to know the existence of the launcher.
    private Launcher mLauncher;
    private Handler mHandler;

    public WidgetHostViewLoader(Launcher launcher) {
        mLauncher = launcher;
        mHandler = new Handler();
    }

    /**
     * Start loading the widget.
     */
    public void load(View v) {
        if (mCreateWidgetInfo != null) {
            // Just in case the cleanup process wasn't properly executed.
            finish(false);
        }
        boolean status = false;
        if (v.getTag() instanceof PendingAddWidgetInfo) {
            mCreateWidgetInfo = new PendingAddWidgetInfo((PendingAddWidgetInfo) v.getTag());
            status = preloadWidget(v, mCreateWidgetInfo);
        }
        if (DEBUG) {
            Log.d(TAG, String.format("load started on [state=%d, status=%s]", mState, status));
        }
    }


    /**
     * Clean up according to what the last known state was.
     * @param widgetIdUsed   {@code true} if the widgetId was consumed which can happen only
     *                       when view is fully inflated
     */
    public void finish(boolean widgetIdUsed) {
        if (DEBUG) {
            Log.d(TAG, String.format("cancel on state [%d] widgetId=[%d]",
                    mState, mWidgetLoadingId));
        }

        // If the widget was not added, we may need to do further cleanup.
        PendingAddWidgetInfo info = mCreateWidgetInfo;
        mCreateWidgetInfo = null;

        if (mState == WIDGET_PRELOAD_PENDING) {
            // We never did any preloading, so just remove pending callbacks to do so
            mHandler.removeCallbacks(mBindWidgetRunnable);
            mHandler.removeCallbacks(mInflateWidgetRunnable);
        } else if (mState == WIDGET_BOUND) {
             // Delete the widget id which was allocated
            if (mWidgetLoadingId != -1 && !info.isCustomWidget()) {
                mLauncher.getAppWidgetHost().deleteAppWidgetId(mWidgetLoadingId);
            }

            // We never got around to inflating the widget, so remove the callback to do so.
            mHandler.removeCallbacks(mInflateWidgetRunnable);
        } else if (mState == WIDGET_INFLATED && !widgetIdUsed) {
            // Delete the widget id which was allocated
            if (mWidgetLoadingId != -1 && !info.isCustomWidget()) {
                mLauncher.getAppWidgetHost().deleteAppWidgetId(mWidgetLoadingId);
            }

            // The widget was inflated and added to the DragLayer -- remove it.
            AppWidgetHostView widget = info.boundWidget;
            mLauncher.getDragLayer().removeView(widget);
        }
        setState(WIDGET_NO_CLEANUP_REQUIRED);
        mWidgetLoadingId = -1;
    }

    private boolean preloadWidget(final View v, final PendingAddWidgetInfo info) {
        final LauncherAppWidgetProviderInfo pInfo = info.info;

        final Bundle options = pInfo.isCustomWidget ? null :
                getDefaultOptionsForWidget(mLauncher, info);

        // If there is a configuration activity, do not follow thru bound and inflate.
        if (pInfo.configure != null) {
            info.bindOptions = options;
            return false;
        }
        setState(WIDGET_PRELOAD_PENDING);
        mBindWidgetRunnable = new Runnable() {
            @Override
            public void run() {
                if (pInfo.isCustomWidget) {
                    setState(WIDGET_BOUND);
                    return;
                }

                mWidgetLoadingId = mLauncher.getAppWidgetHost().allocateAppWidgetId();
                if(AppWidgetManagerCompat.getInstance(mLauncher).bindAppWidgetIdIfAllowed(
                        mWidgetLoadingId, pInfo, options)) {
                    setState(WIDGET_BOUND);
                }
            }
        };
        mHandler.post(mBindWidgetRunnable);

        mInflateWidgetRunnable = new Runnable() {
            @Override
            public void run() {
                if (mState != WIDGET_BOUND) {
                    return;
                }
                AppWidgetHostView hostView = mLauncher.getAppWidgetHost().createView(
                        (Context) mLauncher, mWidgetLoadingId, pInfo);
                info.boundWidget = hostView;
                setState(WIDGET_INFLATED);
                hostView.setVisibility(View.INVISIBLE);
                int[] unScaledSize = mLauncher.getWorkspace().estimateItemSize(info, false);

                // We want the first widget layout to be the correct size. This will be important
                // for width size reporting to the AppWidgetManager.
                DragLayer.LayoutParams lp = new DragLayer.LayoutParams(unScaledSize[0],
                        unScaledSize[1]);
                lp.x = lp.y = 0;
                lp.customPosition = true;
                hostView.setLayoutParams(lp);
                mLauncher.getDragLayer().addView(hostView);
                v.setTag(info);
            }
        };
        mHandler.post(mInflateWidgetRunnable);
        return true;
    }

    public static Bundle getDefaultOptionsForWidget(Launcher launcher, PendingAddWidgetInfo info) {
        Bundle options = null;
        Rect rect = new Rect();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            AppWidgetResizeFrame.getWidgetSizeRanges(launcher, info.spanX, info.spanY, rect);
            Rect padding = AppWidgetHostView.getDefaultPaddingForWidget(launcher,
                    info.componentName, null);

            float density = launcher.getResources().getDisplayMetrics().density;
            int xPaddingDips = (int) ((padding.left + padding.right) / density);
            int yPaddingDips = (int) ((padding.top + padding.bottom) / density);

            options = new Bundle();
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                    rect.left - xPaddingDips);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                    rect.top - yPaddingDips);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
                    rect.right - xPaddingDips);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                    rect.bottom - yPaddingDips);
        }
        return options;
    }

    private void setState(int state) {
        if (DEBUG) {
            Log.d(TAG, String.format("     state [%d -> %d]", mState, state));
        }
        mState = state;
    }
}
