/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.dragndrop;

import android.annotation.TargetApi;
import android.app.ActivityOptions;
import android.appwidget.AppWidgetManager;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.content.pm.LauncherApps.PinItemRequest;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.DragShadowBuilder;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.InstallShortcutReceiver;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetHost;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.LauncherAppsCompatVO;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.widget.PendingAddShortcutInfo;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.WidgetHostViewLoader;
import com.android.launcher3.widget.WidgetImageView;

import static com.android.launcher3.logging.LoggerUtils.newCommandAction;
import static com.android.launcher3.logging.LoggerUtils.newContainerTarget;
import static com.android.launcher3.logging.LoggerUtils.newItemTarget;
import static com.android.launcher3.logging.LoggerUtils.newLauncherEvent;

@TargetApi(Build.VERSION_CODES.O)
public class AddItemActivity extends BaseActivity implements OnLongClickListener, OnTouchListener {

    private static final int SHADOW_SIZE = 10;

    private static final int REQUEST_BIND_APPWIDGET = 1;
    private static final String STATE_EXTRA_WIDGET_ID = "state.widget.id";

    private final PointF mLastTouchPos = new PointF();

    private PinItemRequest mRequest;
    private LauncherAppState mApp;
    private InvariantDeviceProfile mIdp;

    private LivePreviewWidgetCell mWidgetCell;

    // Widget request specific options.
    private LauncherAppWidgetHost mAppWidgetHost;
    private AppWidgetManagerCompat mAppWidgetManager;
    private PendingAddWidgetInfo mPendingWidgetInfo;
    private int mPendingBindWidgetId;
    private Bundle mWidgetOptions;

    private boolean mFinishOnPause = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRequest = LauncherAppsCompatVO.getPinItemRequest(getIntent());
        if (mRequest == null) {
            finish();
            return;
        }

        mApp = LauncherAppState.getInstance(this);
        mIdp = mApp.getInvariantDeviceProfile();

        // Use the application context to get the device profile, as in multiwindow-mode, the
        // confirmation activity might be rotated.
        mDeviceProfile = mIdp.getDeviceProfile(getApplicationContext());

        setContentView(R.layout.add_item_confirmation_activity);
        mWidgetCell = findViewById(R.id.widget_cell);

        if (mRequest.getRequestType() == PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            setupShortcut();
        } else {
            if (!setupWidget()) {
                // TODO: show error toast?
                finish();
            }
        }

        mWidgetCell.setOnTouchListener(this);
        mWidgetCell.setOnLongClickListener(this);

        // savedInstanceState is null when the activity is created the first time (i.e., avoids
        // duplicate logging during rotation)
        if (savedInstanceState == null) {
            logCommand(Action.Command.ENTRY);
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        mLastTouchPos.set(motionEvent.getX(), motionEvent.getY());
        return false;
    }

