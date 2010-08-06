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

import com.android.launcher.R;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.TabHost;

import java.util.ArrayList;

/**
 * Implements a tabbed version of AllApps2D.
 */
public class AllAppsTabbed extends TabHost implements AllAppsView {

    private static final String TAG = "Launcher.AllAppsTabbed";

    private static final String TAG_ALL = "ALL";
    private static final String TAG_APPS = "APPS";
    private static final String TAG_GAMES = "GAMES";
    private static final String TAG_DOWNLOADED = "DOWNLOADED";

    private AllApps2D mAllApps2D;
    private Context mContext;

    public AllAppsTabbed(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        try {
            mAllApps2D = (AllApps2D)findViewById(R.id.all_apps_2d);
            if (mAllApps2D == null) throw new Resources.NotFoundException();
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Can't find necessary layout elements for AllAppsTabbed");
        }
        setup();

        // This lets us share the same view between all tabs
        TabContentFactory contentFactory = new TabContentFactory() {
            public View createTabContent(String tag) {
                return mAllApps2D;
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

        setOnTabChangedListener(new OnTabChangeListener() {
            public void onTabChanged(String tabId) {
                String tag = getCurrentTabTag();
                if (tag == TAG_ALL) {
                    mAllApps2D.filterApps(AllApps2D.AppType.ALL);
                } else if (tag == TAG_APPS) {
                    mAllApps2D.filterApps(AllApps2D.AppType.APP);
                } else if (tag == TAG_GAMES) {
                    mAllApps2D.filterApps(AllApps2D.AppType.GAME);
                } else if (tag == TAG_DOWNLOADED) {
                    mAllApps2D.filterApps(AllApps2D.AppType.DOWNLOADED);
                }
            }
        });

        setCurrentTab(0);
    }

    @Override
    public void setLauncher(Launcher launcher) {
        mAllApps2D.setLauncher(launcher);
    }

    @Override
    public void setDragController(DragController dragger) {
        mAllApps2D.setDragController(dragger);
    }

    @Override
    public void zoom(float zoom, boolean animate) {
        // NOTE: animate parameter is ignored for the TabHost itself
        setVisibility((zoom == 0.0f) ? View.GONE : View.VISIBLE);
        mAllApps2D.zoom(zoom, animate);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        float zoom = visibility == View.VISIBLE ? 1.0f : 0.0f;
        mAllApps2D.zoom(zoom, false);
    }

    @Override
    public boolean isVisible() {
        return mAllApps2D.isVisible();
    }

    @Override
    public boolean isAnimating() {
        return (getAnimation() != null);
    }

    @Override
    public void setApps(ArrayList<ApplicationInfo> list) {
        mAllApps2D.setApps(list);
    }

    @Override
    public void addApps(ArrayList<ApplicationInfo> list) {
        mAllApps2D.addApps(list);
    }

    @Override
    public void removeApps(ArrayList<ApplicationInfo> list) {
        mAllApps2D.removeApps(list);
    }

    @Override
    public void updateApps(ArrayList<ApplicationInfo> list) {
        mAllApps2D.updateApps(list);
    }

    @Override
    public void dumpState() {
        mAllApps2D.dumpState();
    }

    @Override
    public void surrender() {
        mAllApps2D.surrender();
    }
}
