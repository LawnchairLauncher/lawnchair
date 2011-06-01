/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TabHost;
import android.widget.TextView;

import com.android.launcher.R;

public class AppsCustomizeTabHost extends TabHost implements LauncherTransitionable,
        TabHost.OnTabChangeListener  {
    static final String LOG_TAG = "AppsCustomizeTabHost";

    private static final String APPS_TAB_TAG = "APPS";
    private static final String WIDGETS_TAB_TAG = "WIDGETS";
    private static final String WALLPAPERS_TAB_TAG = "WALLPAPERS";

    private final LayoutInflater mLayoutInflater;
    private AppsCustomizePagedView mAppsCustomizePane;

    public AppsCustomizeTabHost(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLayoutInflater = LayoutInflater.from(context);
    }

    void selectAppsTab() {
        setCurrentTabByTag(APPS_TAB_TAG);
    }
    void selectWidgetsTab() {
        setCurrentTabByTag(WIDGETS_TAB_TAG);
    }

    /**
     * Setup the tab host and create all necessary tabs.
     */
    @Override
    protected void onFinishInflate() {
        // Setup the tab host
        setup();

        final ViewGroup tabs = (ViewGroup) findViewById(com.android.internal.R.id.tabs);
        final AppsCustomizePagedView appsCustomizePane = (AppsCustomizePagedView)
                findViewById(R.id.apps_customize_pane_content);
        mAppsCustomizePane = appsCustomizePane;
        if (tabs == null || mAppsCustomizePane == null) throw new Resources.NotFoundException();

        // Configure the tabs content factory to return the same paged view (that we change the
        // content filter on)
        TabContentFactory contentFactory = new TabContentFactory() {
            public View createTabContent(String tag) {
                return appsCustomizePane;
            }
        };

        // Create the tabs
        TextView tabView;
        tabView = (TextView) mLayoutInflater.inflate(R.layout.tab_widget_indicator, tabs, false);
        tabView.setText(mContext.getString(R.string.all_apps_button_label));
        addTab(newTabSpec(APPS_TAB_TAG).setIndicator(tabView).setContent(contentFactory));
        tabView = (TextView) mLayoutInflater.inflate(R.layout.tab_widget_indicator, tabs, false);
        tabView.setText(mContext.getString(R.string.widgets_tab_label));
        addTab(newTabSpec(WIDGETS_TAB_TAG).setIndicator(tabView).setContent(contentFactory));
        tabView = (TextView) mLayoutInflater.inflate(R.layout.tab_widget_indicator, tabs, false);
        tabView.setText(mContext.getString(R.string.wallpapers_tab_label));
        addTab(newTabSpec(WALLPAPERS_TAB_TAG).setIndicator(tabView).setContent(contentFactory));
        setOnTabChangedListener(this);

        // Set the width of the tab bar to match the content (for now)
        tabs.getLayoutParams().width = mAppsCustomizePane.getPageContentWidth();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Intercept all touch events up to the bottom of the AppsCustomizePane so they do not fall
        // through to the workspace and trigger showWorkspace()
        if (event.getY() < mAppsCustomizePane.getBottom()) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void onTabChanged(String tabId) {
        mAppsCustomizePane.setContentType(getContentTypeForTabTag(tabId));
    }

    /**
     * Returns the content type for the specified tab tag.
     */
    public AppsCustomizePagedView.ContentType getContentTypeForTabTag(String tag) {
        if (tag.equals(APPS_TAB_TAG)) {
            return AppsCustomizePagedView.ContentType.Applications;
        } else if (tag.equals(WIDGETS_TAB_TAG)) {
            return AppsCustomizePagedView.ContentType.Widgets;
        } else if (tag.equals(WALLPAPERS_TAB_TAG)) {
            return AppsCustomizePagedView.ContentType.Wallpapers;
        }
        return AppsCustomizePagedView.ContentType.Applications;
    }

    /**
     * Returns the tab tag for a given content type.
     */
    public String getTabTagForContentType(AppsCustomizePagedView.ContentType type) {
        if (type == AppsCustomizePagedView.ContentType.Applications) {
            return APPS_TAB_TAG;
        } else if (type == AppsCustomizePagedView.ContentType.Widgets) {
            return WIDGETS_TAB_TAG;
        } else if (type == AppsCustomizePagedView.ContentType.Wallpapers) {
            return WALLPAPERS_TAB_TAG;
        }
        return APPS_TAB_TAG;
    }

    /**
     * Disable focus on anything under this view in the hierarchy if we are not visible.
     */
    @Override
    public int getDescendantFocusability() {
        if (getVisibility() != View.VISIBLE) {
            return ViewGroup.FOCUS_BLOCK_DESCENDANTS;
        }
        return super.getDescendantFocusability();
    }

    /* LauncherTransitionable overrides */
    @Override
    public void onLauncherTransitionStart(android.animation.Animator animation) {
        // TODO-APPS_CUSTOMIZE: see AllAppsTabbed.onLauncherTransitionStart();
    }
    @Override
    public void onLauncherTransitionEnd(android.animation.Animator animation) {
        // TODO-APPS_CUSTOMIZE: see AllAppsTabbed.onLauncherTransitionEnd();
    }
}