    @Override
    public boolean onLongClick(View view) {
        // Find the position of the preview relative to the touch location.
        WidgetImageView img = mWidgetCell.getWidgetView();

        // If the ImageView doesn't have a drawable yet, the widget preview hasn't been loaded and
        // we abort the drag.
        if (img.getBitmap() == null) {
            return false;
        }

        Rect bounds = img.getBitmapBounds();
        bounds.offset(img.getLeft() - (int) mLastTouchPos.x, img.getTop() - (int) mLastTouchPos.y);

        // Start home and pass the draw request params
        PinItemDragListener listener = new PinItemDragListener(mRequest, bounds,
                img.getBitmap().getWidth(), img.getWidth());
        Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(getPackageName())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(PinItemDragListener.EXTRA_PIN_ITEM_DRAG_LISTENER, listener);

        if (!getResources().getBoolean(R.bool.allow_rotation) &&
                !Utilities.isAllowRotationPrefEnabled(this) &&
                (getResources().getConfiguration().orientation ==
                        Configuration.ORIENTATION_LANDSCAPE && !isInMultiWindowMode())) {
            // If we are starting the drag in landscape even though home is locked in portrait,
            // restart the home activity to temporarily allow rotation.
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }

        startActivity(homeIntent,
                ActivityOptions.makeCustomAnimation(this, 0, android.R.anim.fade_out).toBundle());
        mFinishOnPause = true;

        // Start a system drag and drop. We use a transparent bitmap as preview for system drag
        // as the preview is handled internally by launcher.
        ClipDescription description = new ClipDescription("", new String[]{listener.getMimeType()});
        ClipData data = new ClipData(description, new ClipData.Item(""));
        view.startDragAndDrop(data, new DragShadowBuilder(view) {

            @Override
            public void onDrawShadow(Canvas canvas) { }

            @Override
            public void onProvideShadowMetrics(Point outShadowSize, Point outShadowTouchPoint) {
                outShadowSize.set(SHADOW_SIZE, SHADOW_SIZE);
                outShadowTouchPoint.set(SHADOW_SIZE / 2, SHADOW_SIZE / 2);
            }
        }, null, View.DRAG_FLAG_GLOBAL);
        return false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mFinishOnPause) {
            finish();
        }
    }

    private void setupShortcut() {
        PinShortcutRequestActivityInfo shortcutInfo =
                new PinShortcutRequestActivityInfo(mRequest, this);
        WidgetItem item = new WidgetItem(shortcutInfo);
        mWidgetCell.getWidgetView().setTag(new PendingAddShortcutInfo(shortcutInfo));
        mWidgetCell.applyFromCellItem(item, mApp.getWidgetCache());
        mWidgetCell.ensurePreview();
    }

    private boolean setupWidget() {
        LauncherAppWidgetProviderInfo widgetInfo = LauncherAppWidgetProviderInfo
                .fromProviderInfo(this, mRequest.getAppWidgetProviderInfo(this));
        if (widgetInfo.minSpanX > mIdp.numColumns || widgetInfo.minSpanY > mIdp.numRows) {
            // Cannot add widget
            return false;
        }
        mWidgetCell.setPreview(PinItemDragListener.getPreview(mRequest));

        mAppWidgetManager = AppWidgetManagerCompat.getInstance(this);
        mAppWidgetHost = new LauncherAppWidgetHost(this);

        mPendingWidgetInfo = new PendingAddWidgetInfo(widgetInfo);
        mPendingWidgetInfo.spanX = Math.min(mIdp.numColumns, widgetInfo.spanX);
        mPendingWidgetInfo.spanY = Math.min(mIdp.numRows, widgetInfo.spanY);
        mWidgetOptions = WidgetHostViewLoader.getDefaultOptionsForWidget(this, mPendingWidgetInfo);

        WidgetItem item = new WidgetItem(widgetInfo, getPackageManager(), mIdp);
        mWidgetCell.getWidgetView().setTag(mPendingWidgetInfo);
        mWidgetCell.applyFromCellItem(item, mApp.getWidgetCache());
        mWidgetCell.ensurePreview();
        return true;
    }

    /**
     * Called when the cancel button is clicked.
     */
    public void onCancelClick(View v) {
        logCommand(Action.Command.CANCEL);
        finish();
    }

    /**
     * Called when place-automatically button is clicked.
     */
    public void onPlaceAutomaticallyClick(View v) {
        if (mRequest.getRequestType() == PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            InstallShortcutReceiver.queueShortcut(
                    new ShortcutInfoCompat(mRequest.getShortcutInfo()), this);
            logCommand(Action.Command.CONFIRM);
            mRequest.accept();
            finish();
            return;
        }

        mPendingBindWidgetId = mAppWidgetHost.allocateAppWidgetId();
        boolean success = mAppWidgetManager.bindAppWidgetIdIfAllowed(
                mPendingBindWidgetId, mRequest.getAppWidgetProviderInfo(this), mWidgetOptions);
        if (success) {
            acceptWidget(mPendingBindWidgetId);
            return;
        }

        // request bind widget
        mAppWidgetHost.startBindFlow(this, mPendingBindWidgetId,
                mRequest.getAppWidgetProviderInfo(this), REQUEST_BIND_APPWIDGET);
    }

    private void acceptWidget(int widgetId) {
        InstallShortcutReceiver.queueWidget(mRequest.getAppWidgetProviderInfo(this), widgetId, this);
        mWidgetOptions.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        mRequest.accept(mWidgetOptions);
        logCommand(Action.Command.CONFIRM);
        finish();
    }

    @Override
    public void onBackPressed() {
        logCommand(Action.Command.BACK);
        super.onBackPressed();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_BIND_APPWIDGET) {
            int widgetId = data != null
                    ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mPendingBindWidgetId)
                    : mPendingBindWidgetId;
            if (resultCode == RESULT_OK) {
                acceptWidget(widgetId);
            } else {
                // Simply wait it out.
                mAppWidgetHost.deleteAppWidgetId(widgetId);
                mPendingBindWidgetId = -1;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_EXTRA_WIDGET_ID, mPendingBindWidgetId);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mPendingBindWidgetId = savedInstanceState
                .getInt(STATE_EXTRA_WIDGET_ID, mPendingBindWidgetId);
    }

    private void logCommand(int command) {
        getUserEventDispatcher().dispatchUserEvent(newLauncherEvent(
                newCommandAction(command),
                newItemTarget(mWidgetCell.getWidgetView()),
                newContainerTarget(ContainerType.PINITEM)), null);
    }
}
