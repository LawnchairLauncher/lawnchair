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
package com.android.launcher3.widget;

import static android.app.Activity.RESULT_CANCELED;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.SparseArray;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.WidgetsModel;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.ResourceBasedOverride;
import com.android.launcher3.widget.custom.CustomWidgetManager;

import java.util.function.IntConsumer;

/**
 * A wrapper for LauncherAppWidgetHost. This class is created so the AppWidgetHost could run in
 * background.
 */
public class LauncherWidgetHolder {
    public static final int APPWIDGET_HOST_ID = 1024;

    private static final int FLAG_LISTENING = 1;
    private static final int FLAG_STATE_IS_NORMAL = 1 << 1;
    private static final int FLAG_ACTIVITY_STARTED = 1 << 2;
    private static final int FLAG_ACTIVITY_RESUMED = 1 << 3;
    private static final int FLAGS_SHOULD_LISTEN =
            FLAG_STATE_IS_NORMAL | FLAG_ACTIVITY_STARTED | FLAG_ACTIVITY_RESUMED;

    @NonNull
    private final Context mContext;

    @NonNull
    private final AppWidgetHost mWidgetHost;

    @NonNull
    private final SparseArray<LauncherAppWidgetHostView> mViews = new SparseArray<>();
    @NonNull
    private final SparseArray<PendingAppWidgetHostView> mPendingViews = new SparseArray<>();
    @NonNull
    private final SparseArray<LauncherAppWidgetHostView> mDeferredViews = new SparseArray<>();
    @NonNull
    private final SparseArray<RemoteViews> mCachedRemoteViews = new SparseArray<>();

    private int mFlags = FLAG_STATE_IS_NORMAL;

    // TODO(b/191735836): Replace with ActivityOptions.KEY_SPLASH_SCREEN_STYLE when un-hidden
    private static final String KEY_SPLASH_SCREEN_STYLE = "android.activity.splashScreenStyle";
    // TODO(b/191735836): Replace with SplashScreen.SPLASH_SCREEN_STYLE_EMPTY when un-hidden
    private static final int SPLASH_SCREEN_STYLE_EMPTY = 0;

    protected LauncherWidgetHolder(@NonNull Context context,
            @Nullable IntConsumer appWidgetRemovedCallback) {
        mContext = context;
        mWidgetHost = createHost(context, appWidgetRemovedCallback);
    }

    protected AppWidgetHost createHost(
            Context context, @Nullable IntConsumer appWidgetRemovedCallback) {
        return new LauncherAppWidgetHost(context, appWidgetRemovedCallback, this);
    }

    /**
     * Starts listening to the widget updates from the server side
     */
    public void startListening() {
        if (WidgetsModel.GO_DISABLE_WIDGETS) {
            return;
        }
        setListeningFlag(true);
        try {
            mWidgetHost.startListening();
        } catch (Exception e) {
            if (!Utilities.isBinderSizeError(e)) {
                throw new RuntimeException(e);
            }
            // We're willing to let this slide. The exception is being caused by the list of
            // RemoteViews which is being passed back. The startListening relationship will
            // have been established by this point, and we will end up populating the
            // widgets upon bind anyway. See issue 14255011 for more context.
        }

        // We go in reverse order and inflate any deferred or cached widget
        for (int i = mViews.size() - 1; i >= 0; i--) {
            LauncherAppWidgetHostView view = mViews.valueAt(i);
            if (view instanceof DeferredAppWidgetHostView) {
                view.reInflate();
            }
            if (FeatureFlags.ENABLE_CACHED_WIDGET.get()) {
                final int appWidgetId = mViews.keyAt(i);
                if (view == mDeferredViews.get(appWidgetId)) {
                    // If the widget view was deferred, we'll need to call super.createView here
                    // to make the binder call to system process to fetch cumulative updates to this
                    // widget, as well as setting up this view for future updates.
                    mWidgetHost.createView(view.mLauncher, appWidgetId,
                            view.getAppWidgetInfo());
                    // At this point #onCreateView should have been called, which in turn returned
                    // the deferred view. There's no reason to keep the reference anymore, so we
                    // removed it here.
                    mDeferredViews.remove(appWidgetId);
                }
            }
        }
    }

