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

import static com.android.launcher3.BuildConfigs.WIDGETS_ENABLED;
import static com.android.launcher3.Flags.enableWorkspaceInflation;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.widget.LauncherAppWidgetProviderInfo.fromProviderInfo;
import static com.android.launcher3.widget.ListenableAppWidgetHost.getWidgetHolderExecutor;

import android.app.ActivityOptions;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.ActivityOptionsWrapper;
import com.android.launcher3.util.ResourceBasedOverride;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.ListenableAppWidgetHost.ProviderChangedListener;
import com.android.launcher3.widget.custom.CustomWidgetManager;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import app.lawnchair.LawnchairAppWidgetHostView;

/**
 * A wrapper for LauncherAppWidgetHost. This class is created so the AppWidgetHost could run in
 * background.
 */
public class LauncherWidgetHolder {

    private static final String TAG = "LauncherWidgetHolder";

    public static final int APPWIDGET_HOST_ID = 1024;

    protected static final int FLAG_LISTENING = 1;
    protected static final int FLAG_STATE_IS_NORMAL = 1 << 1;
    protected static final int FLAG_ACTIVITY_STARTED = 1 << 2;
    protected static final int FLAG_ACTIVITY_RESUMED = 1 << 3;

    private static final int FLAGS_SHOULD_LISTEN =
            FLAG_STATE_IS_NORMAL | FLAG_ACTIVITY_STARTED | FLAG_ACTIVITY_RESUMED;

    // TODO(b/191735836): Replace with ActivityOptions.KEY_SPLASH_SCREEN_STYLE when un-hidden
    private static final String KEY_SPLASH_SCREEN_STYLE = "android.activity.splashScreenStyle";
    // TODO(b/191735836): Replace with SplashScreen.SPLASH_SCREEN_STYLE_EMPTY when un-hidden
    private static final int SPLASH_SCREEN_STYLE_EMPTY = 0;

    @NonNull
    protected final Context mContext;

    @NonNull
    protected final ListenableAppWidgetHost mWidgetHost;

    @NonNull
    protected final SparseArray<LauncherAppWidgetHostView> mViews = new SparseArray<>();

    /** package visibility */
    final List<ProviderChangedListener> mProviderChangedListeners = new ArrayList<>();

    protected AtomicInteger mFlags = new AtomicInteger(FLAG_STATE_IS_NORMAL);

    @Nullable
    private Consumer<LauncherAppWidgetHostView> mOnViewCreationCallback;

    /** package visibility */
    @Nullable IntConsumer mAppWidgetRemovedCallback;

    @AssistedInject
    protected LauncherWidgetHolder(@Assisted("UI_CONTEXT") @NonNull Context context) {
        this(context, APPWIDGET_HOST_ID);
    }

    public LauncherWidgetHolder(@NonNull Context context, int hostId) {
        this(context, new LauncherAppWidgetHost(context, hostId));
    }

    protected LauncherWidgetHolder(
            @NonNull Context context, @NonNull ListenableAppWidgetHost appWidgetHost) {
        mContext = context;
        mWidgetHost = appWidgetHost;
        MAIN_EXECUTOR.execute(() ->  mWidgetHost.getHolders().add(this));
    }

    /** Starts listening to the widget updates from the server side */
    public void startListening() {
        if (!WIDGETS_ENABLED) {
            return;
        }

        getWidgetHolderExecutor().execute(() -> {
            try {
                mWidgetHost.startListening();
            } catch (Exception e) {
                if (!Utilities.isBinderSizeError(e)) {
                    Log.e(TAG, "RuntimeException 1");
                    return;
                }
                // We're willing to let this slide. The exception is being caused by the list of
                // RemoteViews which is being passed back. The startListening relationship will
                // have been established by this point, and we will end up populating the
                // widgets upon bind anyway. See issue 14255011 for more context.
            }
        // TODO: Investigate why widgetHost.startListening() always return non-empty updates
            setListeningFlag(true);

            MAIN_EXECUTOR.execute(this::updateDeferredView);
        });
    }

