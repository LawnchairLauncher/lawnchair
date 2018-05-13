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

package ch.deletescape.lawnchair.dragndrop;

import android.annotation.TargetApi;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.pm.LauncherApps.PinItemRequest;
import android.graphics.PointF;
import android.os.Build;
import android.os.Bundle;
import ch.deletescape.lawnchair.*;
import ch.deletescape.lawnchair.compat.AppWidgetManagerCompat;
import ch.deletescape.lawnchair.compat.LauncherAppsCompatVO;
import ch.deletescape.lawnchair.shortcuts.ShortcutInfoCompat;
import ch.deletescape.lawnchair.widget.PendingAddWidgetInfo;

@TargetApi(Build.VERSION_CODES.O)
public class AddItemActivity extends Activity {

    private static final int SHADOW_SIZE = 10;

    private static final int REQUEST_BIND_APPWIDGET = 1;
    private static final String STATE_EXTRA_WIDGET_ID = "state.widget.id";

    private final PointF mLastTouchPos = new PointF();

    private PinItemRequest mRequest;
    private LauncherAppState mApp;
    private InvariantDeviceProfile mIdp;

    // Widget request specific options.
    private LauncherAppWidgetHost mAppWidgetHost;
    private AppWidgetManagerCompat mAppWidgetManager;
    private PendingAddWidgetInfo mPendingWidgetInfo;
    private int mPendingBindWidgetId;
    private Bundle mWidgetOptions;

    private boolean mFinishOnPause = false;

    private DeviceProfile mDeviceProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRequest = LauncherAppsCompatVO.getPinItemRequest(getIntent());
        if (mRequest == null) {
            finish();
            return;
        }

        mApp = LauncherAppState.getInstance();
        mIdp = mApp.getInvariantDeviceProfile();

        // Use the application context to get the device profile, as in multiwindow-mode, the
        // confirmation activity might be rotated.
        mDeviceProfile = mIdp.getDeviceProfile(getApplicationContext());

        onPlaceAutomaticallyClick();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mFinishOnPause) {
            finish();
        }
    }

    /**
     * Called when place-automatically button is clicked.
     */
    public void onPlaceAutomaticallyClick() {
        if (mRequest.getRequestType() == PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            InstallShortcutReceiver.queueShortcut(
                    new ShortcutInfoCompat(mRequest.getShortcutInfo()), this);
            mRequest.accept();
            finish();
            goHome();
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

    private void goHome() {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        startActivity(intent);
    }

    private void acceptWidget(int widgetId) {
        InstallShortcutReceiver.queueWidget(mRequest.getAppWidgetProviderInfo(this), widgetId, this);
        mWidgetOptions.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        mRequest.accept(mWidgetOptions);
        finish();
        goHome();
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
}
