/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.qsb;

import static android.appwidget.AppWidgetManager.ACTION_APPWIDGET_BIND;
import static android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID;
import static android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_PROVIDER;

import static com.android.launcher3.config.FeatureFlags.shouldShowFirstPageWidget;

import android.app.Activity;
import android.app.Fragment;
import android.app.SearchManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.graphics.FragmentWithPreview;
import com.android.launcher3.widget.util.WidgetSizes;

/**
 * A frame layout which contains a QSB. This internally uses fragment to bind the view, which
 * allows it to contain the logic for {@link Fragment#startActivityForResult(Intent, int)}.
 *
 * Note: WidgetManagerHelper can be disabled using FeatureFlags. In QSB, we should use
 * AppWidgetManager directly, so that it keeps working in that case.
 */
public class QsbContainerView extends FrameLayout {

    public static final String SEARCH_PROVIDER_SETTINGS_KEY = "SEARCH_PROVIDER_PACKAGE_NAME";

    /**
     * Returns the package name for user configured search provider or from searchManager
     * @param context
     * @return String
     */
    @WorkerThread
    @Nullable
    public static String getSearchWidgetPackageName(@NonNull Context context) {
        String providerPkg = Settings.Global.getString(context.getContentResolver(),
                SEARCH_PROVIDER_SETTINGS_KEY);
        if (providerPkg == null) {
            SearchManager searchManager = context.getSystemService(SearchManager.class);
            ComponentName componentName = searchManager.getGlobalSearchActivity();
            if (componentName != null) {
                providerPkg = searchManager.getGlobalSearchActivity().getPackageName();
            }
        }
        return providerPkg;
    }

    /**
     * returns it's AppWidgetProviderInfo using package name from getSearchWidgetPackageName
     * @param context
     * @return AppWidgetProviderInfo
     */
    @WorkerThread
    @Nullable
    public static AppWidgetProviderInfo getSearchWidgetProviderInfo(@NonNull Context context) {
        String providerPkg = getSearchWidgetPackageName(context);
        if (providerPkg == null) {
            return null;
        }

        AppWidgetProviderInfo defaultWidgetForSearchPackage = null;
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        for (AppWidgetProviderInfo info :
                appWidgetManager.getInstalledProvidersForPackage(providerPkg, null)) {
            if (info.provider.getPackageName().equals(providerPkg) && info.configure == null) {
                if ((info.widgetCategory
                        & AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX) != 0) {
                    return info;
                } else if (defaultWidgetForSearchPackage == null) {
                    defaultWidgetForSearchPackage = info;
                }
            }
        }
        return defaultWidgetForSearchPackage;
    }

    /**
     * returns componentName for searchWidget if package name is known.
     */
    @WorkerThread
    @Nullable
    public static ComponentName getSearchComponentName(@NonNull  Context context) {
        AppWidgetProviderInfo providerInfo =
                QsbContainerView.getSearchWidgetProviderInfo(context);
        if (providerInfo != null) {
            return providerInfo.provider;
        } else {
            String pkgName = QsbContainerView.getSearchWidgetPackageName(context);
            if (pkgName != null) {
                //we don't know the class name yet. we'll put the package name as placeholder
                return new ComponentName(pkgName, pkgName);
            }
            return null;
        }
    }

    public QsbContainerView(Context context) {
        super(context);
    }

