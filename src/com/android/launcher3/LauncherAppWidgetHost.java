/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.launcher3;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.os.DeadObjectException;
import android.os.TransactionTooLargeException;
import android.view.LayoutInflater;
import android.view.View;

import java.util.ArrayList;


/**
 * Specific {@link AppWidgetHost} that creates our {@link LauncherAppWidgetHostView}
 * which correctly captures all long-press events. This ensures that users can
 * always pick up and move widgets.
 */
public class LauncherAppWidgetHost extends AppWidgetHost {

    private final ArrayList<Runnable> mProviderChangeListeners = new ArrayList<Runnable>();

    private int mQsbWidgetId = -1;
    private Launcher mLauncher;

    public LauncherAppWidgetHost(Launcher launcher, int hostId) {
        super(launcher, hostId);
        mLauncher = launcher;
    }

    public void setQsbWidgetId(int widgetId) {
        mQsbWidgetId = widgetId;
    }

    @Override
    protected AppWidgetHostView onCreateView(Context context, int appWidgetId,
            AppWidgetProviderInfo appWidget) {
        if (appWidgetId == mQsbWidgetId) {
            return new LauncherAppWidgetHostView(context) {

                @Override
                protected View getErrorView() {
                    // For the QSB, show an empty view instead of an error view.
                    return new View(getContext());
                }
            };
        }
        return new LauncherAppWidgetHostView(context);
    }

    @Override
    public void startListening() {
        try {
            super.startListening();
        } catch (Exception e) {
            if (e.getCause() instanceof TransactionTooLargeException ||
                    e.getCause() instanceof DeadObjectException) {
                // We're willing to let this slide. The exception is being caused by the list of
                // RemoteViews which is being passed back. The startListening relationship will
                // have been established by this point, and we will end up populating the
                // widgets upon bind anyway. See issue 14255011 for more context.
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void stopListening() {
        super.stopListening();
        clearViews();
    }

    public void addProviderChangeListener(Runnable callback) {
        mProviderChangeListeners.add(callback);
    }

    public void removeProviderChangeListener(Runnable callback) {
        mProviderChangeListeners.remove(callback);
    }

    protected void onProvidersChanged() {
        if (!mProviderChangeListeners.isEmpty()) {
            for (Runnable callback : new ArrayList<>(mProviderChangeListeners)) {
                callback.run();
            }
        }

        if (Utilities.ATLEAST_MARSHMALLOW) {
            mLauncher.notifyWidgetProvidersChanged();
        }
    }

    public AppWidgetHostView createView(Context context, int appWidgetId,
            LauncherAppWidgetProviderInfo appWidget) {
        if (appWidget.isCustomWidget) {
            LauncherAppWidgetHostView lahv = new LauncherAppWidgetHostView(context);
            LayoutInflater inflater = (LayoutInflater)
                    context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            inflater.inflate(appWidget.initialLayout, lahv);
            lahv.setAppWidget(0, appWidget);
            lahv.updateLastInflationOrientation();
            return lahv;
        } else {
            return super.createView(context, appWidgetId, appWidget);
        }
    }

    /**
     * Called when the AppWidget provider for a AppWidget has been upgraded to a new apk.
     */
    @Override
    protected void onProviderChanged(int appWidgetId, AppWidgetProviderInfo appWidget) {
        LauncherAppWidgetProviderInfo info = LauncherAppWidgetProviderInfo.fromProviderInfo(
                mLauncher, appWidget);
        super.onProviderChanged(appWidgetId, info);
        // The super method updates the dimensions of the providerInfo. Update the
        // launcher spans accordingly.
        info.initSpans();
    }
}