    /**
     * Registers an "activity started/stopped" event.
     */
    public void setActivityStarted(boolean isStarted) {
        setShouldListenFlag(FLAG_ACTIVITY_STARTED, isStarted);
    }

    /**
     * Registers an "activity paused/resumed" event.
     */
    public void setActivityResumed(boolean isResumed) {
        setShouldListenFlag(FLAG_ACTIVITY_RESUMED, isResumed);
    }

    /**
     * Set the NORMAL state of the widget host
     * @param isNormal True if setting the host to be in normal state, false otherwise
     */
    public void setStateIsNormal(boolean isNormal) {
        setShouldListenFlag(FLAG_STATE_IS_NORMAL, isNormal);
    }

    /**
     * Delete the specified app widget from the host
     * @param appWidgetId The ID of the app widget to be deleted
     */
    public void deleteAppWidgetId(int appWidgetId) {
        mWidgetHost.deleteAppWidgetId(appWidgetId);
        mViews.remove(appWidgetId);
    }

    /**
     * Add the pending view to the host for complete configuration in further steps
     * @param appWidgetId The ID of the specified app widget
     * @param view The {@link PendingAppWidgetHostView} of the app widget
     */
    public void addPendingView(int appWidgetId, @NonNull PendingAppWidgetHostView view) {
        mPendingViews.put(appWidgetId, view);
    }

    /**
     * @param appWidgetId The app widget id of the specified widget
     * @return The {@link PendingAppWidgetHostView} of the widget if it exists, null otherwise
     */
    @Nullable
    protected PendingAppWidgetHostView getPendingView(int appWidgetId) {
        return mPendingViews.get(appWidgetId);
    }

    protected void removePendingView(int appWidgetId) {
        mPendingViews.remove(appWidgetId);
    }

    /**
     * Called when the launcher is destroyed
     */
    public void destroy() {
        // No-op
    }

    /**
     * @return The allocated app widget id if allocation is successful, returns -1 otherwise
     */
    public int allocateAppWidgetId() {
        if (WidgetsModel.GO_DISABLE_WIDGETS) {
            return AppWidgetManager.INVALID_APPWIDGET_ID;
        }

        return mWidgetHost.allocateAppWidgetId();
    }

    /**
     * Add a listener that is triggered when the providers of the widgets are changed
     * @param listener The listener that notifies when the providers changed
     */
    public void addProviderChangeListener(@NonNull ProviderChangedListener listener) {
        LauncherAppWidgetHost tempHost = (LauncherAppWidgetHost) mWidgetHost;
        tempHost.addProviderChangeListener(listener);
    }

    /**
     * Remove the specified listener from the host
     * @param listener The listener that is to be removed from the host
     */
    public void removeProviderChangeListener(ProviderChangedListener listener) {
        LauncherAppWidgetHost tempHost = (LauncherAppWidgetHost) mWidgetHost;
        tempHost.removeProviderChangeListener(listener);
    }

