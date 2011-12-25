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

package com.cyanogenmod.trebuchet;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import com.cyanogenmod.trebuchet.R;
import com.cyanogenmod.trebuchet.preference.PreferencesProvider;

import java.util.ArrayList;

public class AppsCustomizeTabHost extends TabHost implements LauncherTransitionable,
        TabHost.OnTabChangeListener  {
    private static final String TAG = "Trebuchet.AppsCustomizeTabHost";

    private static final String APPS_TAB_TAG = "APPS";
    private static final String WIDGETS_TAB_TAG = "WIDGETS";

    private final LayoutInflater mLayoutInflater;
    private ViewGroup mTabs;
    private ViewGroup mTabsContainer;
    private AppsCustomizePagedView mAppsCustomizePane;
    private FrameLayout mAnimationBuffer;
    private LinearLayout mContent;

    private boolean mInTransition;
    private boolean mTransitioningToWorkspace;
    private boolean mResetAfterTransition;
    private Runnable mRelayoutAndMakeVisible;

    // Preferences
    private boolean mJoinWidgetsApps;
    private boolean mFadeScrollingIndicator;

    public AppsCustomizeTabHost(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLayoutInflater = LayoutInflater.from(context);
        mRelayoutAndMakeVisible = new Runnable() {
                public void run() {
                    mTabs.requestLayout();
                    mTabsContainer.setAlpha(1f);
                }
            };

        // Preferences
        mJoinWidgetsApps = PreferencesProvider.Interface.Drawer.getJoinWidgetsApps();
        mFadeScrollingIndicator = PreferencesProvider.Interface.Drawer.Indicator.getFadeScrollingIndicator();
    }

    /**
     * Convenience methods to select specific tabs.  We want to set the content type immediately
     * in these cases, but we note that we still call setCurrentTabByTag() so that the tab view
     * reflects the new content (but doesn't do the animation and logic associated with changing
     * tabs manually).
     */
    void setContentTypeImmediate(AppsCustomizePagedView.ContentType type) {
        setOnTabChangedListener(null);
        onTabChangedStart();
        onTabChangedEnd(type);
        setCurrentTabByTag(getTabTagForContentType(type));
        setOnTabChangedListener(this);
    }
    void selectAppsTab() {
        setContentTypeImmediate(AppsCustomizePagedView.ContentType.Applications);
    }
    void selectWidgetsTab() {
        setContentTypeImmediate(AppsCustomizePagedView.ContentType.Widgets);
        mAppsCustomizePane.setCurrentPageToWidgets();
    }

    /**
     * Setup the tab host and create all necessary tabs.
     */
    @Override
    protected void onFinishInflate() {
        // Setup the tab host
        setup();

        final ViewGroup tabsContainer = (ViewGroup) findViewById(R.id.tabs_container);
        final TabWidget tabs = getTabWidget();
        final AppsCustomizePagedView appsCustomizePane = (AppsCustomizePagedView)
                findViewById(R.id.apps_customize_pane_content);
        mTabs = tabs;
        mTabsContainer = tabsContainer;
        mAppsCustomizePane = appsCustomizePane;
        mAnimationBuffer = (FrameLayout) findViewById(R.id.animation_buffer);
        mContent = (LinearLayout) findViewById(R.id.apps_customize_content);
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
        String label;
        label = getContext().getString(R.string.all_apps_button_label);
        tabView = (TextView) mLayoutInflater.inflate(R.layout.tab_widget_indicator, tabs, false);
        tabView.setText(label);
        tabView.setContentDescription(label);
        if (getContext() instanceof Launcher) {
            tabView.setOnLongClickListener(new View.OnLongClickListener() {
                    public boolean onLongClick(View v) {
                        ((Launcher) getContext()).onLongClickAppsTab(v);
                        return true;
                    }
            });
        }
        addTab(newTabSpec(APPS_TAB_TAG).setIndicator(tabView).setContent(contentFactory));
        label = getContext().getString(R.string.widgets_tab_label);
        tabView = (TextView) mLayoutInflater.inflate(R.layout.tab_widget_indicator, tabs, false);
        tabView.setText(label);
        tabView.setContentDescription(label);
        addTab(newTabSpec(WIDGETS_TAB_TAG).setIndicator(tabView).setContent(contentFactory));
        setOnTabChangedListener(this);

        // Setup the key listener to jump between the last tab view and the market icon
        AppsCustomizeTabKeyEventListener keyListener = new AppsCustomizeTabKeyEventListener();
        View lastTab = tabs.getChildTabViewAt(tabs.getTabCount() - 1);
        lastTab.setOnKeyListener(keyListener);
        View shopButton = findViewById(R.id.market_button);
        shopButton.setOnKeyListener(keyListener);

        // Soft menu button
        View overflowMenuButton = findViewById(R.id.overflow_menu_button);
        overflowMenuButton.setOnKeyListener(keyListener);

        // Hide the tab bar until we measure
        mTabsContainer.setAlpha(0f);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        boolean remeasureTabWidth = (mTabs.getLayoutParams().width <= 0);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        // Set the width of the tab list to the content width
        if (remeasureTabWidth) {
            int contentWidth = mAppsCustomizePane.getPageContentWidth();
            if (contentWidth > 0 && mTabs.getLayoutParams().width != contentWidth) {
                // Set the width and show the tab bar
                mTabs.getLayoutParams().width = contentWidth;
                mRelayoutAndMakeVisible.run();
            }

            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

     public boolean onInterceptTouchEvent(MotionEvent ev) {
         // If we are mid transitioning to the workspace, then intercept touch events here so we
         // can ignore them, otherwise we just let all apps handle the touch events.
         return mInTransition && mTransitioningToWorkspace || super.onInterceptTouchEvent(ev);
     }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Allow touch events to fall through to the workspace if we are transitioning there
        if (mInTransition && mTransitioningToWorkspace) {
            return super.onTouchEvent(event);
        }

        // Intercept all touch events up to the bottom of the AppsCustomizePane so they do not fall
        // through to the workspace and trigger showWorkspace()
        if (event.getY() < mAppsCustomizePane.getBottom()) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void onTabChangedStart() {
        mAppsCustomizePane.hideScrollingIndicator(false);
    }

    private void reloadCurrentPage() {
        if (!LauncherApplication.isScreenLarge()) {
            mAppsCustomizePane.flashScrollingIndicator(true);
        }
        mAppsCustomizePane.loadAssociatedPages(mAppsCustomizePane.getCurrentPage());
        mAppsCustomizePane.requestFocus();
    }

    private void onTabChangedEnd(AppsCustomizePagedView.ContentType type) {
        mAppsCustomizePane.setContentType(type);
    }

    @Override
    public void onTabChanged(String tabId) {
        final AppsCustomizePagedView.ContentType type = getContentTypeForTabTag(tabId);

        if (!mAppsCustomizePane.isContentType(type) || mJoinWidgetsApps) {

            // Animate the changing of the tab content by fading pages in and out
            final Resources res = getResources();
            final int duration = res.getInteger(R.integer.config_tabTransitionDuration);

            // We post a runnable here because there is a delay while the first page is loading and
            // the feedback from having changed the tab almost feels better than having it stick
            post(new Runnable() {
                @Override
                public void run() {
                if (mAppsCustomizePane.getMeasuredWidth() <= 0 ||
                    mAppsCustomizePane.getMeasuredHeight() <= 0) {
                    reloadCurrentPage();
                    return;
                }
                // Take the visible pages and re-parent them temporarily to mAnimatorBuffer
                // and then cross fade to the new pages
                int[] visiblePageRange = new int[2];
                mAppsCustomizePane.getVisiblePages(visiblePageRange);
                if (visiblePageRange[0] == -1 && visiblePageRange[1] == -1) {
                // If we can't get the visible page ranges, then just skip the animation
                    reloadCurrentPage();
                    return;
                }
                ArrayList<View> visiblePages = new ArrayList<View>();
                for (int i = visiblePageRange[0]; i <= visiblePageRange[1]; i++) {
                    visiblePages.add(mAppsCustomizePane.getPageAt(i));
                }
                // We want the pages to be rendered in exactly the same way as they were when
                // their parent was mAppsCustomizePane -- so set the scroll on mAnimationBuffer
                // to be exactly the same as mAppsCustomizePane, and below, set the left/top
                // parameters to be correct for each of the pages
                mAnimationBuffer.scrollTo(mAppsCustomizePane.getScrollX(), 0);

                // mAppsCustomizePane renders its children in reverse order, so
                // add the pages to mAnimationBuffer in reverse order to match that behavior
                for (int i = visiblePages.size() - 1; i >= 0; i--) {
                    View child = visiblePages.get(i);
                    if (child instanceof PagedViewCellLayout) {
                        ((PagedViewCellLayout) child).resetChildrenOnKeyListeners();
                    } else if (child instanceof PagedViewGridLayout) {
                        ((PagedViewGridLayout) child).resetChildrenOnKeyListeners();
                    }
                    PagedViewWidget.setDeletePreviewsWhenDetachedFromWindow(false);
                    mAppsCustomizePane.removeView(child);
                    PagedViewWidget.setDeletePreviewsWhenDetachedFromWindow(true);
                    mAnimationBuffer.setAlpha(1f);
                    mAnimationBuffer.setVisibility(View.VISIBLE);
                    LayoutParams p = new FrameLayout.LayoutParams(child.getMeasuredWidth(),
                            child.getMeasuredHeight());
                    p.setMargins(child.getLeft(), child.getTop(), 0, 0);
                    mAnimationBuffer.addView(child, p);

                // Toggle the new content
                onTabChangedStart();
                onTabChangedEnd(type);

                // Animate the transition
                ObjectAnimator outAnim = LauncherAnimUtils.ofFloat(mAnimationBuffer, "alpha", 0f);
                outAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mAnimationBuffer.setVisibility(View.GONE);
                        mAnimationBuffer.removeAllViews();
                    }
                    @Override
                    public void onAnimationCancel(Animator animation) {
                        mAnimationBuffer.setVisibility(View.GONE);
                        mAnimationBuffer.removeAllViews();
                    }
                });
                ObjectAnimator inAnim = LauncherAnimUtils.ofFloat(mAppsCustomizePane, "alpha", 1f);
                inAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        reloadCurrentPage();
                    }
                });
                AnimatorSet animSet = LauncherAnimUtils.createAnimatorSet();
                animSet.playTogether(outAnim, inAnim);
                animSet.setDuration(duration);
                animSet.start();
            }}
        });
	}
    }

    public void setCurrentTabFromContent(AppsCustomizePagedView.ContentType type) {
        setOnTabChangedListener(null);
        setCurrentTabByTag(getTabTagForContentType(type));
        setOnTabChangedListener(this);
    }

    /**
     * Returns the content type for the specified tab tag.
     */
    public AppsCustomizePagedView.ContentType getContentTypeForTabTag(String tag) {
        if (tag.equals(APPS_TAB_TAG)) {
            return AppsCustomizePagedView.ContentType.Applications;
        } else if (tag.equals(WIDGETS_TAB_TAG)) {
            return AppsCustomizePagedView.ContentType.Widgets;
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

    void reset() {
        if (mInTransition) {
            // Defer to after the transition to reset
            mResetAfterTransition = true;
        } else {
            // Reset immediately
            mAppsCustomizePane.reset();
        }
    }

    private void enableAndBuildHardwareLayer() {
        // isHardwareAccelerated() checks if we're attached to a window and if that
        // window is HW accelerated-- we were sometimes not attached to a window
        // and buildLayer was throwing an IllegalStateException
        if (isHardwareAccelerated()) {
            // Turn on hardware layers for performance
            setLayerType(LAYER_TYPE_HARDWARE, null);

            // force building the layer, so you don't get a blip early in an animation
            // when the layer is created layer
            buildLayer();
        }
    }

    @Override
    public View getContent() {
        return mContent;
    }

    /* LauncherTransitionable overrides */
    @Override
    public void onLauncherTransitionPrepare(Launcher l, boolean animated, boolean toWorkspace) {
        mAppsCustomizePane.onLauncherTransitionPrepare(l, animated, toWorkspace);
        mInTransition = true;
        mTransitioningToWorkspace = toWorkspace;

        if (toWorkspace) {
            // Going from All Apps -> Workspace
            setVisibilityOfSiblingsWithLowerZOrder(VISIBLE);
            // Stop the scrolling indicator - we don't want All Apps to be invalidating itself
            // during the transition, especially since it has a hardware layer set on it
            mAppsCustomizePane.cancelScrollingIndicatorAnimations();
        } else {
            // Going from Workspace -> All Apps
            mContent.setVisibility(VISIBLE);

            // Make sure the current page is loaded (we start loading the side pages after the
            // transition to prevent slowing down the animation)
            mAppsCustomizePane.loadAssociatedPages(mAppsCustomizePane.getCurrentPage(), true);

            if (!LauncherApplication.isScreenLarge()) {
                mAppsCustomizePane.showScrollingIndicator(true);
            }
        }

        if (mResetAfterTransition) {
            mAppsCustomizePane.reset();
            mResetAfterTransition = false;
        }
    }

    @Override
    public void onLauncherTransitionStart(Launcher l, boolean animated, boolean toWorkspace) {
        if (animated) {
            enableAndBuildHardwareLayer();
        }
    }

    @Override
    public void onLauncherTransitionStep(Launcher l, float t) {
        // Do nothing
    }

    @Override
    public void onLauncherTransitionEnd(Launcher l, boolean animated, boolean toWorkspace) {
        mAppsCustomizePane.onLauncherTransitionEnd(l, animated, toWorkspace);
        mInTransition = false;
        if (animated) {
            setLayerType(LAYER_TYPE_NONE, null);
        }

        if (!toWorkspace) {
            // Going from Workspace -> All Apps
            setVisibilityOfSiblingsWithLowerZOrder(INVISIBLE);

            // Dismiss the workspace cling and show the all apps cling (if not already shown)
            l.dismissWorkspaceCling(null);
            mAppsCustomizePane.showAllAppsCling();
            // Make sure adjacent pages are loaded (we wait until after the transition to
            // prevent slowing down the animation)
            mAppsCustomizePane.loadAssociatedPages(mAppsCustomizePane.getCurrentPage());

            if (!LauncherApplication.isScreenLarge() && mFadeScrollingIndicator) {
                mAppsCustomizePane.hideScrollingIndicator(false);
            }
        }
    }

    private void setVisibilityOfSiblingsWithLowerZOrder(int visibility) {
        ViewGroup parent = (ViewGroup) getParent();
        if (parent == null) return;

        final int count = parent.getChildCount();
        if (!isChildrenDrawingOrderEnabled()) {
            for (int i = 0; i < count; i++) {
                final View child = parent.getChildAt(i);
                if (child == this) {
                    break;
                } else {
                    if (child.getVisibility() == GONE) {
                        continue;
                    }
                    child.setVisibility(visibility);
                }
            }
        } else {
            throw new RuntimeException("Failed; can't get z-order of views");
        }
    }

    public void onWindowVisible() {
        if (getVisibility() == VISIBLE) {
            mContent.setVisibility(VISIBLE);
            // We unload the widget previews when the UI is hidden, so need to reload pages
            // Load the current page synchronously, and the neighboring pages asynchronously
            mAppsCustomizePane.loadAssociatedPages(mAppsCustomizePane.getCurrentPage(), true);
            mAppsCustomizePane.loadAssociatedPages(mAppsCustomizePane.getCurrentPage());
        }
    }

    public void onTrimMemory() {
        mContent.setVisibility(GONE);
        // Clear the widget pages of all their subviews - this will trigger the widget previews
        // to delete their bitmaps
        mAppsCustomizePane.clearAllWidgetPages();
    }

    boolean isTransitioning() {
        return mInTransition;
    }
}
