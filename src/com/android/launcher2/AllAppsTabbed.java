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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Implements a tabbed version of AllApps2D.
 */
public class AllAppsTabbed extends TabHost implements AllAppsView, LauncherTransitionable {

    private static final String TAG = "Launcher.AllAppsTabbed";

    private static final String TAG_ALL = "ALL";
    private static final String TAG_DOWNLOADED = "DOWNLOADED";

    private AllAppsPagedView mAllApps;
    private AllAppsBackground mBackground;
    private Launcher mLauncher;
    private Context mContext;
    private final LayoutInflater mInflater;
    private boolean mFirstLayout = true;

    public AllAppsTabbed(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mInflater = LayoutInflater.from(context);
    }

    @Override
    protected void onFinishInflate() {
        // setup the tab host
        setup();

        try {
            mAllApps = (AllAppsPagedView) findViewById(R.id.all_apps_paged_view);
            if (mAllApps == null) throw new Resources.NotFoundException();
            mBackground = (AllAppsBackground) findViewById(R.id.all_apps_background);
            if (mBackground == null) throw new Resources.NotFoundException();
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Can't find necessary layout elements for AllAppsTabbed");
        }

        // share the same AllApps workspace across all the tabs
        TabContentFactory contentFactory = new TabContentFactory() {
            public View createTabContent(String tag) {
                return mAllApps;
            }
        };

        // Create the tabs and wire them up properly
        TextView tabView;
        TabWidget tabWidget = (TabWidget) findViewById(com.android.internal.R.id.tabs);
        tabView = (TextView) mInflater.inflate(R.layout.tab_widget_indicator, tabWidget, false);
        tabView.setText(mContext.getString(R.string.all_apps_tab_all));
        addTab(newTabSpec(TAG_ALL).setIndicator(tabView).setContent(contentFactory));

        tabView = (TextView) mInflater.inflate(R.layout.tab_widget_indicator, tabWidget, false);
        tabView.setText(mContext.getString(R.string.all_apps_tab_downloaded));
        addTab(newTabSpec(TAG_DOWNLOADED).setIndicator(tabView).setContent(contentFactory));

        setOnTabChangedListener(new OnTabChangeListener() {
            public void onTabChanged(String tabId) {
                // animate the changing of the tab content by fading pages in and out
                final Resources res = getResources();
                final int duration = res.getInteger(R.integer.config_tabTransitionTime);
                final float alpha = mAllApps.getAlpha();
                ValueAnimator alphaAnim = ObjectAnimator.ofFloat(mAllApps, "alpha", alpha, 0.0f).
                        setDuration(duration);
                alphaAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        String tag = getCurrentTabTag();
                        if (tag == TAG_ALL) {
                            mAllApps.setAppFilter(AllAppsPagedView.ALL_APPS_FLAG);
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


        // It needs to be INVISIBLE so that it will be measured in the layout.
        // Otherwise the animations is messed up when we show it for the first time.
        setVisibility(INVISIBLE);
    }

    @Override
    public void setLauncher(Launcher launcher) {
        mAllApps.setLauncher(launcher);
        mLauncher = launcher;
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
        if (visibility == View.GONE && mFirstLayout) {
            // It needs to be INVISIBLE so that it will be measured in the layout.
            // Otherwise the animations is messed up when we show it for the first time.
            visibility = View.INVISIBLE;
        }
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
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (mFirstLayout) {
            mFirstLayout = false;
            // Set the width of the tab bar properly
            int pageWidth = mAllApps.getPageContentWidth();
            TabWidget tabWidget = (TabWidget) findViewById(com.android.internal.R.id.tabs);
            View allAppsTabBar = (View) findViewById(R.id.all_apps_tab_bar);
            if (allAppsTabBar == null) throw new Resources.NotFoundException();
            int tabWidgetPadding = 0;
            final int childCount = tabWidget.getChildCount();
            if (childCount > 0) {
                tabWidgetPadding += tabWidget.getChildAt(0).getPaddingLeft() * 2;
            }
            allAppsTabBar.getLayoutParams().width = pageWidth + tabWidgetPadding;
        }
        super.onLayout(changed, l, t, r, b);
    }

    @Override
    public boolean isAnimating() {
        return (getAnimation() != null);
    }

    @Override
    public void onLauncherTransitionStart(Animator animation) {
        if (animation != null) {
            // Turn on hardware layers for performance
            setLayerType(LAYER_TYPE_HARDWARE, null);
            // Re-enable the rendering of the dimmed background in All Apps for performance reasons
            // if we're fading it in
            if (mLauncher.getWorkspace().getBackgroundAlpha() == 0f) {
                mLauncher.getWorkspace().disableBackground();
                mBackground.setVisibility(VISIBLE);
            }
            // just a sanity check that we don't build a layer before a call to onLayout
            if (!mFirstLayout) {
                // force building the layer at the beginning of the animation, so you don't get a
                // blip early in the animation
                buildLayer();
            }
        }
    }

    @Override
    public void onLauncherTransitionEnd(Animator animation) {
        if (animation != null) {
            setLayerType(LAYER_TYPE_NONE, null);
            // To improve the performance of the first time All Apps is run, we initially keep
            // hardware layers in AllAppsPagedView disabled since AllAppsTabbed itself is drawn in a
            // hardware layer, and creating additional hardware layers slows down the animation. We
            // create them here, after the animation is over.
        }
        // Move the rendering of the dimmed background to workspace after the all apps animation
        // is done, so that the background is not rendered *above* the mini workspace screens
        if (mBackground.getVisibility() != GONE) {
            mLauncher.getWorkspace().enableBackground();
            mBackground.setVisibility(GONE);
        }
        mAllApps.allowHardwareLayerCreation();
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

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getY() > mAllApps.getBottom()) {
            return false;
        }
        return true;
    }
}
