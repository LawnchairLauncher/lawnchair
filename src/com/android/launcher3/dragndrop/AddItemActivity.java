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

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_PIN_WIDGETS;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ADD_EXTERNAL_ITEM_BACK;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ADD_EXTERNAL_ITEM_CANCELLED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ADD_EXTERNAL_ITEM_DRAGGED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ADD_EXTERNAL_ITEM_PLACED_AUTOMATICALLY;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ADD_EXTERNAL_ITEM_START;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.widget.WidgetSections.NO_CATEGORY;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps.PinItemRequest;
import android.content.pm.ShortcutInfo;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.DragShadowBuilder;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.ItemInstallQueue;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.WidgetsModel;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.pm.PinRequestHelper;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.views.AbstractSlideInView;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.widget.AddItemWidgetsBottomSheet;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.LauncherWidgetHolder;
import com.android.launcher3.widget.NavigableAppWidgetHostView;
import com.android.launcher3.widget.PendingAddShortcutInfo;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.WidgetCell;
import com.android.launcher3.widget.WidgetCellPreview;
import com.android.launcher3.widget.WidgetImageView;
import com.android.launcher3.widget.WidgetManagerHelper;
import com.android.launcher3.widget.WidgetSections;

import java.util.function.Supplier;

/**
 * Activity to show pin widget dialog.
 */
