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

package com.android.launcher3.widget;

import static android.app.Activity.RESULT_CANCELED;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.SparseArray;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.WidgetsModel;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.widget.custom.CustomWidgetManager;

import java.util.ArrayList;
import java.util.function.IntConsumer;


/**
 * Specific {@link AppWidgetHost} that creates our {@link LauncherAppWidgetHostView}
 * which correctly captures all long-press events. This ensures that users can
 * always pick up and move widgets.
 */
public class LauncherAppWidgetHost extends AppWidgetHost {

    private static final int FLAG_LISTENING = 1;
    private static final int FLAG_STATE_IS_NORMAL = 1 << 1;
    private static final int FLAG_ACTIVITY_STARTED = 1 << 2;
    private static final int FLAG_ACTIVITY_RESUMED = 1 << 3;
    private static final int FLAGS_SHOULD_LISTEN =
            FLAG_STATE_IS_NORMAL | FLAG_ACTIVITY_STARTED | FLAG_ACTIVITY_RESUMED;
    // TODO(b/191735836): Replace with ActivityOptions.KEY_SPLASH_SCREEN_STYLE when un-hidden
    private static final String KEY_SPLASH_SCREEN_STYLE = "android.activity.splashScreenStyle";
    // TODO(b/191735836): Replace with SplashScreen.SPLASH_SCREEN_STYLE_EMPTY when un-hidden
    private static final int SPLASH_SCREEN_STYLE_EMPTY = 0;

    public static final int APPWIDGET_HOST_ID = 1024;

    private final ArrayList<ProviderChangedListener> mProviderChangeListeners = new ArrayList<>();
    private final SparseArray<LauncherAppWidgetHostView> mViews = new SparseArray<>();
    private final SparseArray<PendingAppWidgetHostView> mPendingViews = new SparseArray<>();
    private final SparseArray<LauncherAppWidgetHostView> mDeferredViews = new SparseArray<>();
    private final SparseArray<RemoteViews> mCachedRemoteViews = new SparseArray<>();

    private final Context mContext;
    private int mFlags = FLAG_STATE_IS_NORMAL;

    private IntConsumer mAppWidgetRemovedCallback = null;

    public LauncherAppWidgetHost(Context context) {
        this(context, null);
    }

    public LauncherAppWidgetHost(Context context,
            IntConsumer appWidgetRemovedCallback) {
        super(context, APPWIDGET_HOST_ID);
        mContext = context;
        mAppWidgetRemovedCallback = appWidgetRemovedCallback;
    }

    @Override
    protected LauncherAppWidgetHostView onCreateView(Context context, int appWidgetId,
            AppWidgetProviderInfo appWidget) {
        final LauncherAppWidgetHostView view;
        if (mPendingViews.get(appWidgetId) != null) {
            view = mPendingViews.get(appWidgetId);
            mPendingViews.remove(appWidgetId);
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

    @Override
    public void startListening() {
        if (WidgetsModel.GO_DISABLE_WIDGETS) {
            return;
        }
        mFlags |= FLAG_LISTENING;
        try {
            super.startListening();
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
                    super.createView(view.mLauncher, appWidgetId, view.getAppWidgetInfo());
                    // At this point #onCreateView should have been called, which in turn returned
                    // the deferred view. There's no reason to keep the reference anymore, so we
                    // removed it here.
                    mDeferredViews.remove(appWidgetId);
                }
            }
        }
    }

