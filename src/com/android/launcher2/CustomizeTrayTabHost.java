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

import com.android.launcher.R;
import com.android.launcher2.CustomizePagedView.CustomizationType;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

public class CustomizeTrayTabHost extends TabHost implements LauncherTransitionable  {
    // tags for the customization tabs
    private static final String WIDGETS_TAG = "widgets";
    private static final String APPLICATIONS_TAG = "applications";
    private static final String SHORTCUTS_TAG = "shortcuts";
    private static final String WALLPAPERS_TAG = "wallpapers";

    private boolean mFirstLayout = true;

    private final LayoutInflater mInflater;
    private Context mContext;

    public CustomizeTrayTabHost(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mInflater = LayoutInflater.from(context);
    }

    @Override
    protected void onFinishInflate() {
        setup();

        final CustomizePagedView customizePagedView =
            (CustomizePagedView) findViewById(R.id.customization_drawer_tab_contents);

        // Configure tabs
        TabContentFactory contentFactory = new TabContentFactory() {
            public View createTabContent(String tag) {
                return customizePagedView;
            }
        };

        TextView tabView;
        TabWidget tabWidget = (TabWidget) findViewById(com.android.internal.R.id.tabs);

        tabView = (TextView) mInflater.inflate(R.layout.tab_widget_indicator, tabWidget, false);
        tabView.setText(mContext.getString(R.string.widgets_tab_label));
        addTab(newTabSpec(WIDGETS_TAG).setIndicator(tabView).setContent(contentFactory));
        tabView = (TextView) mInflater.inflate(R.layout.tab_widget_indicator, tabWidget, false);
        tabView.setText(mContext.getString(R.string.applications_tab_label));
        addTab(newTabSpec(APPLICATIONS_TAG)
                .setIndicator(tabView).setContent(contentFactory));
        tabView = (TextView) mInflater.inflate(R.layout.tab_widget_indicator, tabWidget, false);
        tabView.setText(mContext.getString(R.string.wallpapers_tab_label));
        addTab(newTabSpec(WALLPAPERS_TAG)
                .setIndicator(tabView).setContent(contentFactory));
        tabView = (TextView) mInflater.inflate(R.layout.tab_widget_indicator, tabWidget, false);
        tabView.setText(mContext.getString(R.string.shortcuts_tab_label));
        addTab(newTabSpec(SHORTCUTS_TAG)
                .setIndicator(tabView).setContent(contentFactory));
        setOnTabChangedListener(new OnTabChangeListener() {
            public void onTabChanged(String tabId) {
                final CustomizePagedView.CustomizationType newType =
                    getCustomizeFilterForTabTag(tabId);
                if (newType != customizePagedView.getCustomizationFilter()) {
                    // animate the changing of the tab content by fading pages in and out
                    final Resources res = getResources();
                    final int duration = res.getInteger(R.integer.config_tabTransitionTime);
                    final float alpha = customizePagedView.getAlpha();
                    ValueAnimator alphaAnim = ObjectAnimator.ofFloat(customizePagedView,
                            "alpha", alpha, 0.0f);
                    alphaAnim.setDuration(duration);
                    alphaAnim.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            customizePagedView.setCustomizationFilter(newType);

                            final float alpha = customizePagedView.getAlpha();
                            ValueAnimator alphaAnim = ObjectAnimator.ofFloat(
                                    customizePagedView, "alpha", alpha, 1.0f);
                            alphaAnim.setDuration(duration);
                            alphaAnim.start();
                        }
                    });
                    alphaAnim.start();
                }
            }
        });

        // Set the width of the tab bar properly
        int pageWidth = customizePagedView.getPageContentWidth();
        TabWidget customizeTabBar = (TabWidget) findViewById(com.android.internal.R.id.tabs);
        if (customizeTabBar == null) throw new Resources.NotFoundException();
        int tabWidgetPadding = 0;
        final int childCount = tabWidget.getChildCount();
        if (childCount > 0) {
            tabWidgetPadding += tabWidget.getChildAt(0).getPaddingLeft() * 2;
        }
        customizeTabBar.getLayoutParams().width = pageWidth + tabWidgetPadding;
    }

    @Override
    public void onLauncherTransitionStart(Animator animation) {
        if (animation != null) {
            setLayerType(LAYER_TYPE_HARDWARE, null);
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
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mFirstLayout = false;
        super.onLayout(changed, l, t, r, b);
    }

    CustomizationType getCustomizeFilterForTabTag(String tag) {
        if (tag.equals(WIDGETS_TAG)) {
            return CustomizationType.WidgetCustomization;
        } else if (tag.equals(APPLICATIONS_TAG)) {
            return CustomizationType.ApplicationCustomization;
        } else if (tag.equals(WALLPAPERS_TAG)) {
            return CustomizePagedView.CustomizationType.WallpaperCustomization;
        } else if (tag.equals(SHORTCUTS_TAG)) {
            return CustomizePagedView.CustomizationType.ShortcutCustomization;
        }
        return CustomizationType.WidgetCustomization;
    }
}
