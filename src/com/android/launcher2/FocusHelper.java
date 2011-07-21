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

import android.content.res.Configuration;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TabHost;
import android.widget.TabWidget;

import com.android.launcher.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * A keyboard listener we set on all the workspace icons.
 */
class BubbleTextViewKeyEventListener implements View.OnKeyListener {
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return FocusHelper.handleBubbleTextViewKeyEvent((BubbleTextView) v, keyCode, event);
    }
}

/**
 * A keyboard listener we set on all the hotseat buttons.
 */
class HotseatBubbleTextViewKeyEventListener implements View.OnKeyListener {
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        final Configuration configuration = v.getResources().getConfiguration();
        return FocusHelper.handleHotseatButtonKeyEvent(v, keyCode, event, configuration.orientation);
    }
}

/**
 * A keyboard listener we set on the last tab button in AppsCustomize to jump to then
 * market icon and vice versa.
 */
class AppsCustomizeTabKeyEventListener implements View.OnKeyListener {
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return FocusHelper.handleAppsCustomizeTabKeyEvent(v, keyCode, event);
    }
}

public class FocusHelper {
    /**
     * Private helper to get the parent TabHost in the view hiearchy.
     */
    private static TabHost findTabHostParent(View v) {
        ViewParent p = v.getParent();
        while (p != null && !(p instanceof TabHost)) {
            p = p.getParent();
        }
        return (TabHost) p;
    }