    /**
     * Starts the configuration activity for the widget
     * @param activity The activity in which to start the configuration page
     * @param widgetId The ID of the widget
     * @param requestCode The request code
     */
    public void startConfigActivity(@NonNull BaseDraggingActivity activity, int widgetId,
            int requestCode) {
        if (WidgetsModel.GO_DISABLE_WIDGETS) {
            sendActionCancelled(activity, requestCode);
            return;
        }

        try {
            TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "start: startConfigActivity");
            mWidgetHost.startAppWidgetConfigureActivityForResult(activity, widgetId, 0, requestCode,
                    getConfigurationActivityOptions(activity, widgetId));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            sendActionCancelled(activity, requestCode);
        }
    }

    private void sendActionCancelled(final BaseActivity activity, final int requestCode) {
        MAIN_EXECUTOR.execute(
                () -> activity.onActivityResult(requestCode, RESULT_CANCELED, null));
    }

    /**
     * Returns an {@link android.app.ActivityOptions} bundle from the {code activity} for launching
     * the configuration of the {@code widgetId} app widget, or null of options cannot be produced.
     */
    @Nullable
    protected Bundle getConfigurationActivityOptions(@NonNull BaseDraggingActivity activity,
            int widgetId) {
        LauncherAppWidgetHostView view = mViews.get(widgetId);
        if (view == null) return null;
        Object tag = view.getTag();
        if (!(tag instanceof ItemInfo)) return null;
        Bundle bundle = activity.getActivityLaunchOptions(view, (ItemInfo) tag).toBundle();
        bundle.putInt(KEY_SPLASH_SCREEN_STYLE, SPLASH_SCREEN_STYLE_EMPTY);
        return bundle;
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
        if (WidgetsModel.GO_DISABLE_WIDGETS) {
            sendActionCancelled(activity, requestCode);
            return;
        }

        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.getProfile());
        // TODO: we need to make sure that this accounts for the options bundle.
        // intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options);
        activity.startActivityForResult(intent, requestCode);
    }

    /**
     * Stop the host from listening to the widget updates
     */
    public void stopListening() {
        if (WidgetsModel.GO_DISABLE_WIDGETS) {
            return;
        }

        mWidgetHost.stopListening();
        setListeningFlag(false);
    }

    protected void setListeningFlag(final boolean isListening) {
        if (isListening) {
            mFlags |= FLAG_LISTENING;
            return;
        }
        mFlags &= ~FLAG_LISTENING;
    }

    /**
     * Delete the host
     */
    public void deleteHost() {
        mWidgetHost.deleteHost();
    }

    /**
     * @return The app widget ids
     */
    @NonNull
    public int[] getAppWidgetIds() {
        return mWidgetHost.getAppWidgetIds();
    }

    /**
     * Create a view for the specified app widget
     * @param context The activity context for which the view is created
     * @param appWidgetId The ID of the widget
     * @param appWidget The {@link LauncherAppWidgetProviderInfo} of the widget
     * @return A view for the widget
     */
    @NonNull
    public AppWidgetHostView createView(@NonNull Context context, int appWidgetId,
            @NonNull LauncherAppWidgetProviderInfo appWidget) {
        if (appWidget.isCustomWidget()) {
            LauncherAppWidgetHostView lahv = new LauncherAppWidgetHostView(context);
            lahv.setAppWidget(0, appWidget);
            CustomWidgetManager.INSTANCE.get(context).onViewCreated(lahv);
            return lahv;
        } else if ((mFlags & FLAG_LISTENING) == 0) {
            // Since the launcher hasn't started listening to widget updates, we can't simply call
            // super.createView here because the later will make a binder call to retrieve
            // RemoteViews from system process.
            // TODO: have launcher always listens to widget updates in background so that this
            //  check can be removed altogether.
            if (FeatureFlags.ENABLE_CACHED_WIDGET.get()
                    && mCachedRemoteViews.get(appWidgetId) != null) {
                // We've found RemoteViews from cache for this widget, so we will instantiate a
                // widget host view and populate it with the cached RemoteViews.
                final LauncherAppWidgetHostView view = new LauncherAppWidgetHostView(context);
                view.setAppWidget(appWidgetId, appWidget);
                view.updateAppWidget(mCachedRemoteViews.get(appWidgetId));
                mDeferredViews.put(appWidgetId, view);
                mViews.put(appWidgetId, view);
                return view;
            } else {
                // When cache misses, a placeholder for the widget will be returned instead.
                DeferredAppWidgetHostView view = new DeferredAppWidgetHostView(context);
                view.setAppWidget(appWidgetId, appWidget);
                mViews.put(appWidgetId, view);
                return view;
            }
        } else {
            try {
                return mWidgetHost.createView(context, appWidgetId, appWidget);
            } catch (Exception e) {
                if (!Utilities.isBinderSizeError(e)) {
                    throw new RuntimeException(e);
                }

                // If the exception was thrown while fetching the remote views, let the view stay.
                // This will ensure that if the widget posts a valid update later, the view
                // will update.
                LauncherAppWidgetHostView view = mViews.get(appWidgetId);
                if (view == null) {
                    view = onCreateView(mContext, appWidgetId, appWidget);
                }
                view.setAppWidget(appWidgetId, appWidget);
                view.switchToErrorView();
                return view;
            }
        }
    }

    /**
     * Listener for getting notifications on provider changes.
     */
    public interface ProviderChangedListener {
        /**
         * Notify the listener that the providers have changed
         */
        void notifyWidgetProvidersChanged();
    }

    /**
     * Called to return a proper view when creating a view
     * @param context The context for which the widget view is created
     * @param appWidgetId The ID of the added widget
     * @param appWidget The provider info of the added widget
     * @return A view for the specified app widget
     */
    @NonNull
    public LauncherAppWidgetHostView onCreateView(Context context, int appWidgetId,
            AppWidgetProviderInfo appWidget) {
        final LauncherAppWidgetHostView view;
        if (getPendingView(appWidgetId) != null) {
            view = getPendingView(appWidgetId);
            removePendingView(appWidgetId);
        } else if (mDeferredViews.get(appWidgetId) != null) {
            // In case the widget view is deferred, we will simply return the deferred view as
            // opposed to instantiate a new instance of LauncherAppWidgetHostView since launcher
            // already added the former to the workspace.
            view = mDeferredViews.get(appWidgetId);
        } else {
            view = new LauncherAppWidgetHostView(context);
        }
        mViews.put(appWidgetId, view);
        return view;
    }

    /**
     * Clears all the views from the host
     */
    public void clearViews() {
        LauncherAppWidgetHost tempHost = (LauncherAppWidgetHost) mWidgetHost;
        tempHost.clearViews();
        if (FeatureFlags.ENABLE_CACHED_WIDGET.get()) {
            // First, we clear any previously cached content from existing widgets
            mCachedRemoteViews.clear();
            mDeferredViews.clear();
            // Then we proceed to cache the content from the widgets
            for (int i = 0; i < mViews.size(); i++) {
                final int appWidgetId = mViews.keyAt(i);
                final LauncherAppWidgetHostView view = mViews.get(appWidgetId);
                mCachedRemoteViews.put(appWidgetId, view.mLastRemoteViews);
            }
        }
        mViews.clear();
    }

    /**
     * @return True if the host is listening to the updates, false otherwise
     */
    public boolean isListening() {
        return (mFlags & FLAG_LISTENING) != 0;
    }

    /**
     * Sets or unsets a flag the can change whether the widget host should be in the listening
     * state.
     */
    private void setShouldListenFlag(int flag, boolean on) {
        if (on) {
            mFlags |= flag;
        } else {
            mFlags &= ~flag;
        }

        final boolean listening = isListening();
        if (!listening && (mFlags & FLAGS_SHOULD_LISTEN) == FLAGS_SHOULD_LISTEN) {
            // Postpone starting listening until all flags are on.
            startListening();
        } else if (listening && (mFlags & FLAG_ACTIVITY_STARTED) == 0) {
            // Postpone stopping listening until the activity is stopped.
            stopListening();
        }
    }

    /**
     * Returns the new LauncherWidgetHolder instance
     */
    public static LauncherWidgetHolder newInstance(Context context) {
        return HolderFactory.newFactory(context).newInstance(context, null);
    }

    /**
     * A factory class that generates new instances of {@code LauncherWidgetHolder}
     */
    public static class HolderFactory implements ResourceBasedOverride {

        /**
         * @param context The context of the caller
         * @param appWidgetRemovedCallback The callback that is called when widgets are removed
         * @return A new instance of {@code LauncherWidgetHolder}
         */
        public LauncherWidgetHolder newInstance(@NonNull Context context,
                @Nullable IntConsumer appWidgetRemovedCallback) {
            return new LauncherWidgetHolder(context, appWidgetRemovedCallback);
        }

        /**
         * @param context The context of the caller
         * @return A new instance of factory class for widget holders. If not specified, returning
         * {@code HolderFactory} by default.
         */
        public static HolderFactory newFactory(Context context) {
            return Overrides.getObject(
                    HolderFactory.class, context, R.string.widget_holder_factory_class);
        }
    }
}
