/**
 * Copyright (C) 2022 The Android Open Source Project
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

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.uioverrides.ApiWrapper;
import com.android.launcher3.widget.LauncherAppWidgetHost;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.PendingAppWidgetHostView;

import java.util.function.IntConsumer;

/**
 * A wrapper for LauncherAppWidgetHost. This class is created so the AppWidgetHost could run in
 * background.
 */
public class LauncherWidgetHolder {
    @NonNull
    private final LauncherAppWidgetHost mWidgetHost;

    public LauncherWidgetHolder(@NonNull Context context) {
        this(context, null);
    }

    public LauncherWidgetHolder(@NonNull Context context,
            @Nullable IntConsumer appWidgetRemovedCallback) {
        mWidgetHost = new LauncherAppWidgetHost(context, appWidgetRemovedCallback);
    }

    /**
     * Starts listening to the widget updates from the server side
     */
    public void startListening() {
        mWidgetHost.startListening();
    }

    /**
     * Set the STARTED state of the widget host
     * @param isStarted True if setting the host as started, false otherwise
     */
    public void setActivityStarted(boolean isStarted) {
        mWidgetHost.setActivityStarted(isStarted);
    }

    /**
     * Set the RESUMED state of the widget host
     * @param isResumed True if setting the host as resumed, false otherwise
     */
    public void setActivityResumed(boolean isResumed) {
        mWidgetHost.setActivityResumed(isResumed);
    }

    /**
     * Set the NORMAL state of the widget host
     * @param isNormal True if setting the host to be in normal state, false otherwise
     */
    public void setStateIsNormal(boolean isNormal) {
        mWidgetHost.setStateIsNormal(isNormal);
    }

    /**
     * Delete the specified app widget from the host
     * @param appWidgetId The ID of the app widget to be deleted
     */
    public void deleteAppWidgetId(int appWidgetId) {
        mWidgetHost.deleteAppWidgetId(appWidgetId);
    }

    /**
     * Add the pending view to the host for complete configuration in further steps
     * @param appWidgetId The ID of the specified app widget
     * @param view The {@link PendingAppWidgetHostView} of the app widget
     */
    public void addPendingView(int appWidgetId, @NonNull PendingAppWidgetHostView view) {
        mWidgetHost.addPendingView(appWidgetId, view);
    }

    /**
     * @return True if the host is listening to the widget updates, false otherwise
     */
    public boolean isListening() {
        return mWidgetHost.isListening();
    }

    /**
     * @return The allocated app widget id if allocation is successful, returns -1 otherwise
     */
    public int allocateAppWidgetId() {
        return mWidgetHost.allocateAppWidgetId();
    }

    /**
     * Add a listener that is triggered when the providers of the widgets are changed
     * @param listener The listener that notifies when the providers changed
     */
    public void addProviderChangeListener(
            @NonNull LauncherAppWidgetHost.ProviderChangedListener listener) {
        mWidgetHost.addProviderChangeListener(listener);
    }

    /**
     * Remove the specified listener from the host
     * @param listener The listener that is to be removed from the host
     */
    public void removeProviderChangeListener(
            LauncherAppWidgetHost.ProviderChangedListener listener) {
        mWidgetHost.removeProviderChangeListener(listener);
    }

    /**
     * Starts the configuration activity for the widget
     * @param activity The activity in which to start the configuration page
     * @param widgetId The ID of the widget
     * @param requestCode The request code
     */
    public void startConfigActivity(@NonNull BaseDraggingActivity activity, int widgetId,
            int requestCode) {
        mWidgetHost.startConfigActivity(activity, widgetId, requestCode);
    }

    /**
     * Starts the binding flow for the widget
     * @param activity The activity for which to bind the widget
     * @param appWidgetId The ID of the widget
     * @param info The {@link AppWidgetProviderInfo} of the widget
     * @param requestCode The request code
     */
    public void startBindFlow(@NonNull BaseActivity activity,
            int appWidgetId, @NonNull AppWidgetProviderInfo info, int requestCode) {
        mWidgetHost.startBindFlow(activity, appWidgetId, info, requestCode);
    }

    /**
     * Stop the host from listening to the widget updates
     */
    public void stopListening() {
        mWidgetHost.stopListening();
    }

    /**
     * Create a view for the specified app widget
     * @param context The activity context for which the view is created
     * @param appWidgetId The ID of the widget
     * @param info The {@link LauncherAppWidgetProviderInfo} of the widget
     * @return A view for the widget
     */
    @NonNull
    public AppWidgetHostView createView(@NonNull Context context, int appWidgetId,
            @NonNull LauncherAppWidgetProviderInfo info) {
        return mWidgetHost.createView(context, appWidgetId, info);
    }

    /**
     * Set the interaction handler for the widget host
     * @param handler The interaction handler
     */
    public void setInteractionHandler(
            @Nullable LauncherAppWidgetHost.LauncherWidgetInteractionHandler handler) {
        ApiWrapper.setHostInteractionHandler(mWidgetHost, handler);
    }

    /**
     * Clears all the views from the host
     */
    public void clearViews() {
        mWidgetHost.clearViews();
    }
}