    @Override
    public void stopListening() {
        if (WidgetsModel.GO_DISABLE_WIDGETS) {
            return;
        }
        mFlags &= ~FLAG_LISTENING;
        super.stopListening();
    }

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
     * Registers an "entering/leaving Normal state" event.
     */
    public void setStateIsNormal(boolean isNormal) {
        setShouldListenFlag(FLAG_STATE_IS_NORMAL, isNormal);
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

    @Override
    public int allocateAppWidgetId() {
        if (WidgetsModel.GO_DISABLE_WIDGETS) {
            return AppWidgetManager.INVALID_APPWIDGET_ID;
        }

        return super.allocateAppWidgetId();
    }

    public void addProviderChangeListener(ProviderChangedListener callback) {
        mProviderChangeListeners.add(callback);
    }

    public void removeProviderChangeListener(ProviderChangedListener callback) {
        mProviderChangeListeners.remove(callback);
    }

    protected void onProvidersChanged() {
        if (!mProviderChangeListeners.isEmpty()) {
            for (ProviderChangedListener callback : new ArrayList<>(mProviderChangeListeners)) {
                callback.notifyWidgetProvidersChanged();
            }
        }
    }

    public void addPendingView(int appWidgetId, PendingAppWidgetHostView view) {
        mPendingViews.put(appWidgetId, view);
    }

    public AppWidgetHostView createView(Context context, int appWidgetId,
            LauncherAppWidgetProviderInfo appWidget) {
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
                return super.createView(context, appWidgetId, appWidget);
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
     * Called when the AppWidget provider for a AppWidget has been upgraded to a new apk.
     */
    @Override
    protected void onProviderChanged(int appWidgetId, AppWidgetProviderInfo appWidget) {
        LauncherAppWidgetProviderInfo info = LauncherAppWidgetProviderInfo.fromProviderInfo(
                mContext, appWidget);
        super.onProviderChanged(appWidgetId, info);
        // The super method updates the dimensions of the providerInfo. Update the
        // launcher spans accordingly.
        info.initSpans(mContext, LauncherAppState.getIDP(mContext));
    }

    /**
     * Called on an appWidget is removed for a widgetId
     *
     * @param appWidgetId TODO: make this override when SDK is updated
     */
    public void onAppWidgetRemoved(int appWidgetId) {
        if (mAppWidgetRemovedCallback == null) {
            return;
        }
        mAppWidgetRemovedCallback.accept(appWidgetId);
    }

    @Override
    public void deleteAppWidgetId(int appWidgetId) {
        super.deleteAppWidgetId(appWidgetId);
        mViews.remove(appWidgetId);
    }

    @Override
    public void clearViews() {
        super.clearViews();
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

    public void startBindFlow(BaseActivity activity,
            int appWidgetId, AppWidgetProviderInfo info, int requestCode) {

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
     * Launches an app widget's configuration activity.
     * @param activity The activity from which to launch the configuration activity
     * @param widgetId The id of the bound app widget to be configured
     * @param requestCode An optional request code to be returned with the result
     */
    public void startConfigActivity(BaseDraggingActivity activity, int widgetId, int requestCode) {
        if (WidgetsModel.GO_DISABLE_WIDGETS) {
            sendActionCancelled(activity, requestCode);
            return;
        }

        try {
            TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "start: startConfigActivity");
            startAppWidgetConfigureActivityForResult(activity, widgetId, 0, requestCode,
                    getConfigurationActivityOptions(activity, widgetId));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            sendActionCancelled(activity, requestCode);
        }
    }

    /**
     * Returns an {@link android.app.ActivityOptions} bundle from the {code activity} for launching
     * the configuration of the {@code widgetId} app widget, or null of options cannot be produced.
     */
    @Nullable
    private Bundle getConfigurationActivityOptions(BaseDraggingActivity activity, int widgetId) {
        LauncherAppWidgetHostView view = mViews.get(widgetId);
        if (view == null) return null;
        Object tag = view.getTag();
        if (!(tag instanceof ItemInfo)) return null;
        Bundle bundle = activity.getActivityLaunchOptions(view, (ItemInfo) tag).toBundle();
        bundle.putInt(KEY_SPLASH_SCREEN_STYLE, SPLASH_SCREEN_STYLE_EMPTY);
        return bundle;
    }

    private void sendActionCancelled(final BaseActivity activity, final int requestCode) {
        new Handler().post(() -> activity.onActivityResult(requestCode, RESULT_CANCELED, null));
    }

    /**
     * Listener for getting notifications on provider changes.
     */
    public interface ProviderChangedListener {

        void notifyWidgetProvidersChanged();
    }
}
