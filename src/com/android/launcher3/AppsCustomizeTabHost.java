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

package com.android.launcher3;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;

public class AppsCustomizeTabHost extends FrameLayout implements LauncherTransitionable, Insettable  {
    static final String LOG_TAG = "AppsCustomizeTabHost";

    private static final String APPS_TAB_TAG = "APPS";
    private static final String WIDGETS_TAB_TAG = "WIDGETS";

    private AppsCustomizePagedView mPagedView;
    private View mContent;
    private boolean mInTransition = false;

    private final Rect mInsets = new Rect();

    public AppsCustomizeTabHost(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Convenience methods to select specific tabs.  We want to set the content type immediately
     * in these cases, but we note that we still call setCurrentTabByTag() so that the tab view
     * reflects the new content (but doesn't do the animation and logic associated with changing
     * tabs manually).
     */
    void setContentTypeImmediate(AppsCustomizePagedView.ContentType type) {
        mPagedView.setContentType(type);
    }

    public void setCurrentTabFromContent(AppsCustomizePagedView.ContentType type) {
        setContentTypeImmediate(type);
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        LayoutParams flp = (LayoutParams) mContent.getLayoutParams();
        flp.topMargin = insets.top;
        flp.bottomMargin = insets.bottom;
        flp.leftMargin = insets.left;
        flp.rightMargin = insets.right;
        mContent.setLayoutParams(flp);
    }

    /**
     * Setup the tab host and create all necessary tabs.
     */
    @Override
    protected void onFinishInflate() {
        mPagedView = (AppsCustomizePagedView) findViewById(R.id.apps_customize_pane_content);
        mContent = findViewById(R.id.content);
    }

    public String getContentTag() {
        return getTabTagForContentType(mPagedView.getContentType());
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
        // Reset immediately
        mPagedView.reset();
    }

    void trimMemory() {
        mPagedView.trimMemory();
    }

    public void onWindowVisible() {
        if (getVisibility() == VISIBLE) {
            mContent.setVisibility(VISIBLE);
            // We unload the widget previews when the UI is hidden, so need to reload pages
            // Load the current page synchronously, and the neighboring pages asynchronously
            mPagedView.loadAssociatedPages(mPagedView.getCurrentPage(), true);
            mPagedView.loadAssociatedPages(mPagedView.getCurrentPage());
        }
    }
    @Override
    public ViewGroup getContent() {
        return mPagedView;
    }

    public boolean isInTransition() {
        return mInTransition;
    }

    /* LauncherTransitionable overrides */
    @Override
    public void onLauncherTransitionPrepare(Launcher l, boolean animated, boolean toWorkspace) {
        mPagedView.onLauncherTransitionPrepare(l, animated, toWorkspace);
        mInTransition = true;

        if (toWorkspace) {
            // Going from All Apps -> Workspace
            setVisibilityOfSiblingsWithLowerZOrder(VISIBLE);
        } else {
            // Going from Workspace -> All Apps
            mContent.setVisibility(VISIBLE);

            // Make sure the current page is loaded (we start loading the side pages after the
            // transition to prevent slowing down the animation)
            // TODO: revisit this
            mPagedView.loadAssociatedPages(mPagedView.getCurrentPage());
        }
    }

    @Override
    public void onLauncherTransitionStart(Launcher l, boolean animated, boolean toWorkspace) {
        mPagedView.onLauncherTransitionStart(l, animated, toWorkspace);
    }

    @Override
    public void onLauncherTransitionStep(Launcher l, float t) {
        mPagedView.onLauncherTransitionStep(l, t);
    }

    @Override
    public void onLauncherTransitionEnd(Launcher l, boolean animated, boolean toWorkspace) {
        mPagedView.onLauncherTransitionEnd(l, animated, toWorkspace);
        mInTransition = false;

        if (!toWorkspace) {
            // Make sure adjacent pages are loaded (we wait until after the transition to
            // prevent slowing down the animation)
            mPagedView.loadAssociatedPages(mPagedView.getCurrentPage());

            // Opening apps, need to announce what page we are on.
            AccessibilityManager am = (AccessibilityManager)
                    getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am.isEnabled()) {
                // Notify the user when the page changes
                announceForAccessibility(mPagedView.getCurrentPageDescription());
            }

            // Going from Workspace -> All Apps
            // NOTE: We should do this at the end since we check visibility state in some of the
            // cling initialization/dismiss code above.
            setVisibilityOfSiblingsWithLowerZOrder(INVISIBLE);
        }
    }

    private void setVisibilityOfSiblingsWithLowerZOrder(int visibility) {
        ViewGroup parent = (ViewGroup) getParent();
        if (parent == null) return;

        View overviewPanel = ((Launcher) getContext()).getOverviewPanel();
        final int count = parent.getChildCount();
        if (!isChildrenDrawingOrderEnabled()) {
            for (int i = 0; i < count; i++) {
                final View child = parent.getChildAt(i);
                if (child == this) {
                    break;
                } else {
                    if (child.getVisibility() == GONE || child == overviewPanel) {
                        continue;
                    }
                    child.setVisibility(visibility);
                }
            }
        } else {
            throw new RuntimeException("Failed; can't get z-order of views");
        }
    }
}