    /** Update any views which have been deferred because the host was not listening */
    protected void updateDeferredView() {
        // Update any views which have been deferred because the host was not listening.
        // We go in reverse order and inflate any deferred or cached widget
        for (int i = mViews.size() - 1; i >= 0; i--) {
            LauncherAppWidgetHostView view = mViews.valueAt(i);
            if (view instanceof PendingAppWidgetHostView pv) {
                pv.reInflate();
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
     * Called when the launcher is destroyed
     */
    public void destroy() {
        try {
            MAIN_EXECUTOR.submit(() -> {
                clearViews();
                mWidgetHost.getHolders().remove(this);
            }).get();
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove self from holder list", e);
        }
    }

    /**
     * @return The allocated app widget id if allocation is successful, returns -1 otherwise
     */
    public int allocateAppWidgetId() {
        if (!WIDGETS_ENABLED) {
            return AppWidgetManager.INVALID_APPWIDGET_ID;
        }

        return mWidgetHost.allocateAppWidgetId();
    }

    /**
     * Add a listener that is triggered when the providers of the widgets are changed
     * @param listener The listener that notifies when the providers changed
     */
    public void addProviderChangeListener(@NonNull ProviderChangedListener listener) {
        MAIN_EXECUTOR.execute(() -> mProviderChangedListeners.add(listener));
    }

    /**
     * Remove the specified listener from the host
     * @param listener The listener that is to be removed from the host
     */
    public void removeProviderChangeListener(ProviderChangedListener listener) {
        MAIN_EXECUTOR.execute(() -> mProviderChangedListeners.remove(listener));
    }

    /**
     * Sets a callbacks for whenever a widget view is created
     */
    public void setOnViewCreationCallback(@Nullable Consumer<LauncherAppWidgetHostView> callback) {
        mOnViewCreationCallback = callback;
    }

    /** Sets a callback for listening app widget removals */
    public void setAppWidgetRemovedCallback(@Nullable IntConsumer callback) {
        mAppWidgetRemovedCallback = callback;
    }

    /**
     * Starts the configuration activity for the widget
     * @param activity The activity in which to start the configuration page
     * @param widgetId The ID of the widget
     * @param requestCode The request code
     */
    public void startConfigActivity(@NonNull BaseActivity activity, int widgetId, int requestCode) {
        startConfigActivity(activity, widgetId, requestCode, 0);
    }

    /**
     * Starts the configuration activity for the widget with retry counter
     * 
     * @param activity    The activity in which to start the configuration page
     * @param widgetId    The ID of the widget
     * @param requestCode The request code
     * @param retryCount  The number of retries attempted
     */
    private void startConfigActivity(@NonNull BaseActivity activity, int widgetId,
            int requestCode, int retryCount) {
        if (!WIDGETS_ENABLED) {
            sendActionCancelled(activity, requestCode);
            return;
        }

        // Prevent infinite loops by limiting retries
        if (retryCount >= 3) {
            Log.e(this.getClass().getName(), "Too many retries for widget configuration, cancelling");
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
        } catch (IllegalArgumentException e) {
            // Widget ID became invalid, possibly due to two-step configuration some cases
            Log.e(this.getClass().getName(), "Widget ID became invalid during configuration (retry " + retryCount + ")", e);
            handleInvalidWidgetId(activity, widgetId, requestCode, retryCount + 1);
        }
    }

    private void handleInvalidWidgetId(BaseActivity activity, int widgetId, int requestCode) {
        handleInvalidWidgetId(activity, widgetId, requestCode, 0);
    }

    private void handleInvalidWidgetId(BaseActivity activity, int widgetId, int requestCode, int retryCount) {
        // Remove the invalid widget
        deleteAppWidgetId(widgetId);

        int newWidgetId = allocateAppWidgetId();
        if (newWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            startConfigActivity(activity, newWidgetId, requestCode, retryCount);
        } else {
            sendActionCancelled(activity, requestCode);
        }
    }

    private void sendActionCancelled(final BaseActivity activity, final int requestCode) {
        MAIN_EXECUTOR.execute(
                () -> activity.onActivityResult(requestCode, RESULT_CANCELED, null));
    }

    private Bundle getDefaultConfigurationActivityOptions() {
        // Must allow background activity start for U.
        return Utilities.allowBGLaunch(ActivityOptions.makeBasic())
                .toBundle();
    }

    /**
     * Returns an {@link android.app.ActivityOptions} bundle from the {code activity} for launching
     * the configuration of the {@code widgetId} app widget, or null of options cannot be produced.
     */
    @Nullable
    protected Bundle getConfigurationActivityOptions(@NonNull ActivityContext activity,
            int widgetId) {
        LauncherAppWidgetHostView view = mViews.get(widgetId);
        if (view == null) {
            return activity.makeDefaultActivityOptions(
                    -1 /* SPLASH_SCREEN_STYLE_UNDEFINED */).toBundle();
        }
        Object tag = view.getTag();
        if (!(tag instanceof ItemInfo)) {
            return activity.makeDefaultActivityOptions(
                    -1 /* SPLASH_SCREEN_STYLE_UNDEFINED */).toBundle();
        }
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
        if (!WIDGETS_ENABLED) {
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

    /** Stop the host from listening to the widget updates */
    public void stopListening() {
        if (!WIDGETS_ENABLED) {
            return;
        }
        getWidgetHolderExecutor().execute(() -> {
            mWidgetHost.stopListening();
            setListeningFlag(false);
        });
    }

    /**
     * Update {@link #FLAG_LISTENING} on {@link #mFlags} after making binder calls from
     * {@link #mWidgetHost}.
     */
    @WorkerThread
    protected void setListeningFlag(final boolean isListening) {
        if (isListening) {
            mFlags.updateAndGet(old -> old | FLAG_LISTENING);
            return;
        }
        mFlags.updateAndGet(old -> old & ~FLAG_LISTENING);
    }

    /**
     * @return The app widget ids
     */
    @NonNull
    public int[] getAppWidgetIds() {
        return mWidgetHost.getAppWidgetIds();
    }

    /**
     * Adds a callback to be run everytime the provided app widget updates.
     * @return a closable to remove this callback
     */
    public SafeCloseable addOnUpdateListener(
            int appWidgetId, LauncherAppWidgetProviderInfo appWidget, Runnable callback) {
        if (createView(appWidgetId, appWidget) instanceof ListenableHostView lhv) {
            return lhv.addUpdateListener(callback);
        }
        return () -> { };
    }

    /**
     * Create a view for the specified app widget. When calling this method from a background
     * thread, the returned view will not receive ongoing updates. The caller needs to reattach
     * the view using {@link #attachViewToHostAndGetAttachedView} on UIThread
     *
     * @param appWidgetId The ID of the widget
     * @param appWidget   The {@link LauncherAppWidgetProviderInfo} of the widget
     * @return A view for the widget
     */
    @NonNull
    public AppWidgetHostView createView(
            int appWidgetId, @NonNull LauncherAppWidgetProviderInfo appWidget) {
        if (appWidget.isCustomWidget()) {
            LauncherAppWidgetHostView lahv = new LauncherAppWidgetHostView(mContext);
            lahv.setAppWidget(0, appWidget);
            CustomWidgetManager.INSTANCE.get(mContext).onViewCreated(lahv);
            return lahv;
        }

        LauncherAppWidgetHostView view = createViewInternal(appWidgetId, appWidget);
        if (mOnViewCreationCallback != null) mOnViewCreationCallback.accept(view);
        // Do not update mViews on a background thread call, as the holder is not thread safe.
        if (!enableWorkspaceInflation() || Looper.myLooper() == Looper.getMainLooper()) {
            mViews.put(appWidgetId, view);
        }
        return view;
    }

    /**
     * Attaches an already inflated view to the host. If the view can't be attached, creates
     * and attaches a new view.
     * @return the final attached view
     */
    @NonNull
    public final AppWidgetHostView attachViewToHostAndGetAttachedView(
            @NonNull LauncherAppWidgetHostView view) {

        // Binder can also inflate placeholder widgets in case of backup-restore. Skip
        // attaching such widgets
        boolean isRealWidget = (!(view instanceof PendingAppWidgetHostView pw)
                || pw.isDeferredWidget())
                && view.getAppWidgetInfo() != null;
        if (isRealWidget && mViews.get(view.getAppWidgetId()) != view) {
            view = recycleExistingView(view);
            mViews.put(view.getAppWidgetId(), view);
        }
        return view;
    }

    /**
     * Recycling logic:
     *   1) If the final view should be a pendingView
     *          if the provided view is also a pendingView, return itself
     *          otherwise discard provided view and return a new pending view
     *   2) If the recycled view is a pendingView, discard it and return a new view
     *   3) Use the same for as creating a new view, but used the provided view in the host instead
     *      of creating a new view. This ensures that all the host callbacks are properly attached
     *      as a result of using the same flow.
     */
    protected LauncherAppWidgetHostView recycleExistingView(LauncherAppWidgetHostView view) {
        if ((mFlags.get() & FLAG_LISTENING) == 0) {
            if (view instanceof PendingAppWidgetHostView pv && pv.isDeferredWidget()) {
                return view;
            } else {
                return new PendingAppWidgetHostView(mContext, this, view.getAppWidgetId(),
                        fromProviderInfo(mContext, view.getAppWidgetInfo()));
            }
        }
        LauncherAppWidgetHost host = (LauncherAppWidgetHost) mWidgetHost;
        if (view instanceof ListenableHostView lhv) {
            host.recycleViewForNextCreation(lhv);
        }

        view = createViewInternal(
                view.getAppWidgetId(), fromProviderInfo(mContext, view.getAppWidgetInfo()));
        host.recycleViewForNextCreation(null);
        return view;
    }

    @NonNull
    protected LauncherAppWidgetHostView createViewInternal(
            int appWidgetId, @NonNull LauncherAppWidgetProviderInfo appWidget) {
        if ((mFlags.get() & FLAG_LISTENING) == 0) {
            // Since the launcher hasn't started listening to widget updates, we can't simply call
            // host.createView here because the later will make a binder call to retrieve
            // RemoteViews from system process.
            return new PendingAppWidgetHostView(mContext, this, appWidgetId, appWidget);
        } else {
            if (enableWorkspaceInflation() && Looper.myLooper() != Looper.getMainLooper()) {
                // Widget is being inflated a background thread, just create and
                // return a placeholder view
                ListenableHostView hostView = new ListenableHostView(mContext);
                hostView.setAppWidget(appWidgetId, appWidget);
                return hostView;
            }
            try {
                return (LauncherAppWidgetHostView) mWidgetHost.createView(
                        mContext, appWidgetId, appWidget);
            } catch (Exception e) {
                if (!Utilities.isBinderSizeError(e)) {
                    throw new RuntimeException(e);
                }

                // If the exception was thrown while fetching the remote views, let the view stay.
                // This will ensure that if the widget posts a valid update later, the view
                // will update.
                LauncherAppWidgetHostView view = mViews.get(appWidgetId);
                if (view == null) {
                    view = new ListenableHostView(mContext);
                }
                view.setAppWidget(appWidgetId, appWidget);
                view.switchToErrorView();
                return view;
            }
        }
    }

    /** Clears all the views from the host */
    public void clearViews() {
        ((LauncherAppWidgetHost) mWidgetHost).clearViews();
        mViews.clear();
    }

    /** Clears all the internal widget views */
    public void clearWidgetViews() {
        clearViews();
    }

    /**
     * @return True if the host is listening to the updates, false otherwise
     */
    public boolean isListening() {
        return (mFlags.get() & FLAG_LISTENING) != 0;
    }

    /**
     * Sets or unsets a flag the can change whether the widget host should be in the listening
     * state.
     */
    @VisibleForTesting
    void setShouldListenFlag(int flag, boolean on) {
        if (on) {
            mFlags.updateAndGet(old -> old | flag);
        } else {
            mFlags.updateAndGet(old -> old & ~flag);
        }

        final boolean listening = isListening();
        int currentFlag = mFlags.get();
        if (!listening && shouldListen(currentFlag)) {
            // Postpone starting listening until all flags are on.
            startListening();
        } else if (listening && (currentFlag & FLAG_ACTIVITY_STARTED) == 0) {
            // Postpone stopping listening until the activity is stopped.
            stopListening();
        }
    }

    /**
     * Returns true if the holder should be listening for widget updates based
     * on the provided state flags.
     */
    protected boolean shouldListen(int flags) {
        return (flags & FLAGS_SHOULD_LISTEN) == FLAGS_SHOULD_LISTEN;
    }

    /**
     * Returns the new LauncherWidgetHolder instance
     */
    public static LauncherWidgetHolder newInstance(Context context) {
        return LauncherComponentProvider.get(context).getWidgetHolderFactory().newInstance(context);
    }

    /** A factory that generates new instances of {@code LauncherWidgetHolder} */
    public interface WidgetHolderFactory {

        LauncherWidgetHolder newInstance(@NonNull Context context);
    }

    /** A factory that generates new instances of {@code LauncherWidgetHolder} */
    @AssistedFactory
    public interface WidgetHolderFactoryImpl extends WidgetHolderFactory {

        LauncherWidgetHolder newInstance(@Assisted("UI_CONTEXT") @NonNull Context context);
    }
}