    /**
     * Handles key events in a AppsCustomize tab between the last tab view and the shop button.
     */
    static boolean handleAppsCustomizeTabKeyEvent(View v, int keyCode, KeyEvent e) {
        final TabHost tabHost = findTabHostParent(v);
        final ViewGroup contents = (ViewGroup)
                tabHost.findViewById(com.android.internal.R.id.tabcontent);
        final View shop = tabHost.findViewById(R.id.market_button);

        final int action = e.getAction();
        final boolean handleKeyEvent = (action != KeyEvent.ACTION_UP);
        boolean wasHandled = false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (handleKeyEvent) {
                    // Select the shop button if we aren't on it
                    if (v != shop) {
                        shop.requestFocus();
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (handleKeyEvent) {
                    // Select the content view (down is handled by the tab key handler otherwise)
                    if (v == shop) {
                        contents.requestFocus();
                        wasHandled = true;
                    }
                }
                break;
            default: break;
        }
        return wasHandled;
    }

    /**
     * Private helper to determine whether a view is visible.
     */
    private static boolean isVisible(View v) {
        return v.getVisibility() == View.VISIBLE;
    }

    /**
     * Handles key events in a PageViewExtendedLayout containing PagedViewWidgets.
     */
    static boolean handlePagedViewGridLayoutWidgetKeyEvent(PagedViewWidget w, int keyCode,
            KeyEvent e) {

        final PagedViewGridLayout parent = (PagedViewGridLayout) w.getParent();
        final ViewGroup container = (ViewGroup) parent.getParent();
        final TabHost tabHost = findTabHostParent(container);
        final TabWidget tabs = (TabWidget) tabHost.findViewById(com.android.internal.R.id.tabs);
        final int widgetIndex = parent.indexOfChild(w);
        final int widgetCount = parent.getChildCount();
        final int pageIndex = container.indexOfChild(parent);
        final int pageCount = container.getChildCount();
        final int cellCountX = parent.getCellCountX();
        final int cellCountY = parent.getCellCountY();
        final int x = widgetIndex % cellCountX;
        final int y = widgetIndex / cellCountX;

        final int action = e.getAction();
        final boolean handleKeyEvent = (action != KeyEvent.ACTION_UP);
        PagedViewGridLayout newParent = null;
        boolean wasHandled = false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (handleKeyEvent) {
                    // Select the previous widget or the last widget on the previous page
                    if (widgetIndex > 0) {
                        parent.getChildAt(widgetIndex - 1).requestFocus();
                    } else {
                        if (pageIndex > 0) {
                            newParent = (PagedViewGridLayout)
                                    container.getChildAt(pageIndex - 1);
                            newParent.getChildAt(newParent.getChildCount() - 1).requestFocus();
                        }
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (handleKeyEvent) {
                    // Select the next widget or the first widget on the next page
                    if (widgetIndex < (widgetCount - 1)) {
                        parent.getChildAt(widgetIndex + 1).requestFocus();
                    } else {
                        if (pageIndex < (pageCount - 1)) {
                            newParent = (PagedViewGridLayout)
                                    container.getChildAt(pageIndex + 1);
                            newParent.getChildAt(0).requestFocus();
                        }
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (handleKeyEvent) {
                    // Select the closest icon in the previous row, otherwise select the tab bar
                    if (y > 0) {
                        int newWidgetIndex = ((y - 1) * cellCountX) + x;
                        parent.getChildAt(newWidgetIndex).requestFocus();
                    } else {
                        tabs.requestFocus();
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (handleKeyEvent) {
                    // Select the closest icon in the previous row, otherwise do nothing
                    if (y < (cellCountY - 1)) {
                        int newWidgetIndex = Math.min(widgetCount - 1, ((y + 1) * cellCountX) + x);
                        parent.getChildAt(newWidgetIndex).requestFocus();
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (handleKeyEvent) {
                    // Simulate a click on the widget
                    View.OnClickListener clickListener = (View.OnClickListener) container;
                    clickListener.onClick(w);
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_PAGE_UP:
                if (handleKeyEvent) {
                    // Select the first item on the previous page, or the first item on this page
                    // if there is no previous page
                    if (pageIndex > 0) {
                        newParent = (PagedViewGridLayout) container.getChildAt(pageIndex - 1);
                        newParent.getChildAt(0).requestFocus();
                    } else {
                        parent.getChildAt(0).requestFocus();
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (handleKeyEvent) {
                    // Select the first item on the next page, or the last item on this page
                    // if there is no next page
                    if (pageIndex < (pageCount - 1)) {
                        newParent = (PagedViewGridLayout) container.getChildAt(pageIndex + 1);
                        newParent.getChildAt(0).requestFocus();
                    } else {
                        parent.getChildAt(widgetCount - 1).requestFocus();
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_MOVE_HOME:
                if (handleKeyEvent) {
                    // Select the first item on this page
                    parent.getChildAt(0).requestFocus();
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_MOVE_END:
                if (handleKeyEvent) {
                    // Select the last item on this page
                    parent.getChildAt(widgetCount - 1).requestFocus();
                }
                wasHandled = true;
                break;
            default: break;
        }
        return wasHandled;
    }

    /**
     * Private helper method to get the PagedViewCellLayoutChildren given a PagedViewCellLayout
     * index.
     */
    private static PagedViewCellLayoutChildren getPagedViewCellLayoutChildrenForIndex(
            ViewGroup container, int i) {
        ViewGroup parent = (ViewGroup) container.getChildAt(i);
        return (PagedViewCellLayoutChildren) parent.getChildAt(0);
    }

    /**
     * Handles key events in a PageViewCellLayout containing PagedViewIcons.
     */
    static boolean handlePagedViewIconKeyEvent(PagedViewIcon v, int keyCode, KeyEvent e) {
        final PagedViewCellLayoutChildren parent = (PagedViewCellLayoutChildren) v.getParent();
        final PagedViewCellLayout parentLayout = (PagedViewCellLayout) parent.getParent();
        // Note we have an extra parent because of the
        // PagedViewCellLayout/PagedViewCellLayoutChildren relationship
        final ViewGroup container = (ViewGroup) parentLayout.getParent();
        final TabHost tabHost = findTabHostParent(container);
        final TabWidget tabs = (TabWidget) tabHost.findViewById(com.android.internal.R.id.tabs);
        final int widgetIndex = parent.indexOfChild(v);
        final int widgetCount = parent.getChildCount();
        final int pageIndex = container.indexOfChild(parentLayout);
        final int pageCount = container.getChildCount();
        final int cellCountX = parentLayout.getCellCountX();
        final int cellCountY = parentLayout.getCellCountY();
        final int x = widgetIndex % cellCountX;
        final int y = widgetIndex / cellCountX;

        final int action = e.getAction();
        final boolean handleKeyEvent = (action != KeyEvent.ACTION_UP);
        PagedViewCellLayoutChildren newParent = null;
        boolean wasHandled = false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (handleKeyEvent) {
                    // Select the previous icon or the last icon on the previous page
                    if (widgetIndex > 0) {
                        parent.getChildAt(widgetIndex - 1).requestFocus();
                    } else {
                        if (pageIndex > 0) {
                            newParent = getPagedViewCellLayoutChildrenForIndex(container,
                                    pageIndex - 1);
                            newParent.getChildAt(newParent.getChildCount() - 1).requestFocus();
                        }
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (handleKeyEvent) {
                    // Select the next icon or the first icon on the next page
                    if (widgetIndex < (widgetCount - 1)) {
                        parent.getChildAt(widgetIndex + 1).requestFocus();
                    } else {
                        if (pageIndex < (pageCount - 1)) {
                            newParent = getPagedViewCellLayoutChildrenForIndex(container,
                                    pageIndex + 1);
                            newParent.getChildAt(0).requestFocus();
                        }
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (handleKeyEvent) {
                    // Select the closest icon in the previous row, otherwise select the tab bar
                    if (y > 0) {
                        int newWidgetIndex = ((y - 1) * cellCountX) + x;
                        parent.getChildAt(newWidgetIndex).requestFocus();
                    } else {
                        tabs.requestFocus();
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (handleKeyEvent) {
                    // Select the closest icon in the previous row, otherwise do nothing
                    if (y < (cellCountY - 1)) {
                        int newWidgetIndex = Math.min(widgetCount - 1, ((y + 1) * cellCountX) + x);
                        parent.getChildAt(newWidgetIndex).requestFocus();
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (handleKeyEvent) {
                    // Simulate a click on the icon
                    View.OnClickListener clickListener = (View.OnClickListener) container;
                    clickListener.onClick(v);
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_PAGE_UP:
                if (handleKeyEvent) {
                    // Select the first icon on the previous page, or the first icon on this page
                    // if there is no previous page
                    if (pageIndex > 0) {
                        newParent = getPagedViewCellLayoutChildrenForIndex(container,
                                pageIndex - 1);
                        newParent.getChildAt(0).requestFocus();
                    } else {
                        parent.getChildAt(0).requestFocus();
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (handleKeyEvent) {
                    // Select the first icon on the next page, or the last icon on this page
                    // if there is no next page
                    if (pageIndex < (pageCount - 1)) {
                        newParent = getPagedViewCellLayoutChildrenForIndex(container,
                                pageIndex + 1);
                        newParent.getChildAt(0).requestFocus();
                    } else {
                        parent.getChildAt(widgetCount - 1).requestFocus();
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_MOVE_HOME:
                if (handleKeyEvent) {
                    // Select the first icon on this page
                    parent.getChildAt(0).requestFocus();
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_MOVE_END:
                if (handleKeyEvent) {
                    // Select the last icon on this page
                    parent.getChildAt(widgetCount - 1).requestFocus();
                }
                wasHandled = true;
                break;
            default: break;
        }
        return wasHandled;
    }

    /**
     * Handles key events in the tab widget.
     */
    static boolean handleTabKeyEvent(AccessibleTabView v, int keyCode, KeyEvent e) {
        if (!LauncherApplication.isScreenLarge()) return false;

        final FocusOnlyTabWidget parent = (FocusOnlyTabWidget) v.getParent();
        final TabHost tabHost = findTabHostParent(parent);
        final ViewGroup contents = (ViewGroup)
                tabHost.findViewById(com.android.internal.R.id.tabcontent);
        final int tabCount = parent.getTabCount();
        final int tabIndex = parent.getChildTabIndex(v);

        final int action = e.getAction();
        final boolean handleKeyEvent = (action != KeyEvent.ACTION_UP);
        boolean wasHandled = false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (handleKeyEvent) {
                    // Select the previous tab
                    if (tabIndex > 0) {
                        parent.getChildTabViewAt(tabIndex - 1).requestFocus();
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (handleKeyEvent) {
                    // Select the next tab, or if the last tab has a focus right id, select that
                    if (tabIndex < (tabCount - 1)) {
                        parent.getChildTabViewAt(tabIndex + 1).requestFocus();
                    } else {
                        if (v.getNextFocusRightId() != View.NO_ID) {
                            tabHost.findViewById(v.getNextFocusRightId()).requestFocus();
                        }
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                // Do nothing
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (handleKeyEvent) {
                    // Select the content view
                    contents.requestFocus();
                }
                wasHandled = true;
                break;
            default: break;
        }
        return wasHandled;
    }

    /**
     * Handles key events in the workspace hotseat (bottom of the screen).
     */
    static boolean handleHotseatButtonKeyEvent(View v, int keyCode, KeyEvent e, int orientation) {
        final ViewGroup parent = (ViewGroup) v.getParent();
        final ViewGroup launcher = (ViewGroup) parent.getParent();
        final Workspace workspace = (Workspace) launcher.findViewById(R.id.workspace);
        final int buttonIndex = parent.indexOfChild(v);
        final int buttonCount = parent.getChildCount();
        final int pageIndex = workspace.getCurrentPage();
        final int pageCount = workspace.getChildCount();

        // NOTE: currently we don't special case for the phone UI in different
        // orientations, even though the hotseat is on the side in landscape mode.  This
        // is to ensure that accessibility consistency is maintained across rotations.

        final int action = e.getAction();
        final boolean handleKeyEvent = (action != KeyEvent.ACTION_UP);
        boolean wasHandled = false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (handleKeyEvent) {
                    // Select the previous button, otherwise snap to the previous page
                    if (buttonIndex > 0) {
                        parent.getChildAt(buttonIndex - 1).requestFocus();
                    } else {
                        workspace.snapToPage(pageIndex - 1);
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (handleKeyEvent) {
                    // Select the next button, otherwise snap to the next page
                    if (buttonIndex < (buttonCount - 1)) {
                        parent.getChildAt(buttonIndex + 1).requestFocus();
                    } else {
                        workspace.snapToPage(pageIndex + 1);
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (handleKeyEvent) {
                    // Select the first bubble text view in the current page of the workspace
                    final CellLayout layout = (CellLayout) workspace.getChildAt(pageIndex);
                    final CellLayoutChildren children = layout.getChildrenLayout();
                    final View newIcon = getBubbleTextViewInDirection(layout, children, -1, 1);
                    if (newIcon != null) {
                        newIcon.requestFocus();
                    } else {
                        workspace.requestFocus();
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                // Do nothing
                wasHandled = true;
                break;
            default: break;
        }
        return wasHandled;
    }

    /**
     * Private helper method to get the CellLayoutChildren given a CellLayout index.
     */
    private static CellLayoutChildren getCellLayoutChildrenForIndex(ViewGroup container, int i) {
        ViewGroup parent = (ViewGroup) container.getChildAt(i);
        return (CellLayoutChildren) parent.getChildAt(0);
    }

    /**
     * Private helper method to sort all the CellLayout children in order of their (x,y) spatially
     * from top left to bottom right.
     */
    private static ArrayList<View> getCellLayoutChildrenSortedSpatially(CellLayout layout,
            ViewGroup parent) {
        // First we order each the CellLayout children by their x,y coordinates
        final int cellCountX = layout.getCountX();
        final int count = parent.getChildCount();
        ArrayList<View> views = new ArrayList<View>();
        for (int j = 0; j < count; ++j) {
            views.add(parent.getChildAt(j));
        }
        Collections.sort(views, new Comparator<View>() {
            @Override
            public int compare(View lhs, View rhs) {
                CellLayout.LayoutParams llp = (CellLayout.LayoutParams) lhs.getLayoutParams();
                CellLayout.LayoutParams rlp = (CellLayout.LayoutParams) rhs.getLayoutParams();
                int lvIndex = (llp.cellY * cellCountX) + llp.cellX;
                int rvIndex = (rlp.cellY * cellCountX) + rlp.cellX;
                return lvIndex - rvIndex;
            }
        });
        return views;
    }
    /**
     * Private helper method to find the index of the next BubbleTextView in the delta direction.
     * @param delta either -1 or 1 depending on the direction we want to search
     */
    private static View findIndexOfBubbleTextView(ArrayList<View> views, int i, int delta) {
        // Then we find the next BubbleTextView offset by delta from i
        final int count = views.size();
        int newI = i + delta;
        while (0 <= newI && newI < count) {
            View newV = views.get(newI);
            if (newV instanceof BubbleTextView) {
                return newV;
            }
            newI += delta;
        }
        return null;
    }
    private static View getBubbleTextViewInDirection(CellLayout layout, ViewGroup parent, int i,
            int delta) {
        final ArrayList<View> views = getCellLayoutChildrenSortedSpatially(layout, parent);
        return findIndexOfBubbleTextView(views, i, delta);
    }
    private static View getBubbleTextViewInDirection(CellLayout layout, ViewGroup parent, View v,
            int delta) {
        final ArrayList<View> views = getCellLayoutChildrenSortedSpatially(layout, parent);
        return findIndexOfBubbleTextView(views, views.indexOf(v), delta);
    }
    /**
     * Private helper method to find the next closest BubbleTextView in the delta direction on the
     * next line.
     * @param delta either -1 or 1 depending on the line and direction we want to search
     */
    private static View getClosestBubbleTextViewOnLine(CellLayout layout, ViewGroup parent, View v,
            int lineDelta) {
        final ArrayList<View> views = getCellLayoutChildrenSortedSpatially(layout, parent);
        final CellLayout.LayoutParams lp = (CellLayout.LayoutParams) v.getLayoutParams();
        final int cellCountX = layout.getCountX();
        final int cellCountY = layout.getCountY();
        final int row = lp.cellY;
        final int newRow = row + lineDelta;
        if (0 <= newRow && newRow < cellCountY) {
            float closestDistance = Float.MAX_VALUE;
            int closestIndex = -1;
            int index = views.indexOf(v);
            int endIndex = (lineDelta < 0) ? -1 : views.size();
            while (index != endIndex) {
                View newV = views.get(index);
                CellLayout.LayoutParams tmpLp = (CellLayout.LayoutParams) newV.getLayoutParams();
                boolean satisfiesRow = (lineDelta < 0) ? (tmpLp.cellY < row) : (tmpLp.cellY > row);
                if (satisfiesRow && newV instanceof BubbleTextView) {
                    float tmpDistance = (float) Math.sqrt(Math.pow(tmpLp.cellX - lp.cellX, 2) +
                            Math.pow(tmpLp.cellY - lp.cellY, 2));
                    if (tmpDistance < closestDistance) {
                        closestIndex = index;
                        closestDistance = tmpDistance;
                    }
                }
                if (index <= endIndex) {
                    ++index;
                } else {
                    --index;
                }
            }
            if (closestIndex > -1) {
                return views.get(closestIndex);
            }
        }
        return null;
    }

    /**
     * Handles key events in a Workspace containing BubbleTextView.
     */
    static boolean handleBubbleTextViewKeyEvent(BubbleTextView v, int keyCode, KeyEvent e) {
        CellLayoutChildren parent = (CellLayoutChildren) v.getParent();
        final CellLayout layout = (CellLayout) parent.getParent();
        final Workspace workspace = (Workspace) layout.getParent();
        final ViewGroup launcher = (ViewGroup) workspace.getParent();
        final ViewGroup tabs = (ViewGroup) launcher.findViewById(R.id.qsb_bar);
        final ViewGroup hotseat = (ViewGroup) launcher.findViewById(R.id.hotseat);
        int iconIndex = parent.indexOfChild(v);
        int iconCount = parent.getChildCount();
        int pageIndex = workspace.indexOfChild(layout);
        int pageCount = workspace.getChildCount();

        final int action = e.getAction();
        final boolean handleKeyEvent = (action != KeyEvent.ACTION_UP);
        boolean wasHandled = false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (handleKeyEvent) {
                    // Select the previous icon or the last icon on the previous page if possible
                    View newIcon = getBubbleTextViewInDirection(layout, parent, v, -1);
                    if (newIcon != null) {
                        newIcon.requestFocus();
                    } else {
                        if (pageIndex > 0) {
                            parent = getCellLayoutChildrenForIndex(workspace, pageIndex - 1);
                            newIcon = getBubbleTextViewInDirection(layout, parent,
                                    parent.getChildCount(), -1);
                            if (newIcon != null) {
                                newIcon.requestFocus();
                            } else {
                                // Snap to the previous page
                                workspace.snapToPage(pageIndex - 1);
                            }
                        }
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (handleKeyEvent) {
                    // Select the next icon or the first icon on the next page if possible
                    View newIcon = getBubbleTextViewInDirection(layout, parent, v, 1);
                    if (newIcon != null) {
                        newIcon.requestFocus();
                    } else {
                        if (pageIndex < (pageCount - 1)) {
                            parent = getCellLayoutChildrenForIndex(workspace, pageIndex + 1);
                            newIcon = getBubbleTextViewInDirection(layout, parent, -1, 1);
                            if (newIcon != null) {
                                newIcon.requestFocus();
                            } else {
                                // Snap to the next page
                                workspace.snapToPage(pageIndex + 1);
                            }
                        }
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (handleKeyEvent) {
                    // Select the closest icon in the previous line, otherwise select the tab bar
                    View newIcon = getClosestBubbleTextViewOnLine(layout, parent, v, -1);
                    if (newIcon != null) {
                        newIcon.requestFocus();
                        wasHandled = true;
                    } else {
                        tabs.requestFocus();
                    }
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (handleKeyEvent) {
                    // Select the closest icon in the next line, otherwise select the button bar
                    View newIcon = getClosestBubbleTextViewOnLine(layout, parent, v, 1);
                    if (newIcon != null) {
                        newIcon.requestFocus();
                        wasHandled = true;
                    } else if (hotseat != null) {
                        hotseat.requestFocus();
                    }
                }
                break;
            case KeyEvent.KEYCODE_PAGE_UP:
                if (handleKeyEvent) {
                    // Select the first icon on the previous page or the first icon on this page
                    // if there is no previous page
                    if (pageIndex > 0) {
                        parent = getCellLayoutChildrenForIndex(workspace, pageIndex - 1);
                        View newIcon = getBubbleTextViewInDirection(layout, parent, -1, 1);
                        if (newIcon != null) {
                            newIcon.requestFocus();
                        } else {
                            // Snap to the previous page
                            workspace.snapToPage(pageIndex - 1);
                        }
                    } else {
                        View newIcon = getBubbleTextViewInDirection(layout, parent, -1, 1);
                        if (newIcon != null) {
                            newIcon.requestFocus();
                        }
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (handleKeyEvent) {
                    // Select the first icon on the next page or the last icon on this page
                    // if there is no previous page
                    if (pageIndex < (pageCount - 1)) {
                        parent = getCellLayoutChildrenForIndex(workspace, pageIndex + 1);
                        View newIcon = getBubbleTextViewInDirection(layout, parent, -1, 1);
                        if (newIcon != null) {
                            newIcon.requestFocus();
                        } else {
                            // Snap to the next page
                            workspace.snapToPage(pageIndex + 1);
                        }
                    } else {
                        View newIcon = getBubbleTextViewInDirection(layout, parent,
                                parent.getChildCount(), -1);
                        if (newIcon != null) {
                            newIcon.requestFocus();
                        }
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_MOVE_HOME:
                if (handleKeyEvent) {
                    // Select the first icon on this page
                    View newIcon = getBubbleTextViewInDirection(layout, parent, -1, 1);
                    if (newIcon != null) {
                        newIcon.requestFocus();
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_MOVE_END:
                if (handleKeyEvent) {
                    // Select the last icon on this page
                    View newIcon = getBubbleTextViewInDirection(layout, parent,
                            parent.getChildCount(), -1);
                    if (newIcon != null) {
                        newIcon.requestFocus();
                    }
                }
                wasHandled = true;
                break;
            default: break;
        }
        return wasHandled;
    }
}
