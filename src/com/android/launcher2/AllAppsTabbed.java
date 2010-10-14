/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.launcher2;

import java.util.ArrayList;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import com.android.launcher.R;

/**
 * Implements a tabbed version of AllApps2D.
 */
public class AllAppsTabbed extends TabHost implements AllAppsView {

    private static final String TAG = "Launcher.AllAppsTabbed";

    private static final String TAG_ALL = "ALL";
    private static final String TAG_APPS = "APPS";
    private static final String TAG_GAMES = "GAMES";
    private static final String TAG_DOWNLOADED = "DOWNLOADED";

    private AllAppsPagedView mAllApps;
    private Context mContext;

    public AllAppsTabbed(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        // setup the tab host
        setup();

        try {
            mAllApps = (AllAppsPagedView) findViewById(R.id.all_apps_paged_view);
            if (mAllApps == null) throw new Resources.NotFoundException();
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Can't find necessary layout elements for AllAppsTabbed");
        }

        // share the same AllApps workspace across all the tabs
        TabContentFactory contentFactory = new TabContentFactory() {
            public View createTabContent(String tag) {
                return mAllApps;
            }
        };

        String label = mContext.getString(R.string.all_apps_tab_all);
        addTab(newTabSpec(TAG_ALL).setIndicator(label).setContent(contentFactory));

        label = mContext.getString(R.string.all_apps_tab_apps);
        addTab(newTabSpec(TAG_APPS).setIndicator(label).setContent(contentFactory));

        label = mContext.getString(R.string.all_apps_tab_games);
        addTab(newTabSpec(TAG_GAMES).setIndicator(label).setContent(contentFactory));

        label = mContext.getString(R.string.all_apps_tab_downloaded);
        addTab(newTabSpec(TAG_DOWNLOADED).setIndicator(label).setContent(contentFactory));

        // TEMP: just styling the tab widget to be a bit nicer until we get the actual
        // new assets
        TabWidget tabWidget = getTabWidget();
        for (int i = 0; i < tabWidget.getChildCount(); ++i) {
            RelativeLayout tab = (RelativeLayout) tabWidget.getChildTabViewAt(i);
            TextView text = (TextView) tab.getChildAt(1);
            text.setTextSize(20.0f);
            text.setPadding(20, 0, 20, 0);
            text.setShadowLayer(1.0f, 0.0f, 1.0f, Color.BLACK);
            tab.setBackgroundDrawable(null);
        }

        setOnTabChangedListener(new OnTabChangeListener() {
            public void onTabChanged(String tabId) {
                // animate the changing of the tab content by fading pages in and out
                final int duration = 150;
                final float alpha = mAllApps.getAlpha();
                ValueAnimator alphaAnim = ObjectAnimator.ofFloat(mAllApps, "alpha", alpha, 0.0f).
                        setDuration(duration);
                alphaAnim.addListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator animation) {
                        String tag = getCurrentTabTag();
                        if (tag == TAG_ALL) {
                            mAllApps.setAppFilter(AllAppsPagedView.ALL_APPS_FLAG);
                        } else if (tag == TAG_APPS) {
                            mAllApps.setAppFilter(ApplicationInfo.APP_FLAG);
                        } else if (tag == TAG_GAMES) {
                            mAllApps.setAppFilter(ApplicationInfo.GAME_FLAG);
                        } else if (tag == TAG_DOWNLOADED) {
                            mAllApps.setAppFilter(ApplicationInfo.DOWNLOADED_FLAG);
                        }

                        final float alpha = mAllApps.getAlpha();
                        ObjectAnimator.ofFloat(mAllApps, "alpha", alpha, 1.0f).
                                setDuration(duration).start();
                    }
                });
                alphaAnim.start();
            }
        });

        // TEMP: Working around a bug in tab host where the current tab does not initially have a
        // highlight on it by selecting something else, then selecting the actual tab we want..
        setCurrentTab(1);
        setCurrentTab(0);

        // It needs to be INVISIBLE so that it will be measured in the layout.
        // Otherwise the animations is messed up when we show it for the first time.
        setVisibility(INVISIBLE);
    }

    @Override
    public void setLauncher(Launcher launcher) {
        mAllApps.setLauncher(launcher);
    }

    @Override
    public void setDragController(DragController dragger) {
        mAllApps.setDragController(dragger);
    }

    @Override
    public void zoom(float zoom, boolean animate) {
        // NOTE: animate parameter is ignored for the TabHost itself
        setVisibility((zoom == 0.0f) ? View.GONE : View.VISIBLE);
        mAllApps.zoom(zoom, animate);
    }

    @Override
    public void setVisibility(int visibility) {
        final boolean isVisible = (visibility == View.VISIBLE); 
        super.setVisibility(visibility);
        float zoom = (isVisible ? 1.0f : 0.0f);
        mAllApps.zoom(zoom, false);
    }

    @Override
    public boolean isVisible() {
        return mAllApps.isVisible();
    }

    @Override
    public boolean isAnimating() {
        return (getAnimation() != null);
    }

    @Override
    public void setApps(ArrayList<ApplicationInfo> list) {
        mAllApps.setApps(list);
    }

    @Override
    public void addApps(ArrayList<ApplicationInfo> list) {
        mAllApps.addApps(list);
    }

    @Override
    public void removeApps(ArrayList<ApplicationInfo> list) {
        mAllApps.removeApps(list);
    }

    @Override
    public void updateApps(ArrayList<ApplicationInfo> list) {
        mAllApps.updateApps(list);
    }

    @Override
    public void dumpState() {
        mAllApps.dumpState();
    }

    @Override
    public void surrender() {
        mAllApps.surrender();
    }
}