@TargetApi(Build.VERSION_CODES.O)
public class AddItemActivity extends BaseActivity
        implements OnLongClickListener, OnTouchListener, AbstractSlideInView.OnCloseListener,
        WidgetCell.PreviewReadyListener {

    private static final int SHADOW_SIZE = 10;

    private static final int REQUEST_BIND_APPWIDGET = 1;
    private static final String STATE_EXTRA_WIDGET_ID = "state.widget.id";

    private final PointF mLastTouchPos = new PointF();

    private PinItemRequest mRequest;
    private LauncherAppState mApp;
    private InvariantDeviceProfile mIdp;
    private BaseDragLayer<AddItemActivity> mDragLayer;
    private AddItemWidgetsBottomSheet mSlideInView;
    private AccessibilityManager mAccessibilityManager;

    private WidgetCell mWidgetCell;

    // Widget request specific options.
    @Nullable
    private LauncherWidgetHolder mAppWidgetHolder = null;
    private WidgetManagerHelper mAppWidgetManager;
    private int mPendingBindWidgetId;
    private Bundle mWidgetOptions;

    private boolean mFinishOnPause = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRequest = PinRequestHelper.getPinItemRequest(getIntent());
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
        // Set flag to allow activity to draw over navigation and status bar.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        mDragLayer = findViewById(R.id.add_item_drag_layer);
        mDragLayer.recreateControllers();
        mWidgetCell = findViewById(R.id.widget_cell);
        mWidgetCell.addPreviewReadyListener(this);
        mAccessibilityManager =
                getApplicationContext().getSystemService(AccessibilityManager.class);

        final PackageItemInfo targetApp;
        switch (mRequest.getRequestType()) {
            case PinItemRequest.REQUEST_TYPE_SHORTCUT:
                targetApp = setupShortcut();
                break;
            case PinItemRequest.REQUEST_TYPE_APPWIDGET:
                targetApp = setupWidget();
                break;
            default:
                targetApp = null;
                break;
        }
        if (targetApp == null) {
            // TODO: show error toast?
            finish();
            return;
        }
        ApplicationInfo info = PackageManagerHelper.INSTANCE.get(this)
                .getApplicationInfo(targetApp.packageName, targetApp.user, 0);
        if (info == null) {
            finish();
            return;
        }

        WidgetCellPreview previewContainer = mWidgetCell.findViewById(
                R.id.widget_preview_container);
        previewContainer.setOnTouchListener(this);
        previewContainer.setOnLongClickListener(this);

        // savedInstanceState is null when the activity is created the first time (i.e., avoids
        // duplicate logging during rotation)
        if (savedInstanceState == null) {
            logCommand(LAUNCHER_ADD_EXTERNAL_ITEM_START);
        }

        // Set the label synchronously instead of via IconCache as this is the first thing
        // user sees
        TextView widgetAppName = findViewById(R.id.widget_appName);
        WidgetSections.WidgetSection section = targetApp.widgetCategory == NO_CATEGORY ? null
                : WidgetSections.getWidgetSections(this).get(targetApp.widgetCategory);
        widgetAppName.setText(section == null ? info.loadLabel(getPackageManager())
                : getString(section.mSectionTitle));

        mSlideInView = findViewById(R.id.add_item_bottom_sheet);
        mSlideInView.addOnCloseListener(this);
        mSlideInView.show();
        setupNavBarColor();
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
        NavigableAppWidgetHostView appWidgetHostView = mWidgetCell.getAppWidgetHostViewPreview();

        // If the ImageView doesn't have a drawable yet, the widget preview hasn't been loaded and
        // we abort the drag.
        if (img.getDrawable() == null && appWidgetHostView == null) {
            return false;
        }

        final Rect bounds;
        // Start home and pass the draw request params
        final PinItemDragListener listener;
        if (appWidgetHostView != null) {
            bounds = new Rect();
            appWidgetHostView.getSourceVisualDragBounds(bounds);
            float appWidgetHostViewScale = mWidgetCell.getAppWidgetHostViewScale();
            int xOffset =
                    appWidgetHostView.getLeft() - (int) (mLastTouchPos.x * appWidgetHostViewScale);
            int yOffset =
                    appWidgetHostView.getTop() - (int) (mLastTouchPos.y * appWidgetHostViewScale);
            bounds.offset(xOffset, yOffset);
            listener = new PinItemDragListener(
                    mRequest,
                    bounds,
                    appWidgetHostView.getMeasuredWidth(),
                    appWidgetHostView.getMeasuredWidth(),
                    appWidgetHostViewScale);
        } else {
            bounds = img.getBitmapBounds();
            bounds.offset(img.getLeft() - (int) mLastTouchPos.x,
                    img.getTop() - (int) mLastTouchPos.y);
            listener = new PinItemDragListener(mRequest, bounds,
                    img.getDrawable().getIntrinsicWidth(), img.getWidth());
        }

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

        Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .setPackage(getPackageName())
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Launcher.ACTIVITY_TRACKER.registerCallback(listener, "AddItemActivity.onLongClick");
        startActivity(homeIntent,
                ApiWrapper.INSTANCE.get(this).createFadeOutAnimOptions().toBundle());
        logCommand(LAUNCHER_ADD_EXTERNAL_ITEM_DRAGGED);
        mFinishOnPause = true;
        return false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mFinishOnPause) {
            finish();
        }
    }

    private PackageItemInfo setupShortcut() {
        PinShortcutRequestActivityInfo shortcutInfo =
                new PinShortcutRequestActivityInfo(mRequest, this);
        mWidgetCell.getWidgetView().setTag(new PendingAddShortcutInfo(shortcutInfo));
        applyWidgetItemAsync(
                () -> new WidgetItem(shortcutInfo, mApp.getIconCache(), getPackageManager()));
        return new PackageItemInfo(mRequest.getShortcutInfo().getPackage(),
                mRequest.getShortcutInfo().getUserHandle());
    }

    private PackageItemInfo setupWidget() {
        final LauncherAppWidgetProviderInfo widgetInfo = LauncherAppWidgetProviderInfo
                .fromProviderInfo(this, mRequest.getAppWidgetProviderInfo(this));
        if (widgetInfo.minSpanX > mIdp.numColumns || widgetInfo.minSpanY > mIdp.numRows) {
            // Cannot add widget
            return null;
        }
        mWidgetCell.setRemoteViewsPreview(PinItemDragListener.getPreview(mRequest));

        mAppWidgetManager = new WidgetManagerHelper(this);
        mAppWidgetHolder = LauncherWidgetHolder.newInstance(this);

        PendingAddWidgetInfo pendingInfo =
                new PendingAddWidgetInfo(widgetInfo, CONTAINER_PIN_WIDGETS);
        pendingInfo.spanX = Math.min(mIdp.numColumns, widgetInfo.spanX);
        pendingInfo.spanY = Math.min(mIdp.numRows, widgetInfo.spanY);
        mWidgetOptions = pendingInfo.getDefaultSizeOptions(this);
        mWidgetCell.getWidgetView().setTag(pendingInfo);

        applyWidgetItemAsync(() -> new WidgetItem(
                widgetInfo, mIdp, mApp.getIconCache(), mApp.getContext()));
        return WidgetsModel.newPendingItemInfo(this, widgetInfo.getComponent(),
                widgetInfo.getUser());
    }

    private void applyWidgetItemAsync(final Supplier<WidgetItem> itemProvider) {
        new AsyncTask<Void, Void, WidgetItem>() {
            @Override
            protected WidgetItem doInBackground(Void... voids) {
                return itemProvider.get();
            }

            @Override
            protected void onPostExecute(WidgetItem item) {
                mWidgetCell.applyFromCellItem(item);
            }
        }.executeOnExecutor(MODEL_EXECUTOR);
        // TODO: Create a worker looper executor and reuse that everywhere.
    }

    /**
     * Called when the cancel button is clicked.
     */
    public void onCancelClick(View v) {
        logCommand(LAUNCHER_ADD_EXTERNAL_ITEM_CANCELLED);
        mSlideInView.close(/* animate= */ true);
    }

    /**
     * Called when place-automatically button is clicked.
     */
    public void onPlaceAutomaticallyClick(View v) {
        if (mRequest.getRequestType() == PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            ShortcutInfo shortcutInfo = mRequest.getShortcutInfo();
            ItemInstallQueue.INSTANCE.get(this).queueItem(shortcutInfo);
            logCommand(LAUNCHER_ADD_EXTERNAL_ITEM_PLACED_AUTOMATICALLY);
            mRequest.accept();
            CharSequence label = shortcutInfo.getLongLabel();
            if (TextUtils.isEmpty(label)) {
                label = shortcutInfo.getShortLabel();
            }
            sendWidgetAddedToScreenAccessibilityEvent(label.toString());
            mSlideInView.close(/* animate= */ true);
            return;
        }

        mPendingBindWidgetId = mAppWidgetHolder.allocateAppWidgetId();
        AppWidgetProviderInfo widgetProviderInfo = mRequest.getAppWidgetProviderInfo(this);
        boolean success = mAppWidgetManager.bindAppWidgetIdIfAllowed(
                mPendingBindWidgetId, widgetProviderInfo, mWidgetOptions);
        if (success) {
            sendWidgetAddedToScreenAccessibilityEvent(widgetProviderInfo.label);
            acceptWidget(mPendingBindWidgetId);
            return;
        }

        // request bind widget
        mAppWidgetHolder.startBindFlow(this, mPendingBindWidgetId,
                mRequest.getAppWidgetProviderInfo(this), REQUEST_BIND_APPWIDGET);
    }

    private void acceptWidget(int widgetId) {
        ItemInstallQueue.INSTANCE.get(this)
                .queueItem(mRequest.getAppWidgetProviderInfo(this), widgetId);
        mWidgetOptions.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        mRequest.accept(mWidgetOptions);
        logCommand(LAUNCHER_ADD_EXTERNAL_ITEM_PLACED_AUTOMATICALLY);
        mSlideInView.close(/* animate= */ true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mAppWidgetHolder != null) {
            // Necessary to destroy the holder to free up possible activity context
            mAppWidgetHolder.destroy();
        }
    }

    @Override
    public void onBackPressed() {
        logCommand(LAUNCHER_ADD_EXTERNAL_ITEM_BACK);
        mSlideInView.close(/* animate= */ true);
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
                mAppWidgetHolder.deleteAppWidgetId(widgetId);
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

    @Override
    public BaseDragLayer getDragLayer() {
        return mDragLayer;
    }

    @Override
    public void onSlideInViewClosed() {
        finish();
    }

    protected void setupNavBarColor() {
        boolean isSheetDark = (getApplicationContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        getSystemUiController().updateUiState(
                SystemUiController.UI_STATE_BASE_WINDOW,
                isSheetDark ? SystemUiController.FLAG_DARK_NAV : SystemUiController.FLAG_LIGHT_NAV);
    }

    private void sendWidgetAddedToScreenAccessibilityEvent(String widgetName) {
        if (mAccessibilityManager.isEnabled()) {
            AccessibilityEvent event =
                    AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT);
            event.setContentDescription(
                    getApplicationContext().getResources().getString(
                            R.string.added_to_home_screen_accessibility_text, widgetName));
            mAccessibilityManager.sendAccessibilityEvent(event);
        }
    }

    private void logCommand(StatsLogManager.EventEnum command) {
        getStatsLogManager().logger()
                .withItemInfo((ItemInfo) mWidgetCell.getWidgetView().getTag())
                .log(command);
    }

    @Override
    public void onPreviewAvailable() {
        // Set the preview height based on "the only" widget's preview.
        mWidgetCell.setParentAlignedPreviewHeight(mWidgetCell.getPreviewContentHeight());
        mWidgetCell.post(mWidgetCell::requestLayout);
    }
}
