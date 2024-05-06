package com.android.launcher3.widget;

import android.appwidget.AppWidgetHostView;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import com.android.launcher3.DropTarget;
import com.android.launcher3.Launcher;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.util.Thunk;

public class WidgetHostViewLoader implements DragController.DragListener {
    private static final String TAG = "WidgetHostViewLoader";
    private static final boolean LOGD = false;

    /* Runnables to handle inflation and binding. */
    @Thunk Runnable mInflateWidgetRunnable = null;
    private Runnable mBindWidgetRunnable = null;

    // TODO: technically, this class should not have to know the existence of the launcher.
    @Thunk Launcher mLauncher;
    @Thunk Handler mHandler;
    @Thunk final View mView;
    @Thunk final PendingAddWidgetInfo mInfo;

    // Widget id generated for binding a widget host view or -1 for invalid id. The id is
    // not is use as long as it is stored here and can be deleted safely. Once its used, this value
    // to be set back to -1.
    @Thunk int mWidgetLoadingId = -1;

    public WidgetHostViewLoader(Launcher launcher, View view) {
        mLauncher = launcher;
        mHandler = new Handler();
        mView = view;
        mInfo = (PendingAddWidgetInfo) view.getTag();
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        preloadWidget();
    }

    @Override
    public void onDragEnd() {
        if (LOGD) {
            Log.d(TAG, "Cleaning up in onDragEnd()...");
        }

        // Cleanup up preloading state.
        mLauncher.getDragController().removeDragListener(this);

        mHandler.removeCallbacks(mBindWidgetRunnable);
        mHandler.removeCallbacks(mInflateWidgetRunnable);

        // Cleanup widget id
        if (mWidgetLoadingId != -1) {
            mLauncher.getAppWidgetHolder().deleteAppWidgetId(mWidgetLoadingId);
            mWidgetLoadingId = -1;
        }

        // The widget was inflated and added to the DragLayer -- remove it.
        if (mInfo.boundWidget != null) {
            if (LOGD) {
                Log.d(TAG, "...removing widget from drag layer");
            }
            mLauncher.getDragLayer().removeView(mInfo.boundWidget);
            mLauncher.getAppWidgetHolder().deleteAppWidgetId(mInfo.boundWidget.getAppWidgetId());
            mInfo.boundWidget = null;
        }
    }

    /**
     * Start preloading the widget.
     */
    private boolean preloadWidget() {
        final LauncherAppWidgetProviderInfo pInfo = mInfo.info;

        if (pInfo.isCustomWidget()) {
            return false;
        }
        final Bundle options = mInfo.getDefaultSizeOptions(mLauncher);

        // If there is a configuration activity, do not follow thru bound and inflate.
        if (mInfo.getHandler().needsConfigure()) {
            mInfo.bindOptions = options;
            return false;
        }

        mBindWidgetRunnable = new Runnable() {
            @Override
            public void run() {
                mWidgetLoadingId = mLauncher.getAppWidgetHolder().allocateAppWidgetId();
                if (LOGD) {
                    Log.d(TAG, "Binding widget, id: " + mWidgetLoadingId);
                }
                if (new WidgetManagerHelper(mLauncher).bindAppWidgetIdIfAllowed(
                        mWidgetLoadingId, pInfo, options)) {

                    // Widget id bound. Inflate the widget.
                    mHandler.post(mInflateWidgetRunnable);
                }
            }
        };

        mInflateWidgetRunnable = new Runnable() {
            @Override
            public void run() {
                if (LOGD) {
                    Log.d(TAG, "Inflating widget, id: " + mWidgetLoadingId);
                }
                if (mWidgetLoadingId == -1) {
                    return;
                }
                AppWidgetHostView hostView = mLauncher.getAppWidgetHolder().createView(
                        mWidgetLoadingId, pInfo);
                mInfo.boundWidget = hostView;

                // We used up the widget Id in binding the above view.
                mWidgetLoadingId = -1;

                hostView.setVisibility(View.INVISIBLE);
                int[] unScaledSize = mLauncher.getWorkspace().estimateItemSize(mInfo);
                // We want the first widget layout to be the correct size. This will be important
                // for width size reporting to the AppWidgetManager.
                DragLayer.LayoutParams lp = new DragLayer.LayoutParams(unScaledSize[0],
                        unScaledSize[1]);
                lp.x = lp.y = 0;
                lp.customPosition = true;
                hostView.setLayoutParams(lp);
                if (LOGD) {
                    Log.d(TAG, "Adding host view to drag layer");
                }
                mLauncher.getDragLayer().addView(hostView);
                mView.setTag(mInfo);
            }
        };

        if (LOGD) {
            Log.d(TAG, "About to bind/inflate widget");
        }
        mHandler.post(mBindWidgetRunnable);
        return true;
    }

}
