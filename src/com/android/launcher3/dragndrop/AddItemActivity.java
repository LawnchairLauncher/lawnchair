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
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.InstallShortcutReceiver;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.R;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.PinItemRequestCompat;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.WidgetCell;
import com.android.launcher3.widget.WidgetHostViewLoader;

@TargetApi(Build.VERSION_CODES.N_MR1)
public class AddItemActivity extends BaseActivity {

    private static final int REQUEST_BIND_APPWIDGET = 1;
    private static final String STATE_EXTRA_WIDGET_ID = "state.widget.id";

    private PinItemRequestCompat mRequest;
    private LauncherAppState mApp;
    private InvariantDeviceProfile mIdp;

    private WidgetCell mWidgetCell;

    // Widget request specific options.
    private AppWidgetHost mAppWidgetHost;
    private AppWidgetManagerCompat mAppWidgetManager;
    private PendingAddWidgetInfo mPendingWidgetInfo;
    private int mPendingBindWidgetId;
    private Bundle mWidgetOptions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRequest = PinItemRequestCompat.getPinItemRequest(getIntent());
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
        mWidgetCell = (WidgetCell) findViewById(R.id.widget_cell);

        if (mRequest.getRequestType() == PinItemRequestCompat.REQUEST_TYPE_SHORTCUT) {
            setupShortcut();
        } else {
            if (!setupWidget()) {
                // TODO: show error toast?
                finish();
            }
        }
    }

    private void setupShortcut() {
        WidgetItem item = new WidgetItem(new PinShortcutRequestActivityInfo(
                mRequest.getShortcutInfo(), this));
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

        mAppWidgetManager = AppWidgetManagerCompat.getInstance(this);
        mAppWidgetHost = new AppWidgetHost(this, Launcher.APPWIDGET_HOST_ID);

        mPendingWidgetInfo = new PendingAddWidgetInfo(widgetInfo);
        mPendingWidgetInfo.spanX = Math.min(mIdp.numColumns, widgetInfo.spanX);
        mPendingWidgetInfo.spanY = Math.min(mIdp.numRows, widgetInfo.spanY);
        mWidgetOptions = WidgetHostViewLoader.getDefaultOptionsForWidget(this, mPendingWidgetInfo);

        WidgetItem item = new WidgetItem(widgetInfo, getPackageManager(), mIdp);
        mWidgetCell.applyFromCellItem(item, mApp.getWidgetCache());
        mWidgetCell.ensurePreview();
        return true;
    }

    /**
     * Called when the cancel button is clicked.
     */
    public void onCancelClick(View v) {
        finish();
    }

    /**
     * Called when place-automatically button is clicked.
     */
    public void onPlaceAutomaticallyClick(View v) {
        if (mRequest.getRequestType() == PinItemRequestCompat.REQUEST_TYPE_SHORTCUT) {
            InstallShortcutReceiver.queueShortcut(
                    new ShortcutInfoCompat(mRequest.getShortcutInfo()), this);
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
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mPendingBindWidgetId);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,
                mPendingWidgetInfo.componentName);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE,
                mRequest.getAppWidgetProviderInfo(this).getProfile());
        startActivityForResult(intent, REQUEST_BIND_APPWIDGET);
    }

    private void acceptWidget(int widgetId) {
        InstallShortcutReceiver.queueWidget(mRequest.getAppWidgetProviderInfo(this), widgetId, this);
        mWidgetOptions.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        mRequest.accept(mWidgetOptions);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
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
}
