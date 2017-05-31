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

package com.android.launcher3;

import android.app.Activity;
import android.app.Fragment;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.launcher3.compat.AppWidgetManagerCompat;

/**
 * A frame layout which contains a QSB. This internally uses fragment to bind the view, which
 * allows it to contain the logic for {@link Fragment#startActivityForResult(Intent, int)}.
 */
public class QsbContainerView extends FrameLayout {

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

    /**
     * A fragment to display the QSB.
     */
    public static class QsbFragment extends Fragment implements View.OnClickListener {

        private static final int REQUEST_BIND_QSB = 1;
        private static final String QSB_WIDGET_ID = "qsb_widget_id";

        private static int sSavedWidgetId = -1;

        private AppWidgetProviderInfo mWidgetInfo;
        private LauncherAppWidgetHostView mQsb;

        private BroadcastReceiver mRebindReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                rebindFragment();
            }
        };

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            IntentFilter filter = new IntentFilter(Launcher.ACTION_APPWIDGET_HOST_RESET);
            filter.addAction(SearchManager.INTENT_GLOBAL_SEARCH_ACTIVITY_CHANGED);
            getActivity().registerReceiver(mRebindReceiver, filter);
        }

        private FrameLayout mWrapper;

        @Override
        public View onCreateView(
                LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

            if (savedInstanceState != null) {
                sSavedWidgetId = savedInstanceState.getInt(QSB_WIDGET_ID, -1);
            }
            mWrapper = new FrameLayout(getActivity());
            mWrapper.addView(createQsb(inflater, mWrapper));
            return mWrapper;
        }

        private View createQsb(LayoutInflater inflater, ViewGroup container) {
            Launcher launcher = Launcher.getLauncher(getActivity());
            mWidgetInfo = getSearchWidgetProvider(launcher);
            if (mWidgetInfo == null) {
                // There is no search provider, just show the default widget.
                return getDefaultView(inflater, container, false);
            }

            SharedPreferences prefs = Utilities.getPrefs(launcher);
            AppWidgetManagerCompat widgetManager = AppWidgetManagerCompat.getInstance(launcher);
            LauncherAppWidgetHost widgetHost = launcher.getAppWidgetHost();
            InvariantDeviceProfile idp = LauncherAppState.getInstance().getInvariantDeviceProfile();

            Bundle opts = new Bundle();
            Rect size = AppWidgetResizeFrame.getWidgetSizeRanges(launcher, idp.numColumns, 1, null);
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, size.left);
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, size.top);
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, size.right);
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, size.bottom);

            int widgetId = prefs.getInt(QSB_WIDGET_ID, -1);
            AppWidgetProviderInfo widgetInfo = widgetManager.getAppWidgetInfo(widgetId);
            boolean isWidgetBound = (widgetInfo != null) &&
                    widgetInfo.provider.equals(mWidgetInfo.provider);

            if (!isWidgetBound) {
                // widgetId is already bound and its not the correct provider.
                // Delete the widget id.
                if (widgetId > -1) {
                    widgetHost.deleteAppWidgetId(widgetId);
                    widgetId = -1;
                }

                widgetId = widgetHost.allocateAppWidgetId();
                isWidgetBound = widgetManager.bindAppWidgetIdIfAllowed(widgetId, mWidgetInfo, opts);
                if (!isWidgetBound) {
                    widgetHost.deleteAppWidgetId(widgetId);
                    widgetId = -1;
                }
            }

            if (isWidgetBound) {
                mQsb = (LauncherAppWidgetHostView)
                        widgetHost.createView(launcher, widgetId, mWidgetInfo);
                mQsb.setId(R.id.qsb_widget);
                mQsb.mErrorViewId = R.layout.qsb_default_view;

                if (!Utilities.containsAll(AppWidgetManager.getInstance(launcher)
                        .getAppWidgetOptions(widgetId), opts)) {
                    mQsb.updateAppWidgetOptions(opts);
                }
                mQsb.setPadding(mQsb.getPaddingLeft(), 0, mQsb.getPaddingRight(), 0);
                mQsb.setLayoutParams(new LauncherAppWidgetHostView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.TOP));
                return mQsb;
            }

            // Return a default widget with setup icon.
            return getDefaultView(inflater, container, true);
        }

        @Override
        public void onClick(View view) {
            if (view.getId() == R.id.btn_qsb_search) {
                getActivity().startSearch("", false, null, true);
            } else if (view.getId() == R.id.btn_qsb_setup) {
                // Allocate a new widget id for QSB
                sSavedWidgetId = Launcher.getLauncher(getActivity())
                        .getAppWidgetHost().allocateAppWidgetId();
                // Start intent for bind the widget
                Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, sSavedWidgetId);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, mWidgetInfo.provider);
                startActivityForResult(intent, REQUEST_BIND_QSB);
            }
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putInt(QSB_WIDGET_ID, sSavedWidgetId);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode == REQUEST_BIND_QSB) {
                if (resultCode == Activity.RESULT_OK) {
                    int widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                            sSavedWidgetId);
                    Utilities.getPrefs(getActivity()).edit().putInt(QSB_WIDGET_ID, widgetId).apply();
                    sSavedWidgetId = -1;
                    rebindFragment();
                } else if (sSavedWidgetId != -1) {
                    Launcher.getLauncher(getActivity()).getAppWidgetHost()
                            .deleteAppWidgetId(sSavedWidgetId);
                    sSavedWidgetId = -1;
                }
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            if (mQsb != null && mQsb.isReinflateRequired()) {
                rebindFragment();
            }
        }

        @Override
        public void onDestroy() {
            getActivity().unregisterReceiver(mRebindReceiver);
            super.onDestroy();
        }

        private void rebindFragment() {
            if (mWrapper != null && getActivity() != null) {
                mWrapper.removeAllViews();
                mWrapper.addView(createQsb(getActivity().getLayoutInflater(), mWrapper));
            }
        }

        private View getDefaultView(LayoutInflater inflater, ViewGroup parent, boolean showSetup) {
            View v = inflater.inflate(R.layout.qsb_default_view, parent, false);
            if (showSetup) {
                View setupButton = v.findViewById(R.id.btn_qsb_setup);
                setupButton.setVisibility(View.VISIBLE);
                setupButton.setOnClickListener(this);
            }
            v.findViewById(R.id.btn_qsb_search).setOnClickListener(this);
            return v;
        }
    }

    /**
     * Returns a widget with category {@link AppWidgetProviderInfo#WIDGET_CATEGORY_SEARCHBOX}
     * provided by the same package which is set to be global search activity.
     * If widgetCategory is not supported, or no such widget is found, returns the first widget
     * provided by the package.
     */
    public static AppWidgetProviderInfo getSearchWidgetProvider(Context context) {
        SearchManager searchManager =
                (SearchManager) context.getSystemService(Context.SEARCH_SERVICE);
        ComponentName searchComponent = searchManager.getGlobalSearchActivity();
        if (searchComponent == null) return null;
        String providerPkg = searchComponent.getPackageName();

        AppWidgetProviderInfo defaultWidgetForSearchPackage = null;

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        for (AppWidgetProviderInfo info : appWidgetManager.getInstalledProviders()) {
            if (info.provider.getPackageName().equals(providerPkg) && info.configure == null) {
                if ((info.widgetCategory & AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX) != 0) {
                    return info;
                } else if (defaultWidgetForSearchPackage == null) {
                    defaultWidgetForSearchPackage = info;
                }
            }
        }
        return defaultWidgetForSearchPackage;
    }
}
