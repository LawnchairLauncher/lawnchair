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

    private AllAppsView mAllApps2D;

    public AllAppsTabbed(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        try {
            mAllApps2D = (AllAppsView)findViewById(R.id.all_apps_2d);
            if (mAllApps2D == null) throw new Resources.NotFoundException();
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Can't find necessary layout elements for AllAppsTabbed");
        }
        setup();

        // This lets us share the same view between all tabs
        TabContentFactory contentFactory = new TabContentFactory() {
            public View createTabContent(String tag) {
                return (View)mAllApps2D;
            }
        };

        // TODO: Make these tabs show the appropriate content (they're no-ops for now)
        addTab(newTabSpec("apps").setIndicator("All").setContent(contentFactory));
        addTab(newTabSpec("apps").setIndicator("Apps").setContent(contentFactory));
        addTab(newTabSpec("apps").setIndicator("Games").setContent(contentFactory));
        addTab(newTabSpec("apps").setIndicator("Downloaded").setContent(contentFactory));

        setCurrentTab(0);
        setVisibility(GONE);
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
        bringChildToFront((View)mAllApps2D);
        getParent().bringChildToFront(this);
    }

    @Override
    public boolean isVisible() {
        return mAllApps2D.isVisible();
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