    public QsbContainerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public QsbContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(0, 0, 0, 0);
    }

    protected void setPaddingUnchecked(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
    }

    /**
     * A fragment to display the QSB.
     */
    public static class QsbFragment extends FragmentWithPreview {

        public static final int QSB_WIDGET_HOST_ID = 1026;
        private static final int REQUEST_BIND_QSB = 1;

        protected String mKeyWidgetId = "qsb_widget_id";
        private QsbWidgetHost mQsbWidgetHost;
        protected AppWidgetProviderInfo mWidgetInfo;
        private QsbWidgetHostView mQsb;

        // We need to store the orientation here, due to a bug (b/64916689) that results in widgets
        // being inflated in the wrong orientation.
        private int mOrientation;

        @Override
        public void onInit(Bundle savedInstanceState) {
            mQsbWidgetHost = createHost();
            mOrientation = getContext().getResources().getConfiguration().orientation;
        }

        protected QsbWidgetHost createHost() {
            return new QsbWidgetHost(getContext(), QSB_WIDGET_HOST_ID,
                    (c) -> new QsbWidgetHostView(c), this::rebindFragment);
        }

        private FrameLayout mWrapper;

        @Override
        public View onCreateView(
                LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

            mWrapper = new FrameLayout(getContext());
            // Only add the view when enabled
            if (isQsbEnabled()) {
                mQsbWidgetHost.startListening();
                mWrapper.addView(createQsb(mWrapper));
            }
            return mWrapper;
        }

        private View createQsb(ViewGroup container) {
            mWidgetInfo = getSearchWidgetProvider();
            if (mWidgetInfo == null) {
                // There is no search provider, just show the default widget.
                return getDefaultView(container, false /* show setup icon */);
            }
            Bundle opts = createBindOptions();
            Context context = getContext();
            AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);

            int widgetId = LauncherPrefs.getPrefs(context).getInt(mKeyWidgetId, -1);
            AppWidgetProviderInfo widgetInfo = widgetManager.getAppWidgetInfo(widgetId);
            boolean isWidgetBound = (widgetInfo != null) &&
                    widgetInfo.provider.equals(mWidgetInfo.provider);

            int oldWidgetId = widgetId;
            if (!isWidgetBound && !isInPreviewMode()) {
                if (widgetId > -1) {
                    // widgetId is already bound and its not the correct provider. reset host.
                    mQsbWidgetHost.deleteHost();
                }

                widgetId = mQsbWidgetHost.allocateAppWidgetId();
                isWidgetBound = widgetManager.bindAppWidgetIdIfAllowed(
                        widgetId, mWidgetInfo.getProfile(), mWidgetInfo.provider, opts);
                if (!isWidgetBound) {
                    mQsbWidgetHost.deleteAppWidgetId(widgetId);
                    widgetId = -1;
                }

                if (oldWidgetId != widgetId) {
                    saveWidgetId(widgetId);
                }
            }

            if (isWidgetBound) {
                mQsb = (QsbWidgetHostView) mQsbWidgetHost.createView(context, widgetId,
                        mWidgetInfo);
                mQsb.setId(R.id.qsb_widget);

                if (!isInPreviewMode()) {
                    if (!containsAll(AppWidgetManager.getInstance(context)
                            .getAppWidgetOptions(widgetId), opts)) {
                        mQsb.updateAppWidgetOptions(opts);
                    }
                }
                return mQsb;
            }

            // Return a default widget with setup icon.
            return getDefaultView(container, true /* show setup icon */);
        }

        private void saveWidgetId(int widgetId) {
            LauncherPrefs.getPrefs(getContext()).edit().putInt(mKeyWidgetId, widgetId).apply();
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode == REQUEST_BIND_QSB) {
                if (resultCode == Activity.RESULT_OK) {
                    saveWidgetId(data.getIntExtra(EXTRA_APPWIDGET_ID, -1));
                    rebindFragment();
                } else {
                    mQsbWidgetHost.deleteHost();
                }
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            if (mQsb != null && mQsb.isReinflateRequired(mOrientation)) {
                rebindFragment();
            }
        }

        @Override
        public void onDestroy() {
            mQsbWidgetHost.stopListening();
            super.onDestroy();
        }

        private void rebindFragment() {
            // Exit if the embedded qsb is disabled
            if (!isQsbEnabled()) {
                return;
            }

            if (mWrapper != null && getContext() != null) {
                mWrapper.removeAllViews();
                mWrapper.addView(createQsb(mWrapper));
            }
        }

        public boolean isQsbEnabled() {
            return FeatureFlags.QSB_ON_FIRST_SCREEN && !shouldShowFirstPageWidget();
        }

        protected Bundle createBindOptions() {
            InvariantDeviceProfile idp = LauncherAppState.getIDP(getContext());
            return WidgetSizes.getWidgetSizeOptions(getContext(), mWidgetInfo.provider,
                    idp.numColumns, 1);
        }

        protected View getDefaultView(ViewGroup container, boolean showSetupIcon) {
            // Return a default widget with setup icon.
            View v = QsbWidgetHostView.getDefaultView(container);
            if (showSetupIcon) {
                View setupButton = v.findViewById(R.id.btn_qsb_setup);
                setupButton.setVisibility(View.VISIBLE);
                setupButton.setOnClickListener((v2) -> startActivityForResult(
                        new Intent(ACTION_APPWIDGET_BIND)
                                .putExtra(EXTRA_APPWIDGET_ID, mQsbWidgetHost.allocateAppWidgetId())
                                .putExtra(EXTRA_APPWIDGET_PROVIDER, mWidgetInfo.provider),
                        REQUEST_BIND_QSB));
            }
            return v;
        }


        /**
         * Returns a widget with category {@link AppWidgetProviderInfo#WIDGET_CATEGORY_SEARCHBOX}
         * provided by the package from getSearchProviderPackageName
         * If widgetCategory is not supported, or no such widget is found, returns the first widget
         * provided by the package.
         */
        @WorkerThread
        protected AppWidgetProviderInfo getSearchWidgetProvider() {
            return getSearchWidgetProviderInfo(getContext());
        }
    }

    public static class QsbWidgetHost extends AppWidgetHost {

        private final WidgetViewFactory mViewFactory;
        private final WidgetProvidersUpdateCallback mWidgetsUpdateCallback;

        public QsbWidgetHost(Context context, int hostId, WidgetViewFactory viewFactory,
                WidgetProvidersUpdateCallback widgetProvidersUpdateCallback) {
            super(context, hostId);
            mViewFactory = viewFactory;
            mWidgetsUpdateCallback = widgetProvidersUpdateCallback;
        }

        public QsbWidgetHost(Context context, int hostId, WidgetViewFactory viewFactory) {
            this(context, hostId, viewFactory, null);
        }

        @Override
        protected AppWidgetHostView onCreateView(
                Context context, int appWidgetId, AppWidgetProviderInfo appWidget) {
            return mViewFactory.newView(context);
        }

        @Override
        protected void onProvidersChanged() {
            super.onProvidersChanged();
            if (mWidgetsUpdateCallback != null) {
                mWidgetsUpdateCallback.onProvidersUpdated();
            }
        }
    }

    public interface WidgetViewFactory {

        QsbWidgetHostView newView(Context context);
    }

    /**
     * Callback interface for packages list update.
     */
    @FunctionalInterface
    public interface WidgetProvidersUpdateCallback {
        /**
         * Gets called when widget providers list changes
         */
        void onProvidersUpdated();
    }

    /**
     * Returns true if {@param original} contains all entries defined in {@param updates} and
     * have the same value.
     * The comparison uses {@link Object#equals(Object)} to compare the values.
     */
    private static boolean containsAll(Bundle original, Bundle updates) {
        for (String key : updates.keySet()) {
            Object value1 = updates.get(key);
            Object value2 = original.get(key);
            if (value1 == null) {
                if (value2 != null) {
                    return false;
                }
            } else if (!value1.equals(value2)) {
                return false;
            }
        }
        return true;
    }

}
