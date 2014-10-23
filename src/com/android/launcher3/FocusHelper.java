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

import android.content.res.Configuration;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * A keyboard listener we set on all the workspace icons.
 */
class IconKeyEventListener implements View.OnKeyListener {
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return FocusHelper.handleIconKeyEvent(v, keyCode, event);
    }
}

/**
 * A keyboard listener we set on all the workspace icons.
 */
class FolderKeyEventListener implements View.OnKeyListener {
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return FocusHelper.handleFolderKeyEvent(v, keyCode, event);
    }
}

/**
 * A keyboard listener we set on all the hotseat buttons.
 */
class HotseatIconKeyEventListener implements View.OnKeyListener {
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        final Configuration configuration = v.getResources().getConfiguration();
        return FocusHelper.handleHotseatButtonKeyEvent(v, keyCode, event, configuration.orientation);
    }
}

public class FocusHelper {

    /**
     * Returns the Viewgroup containing page contents for the page at the index specified.
     */
    private static ViewGroup getAppsCustomizePage(ViewGroup container, int index) {
        ViewGroup page = (ViewGroup) ((PagedView) container).getPageAt(index);
        if (page instanceof CellLayout) {
            // There are two layers, a PagedViewCellLayout and PagedViewCellLayoutChildren
            page = ((CellLayout) page).getShortcutsAndWidgets();
        }
        return page;
    }

    /**
     * Handles key events in a PageViewCellLayout containing PagedViewIcons.
     */
    static boolean handleAppsCustomizeKeyEvent(View v, int keyCode, KeyEvent e) {
        ViewGroup parentLayout;
        ViewGroup itemContainer;
        int countX;
        int countY;
        if (v.getParent() instanceof ShortcutAndWidgetContainer) {
            itemContainer = (ViewGroup) v.getParent();
            parentLayout = (ViewGroup) itemContainer.getParent();
            countX = ((CellLayout) parentLayout).getCountX();
            countY = ((CellLayout) parentLayout).getCountY();
        } else {
            itemContainer = parentLayout = (ViewGroup) v.getParent();
            countX = ((PagedViewGridLayout) parentLayout).getCellCountX();
            countY = ((PagedViewGridLayout) parentLayout).getCellCountY();
        }

        // Note we have an extra parent because of the
        // PagedViewCellLayout/PagedViewCellLayoutChildren relationship
        final PagedView container = (PagedView) parentLayout.getParent();
        final int iconIndex = itemContainer.indexOfChild(v);
        final int itemCount = itemContainer.getChildCount();
        final int pageIndex = ((PagedView) container).indexToPage(container.indexOfChild(parentLayout));
        final int pageCount = container.getChildCount();

        final int x = iconIndex % countX;
        final int y = iconIndex / countX;

        final int action = e.getAction();
        final boolean handleKeyEvent = (action != KeyEvent.ACTION_UP);
        ViewGroup newParent = null;
        // Side pages do not always load synchronously, so check before focusing child siblings
        // willy-nilly
        View child = null;
        boolean wasHandled = false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (handleKeyEvent) {
                    // Select the previous icon or the last icon on the previous page
                    if (iconIndex > 0) {
                        itemContainer.getChildAt(iconIndex - 1).requestFocus();
                        v.playSoundEffect(SoundEffectConstants.NAVIGATION_LEFT);
                    } else {
                        if (pageIndex > 0) {
                            newParent = getAppsCustomizePage(container, pageIndex - 1);
                            if (newParent != null) {
                                container.snapToPage(pageIndex - 1);
                                child = newParent.getChildAt(newParent.getChildCount() - 1);
                                if (child != null) {
                                    child.requestFocus();
                                    v.playSoundEffect(SoundEffectConstants.NAVIGATION_LEFT);
                                }
                            }
                        }
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (handleKeyEvent) {
                    // Select the next icon or the first icon on the next page
                    if (iconIndex < (itemCount - 1)) {
                        itemContainer.getChildAt(iconIndex + 1).requestFocus();
                        v.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT);
                    } else {
                        if (pageIndex < (pageCount - 1)) {
                            newParent = getAppsCustomizePage(container, pageIndex + 1);
                            if (newParent != null) {
                                container.snapToPage(pageIndex + 1);
                                child = newParent.getChildAt(0);
                                if (child != null) {
                                    child.requestFocus();
                                    v.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT);
                                }
                            }
                        }
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (handleKeyEvent) {
                    // Select the closest icon in the previous row, otherwise select the tab bar
                    if (y > 0) {
                        int newiconIndex = ((y - 1) * countX) + x;
                        itemContainer.getChildAt(newiconIndex).requestFocus();
                        v.playSoundEffect(SoundEffectConstants.NAVIGATION_UP);
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (handleKeyEvent) {
                    // Select the closest icon in the next row, otherwise do nothing
                    if (y < (countY - 1)) {
                        int newiconIndex = Math.min(itemCount - 1, ((y + 1) * countX) + x);
                        int newIconY = newiconIndex / countX;
                        if (newIconY != y) {
                            itemContainer.getChildAt(newiconIndex).requestFocus();
                            v.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN);
                        }
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_PAGE_UP:
                if (handleKeyEvent) {
                    // Select the first icon on the previous page, or the first icon on this page
                    // if there is no previous page
                    if (pageIndex > 0) {
                        newParent = getAppsCustomizePage(container, pageIndex - 1);
                        if (newParent != null) {
                            container.snapToPage(pageIndex - 1);
                            child = newParent.getChildAt(0);
                            if (child != null) {
                                child.requestFocus();
                                v.playSoundEffect(SoundEffectConstants.NAVIGATION_UP);
                            }
                        }
                    } else {
                        itemContainer.getChildAt(0).requestFocus();
                        v.playSoundEffect(SoundEffectConstants.NAVIGATION_UP);
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (handleKeyEvent) {
                    // Select the first icon on the next page, or the last icon on this page
                    // if there is no next page
                    if (pageIndex < (pageCount - 1)) {
                        newParent = getAppsCustomizePage(container, pageIndex + 1);
                        if (newParent != null) {
                            container.snapToPage(pageIndex + 1);
                            child = newParent.getChildAt(0);
                            if (child != null) {
                                child.requestFocus();
                                v.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN);
                            }
                        }
                    } else {
                        itemContainer.getChildAt(itemCount - 1).requestFocus();
                        v.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN);
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_MOVE_HOME:
                if (handleKeyEvent) {
                    // Select the first icon on this page
                    itemContainer.getChildAt(0).requestFocus();
                    v.playSoundEffect(SoundEffectConstants.NAVIGATION_UP);
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_MOVE_END:
                if (handleKeyEvent) {
                    // Select the last icon on this page
                    itemContainer.getChildAt(itemCount - 1).requestFocus();
                    v.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN);
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
        ShortcutAndWidgetContainer parent = (ShortcutAndWidgetContainer) v.getParent();
        final CellLayout layout = (CellLayout) parent.getParent();

        // NOTE: currently we don't special case for the phone UI in different
        // orientations, even though the hotseat is on the side in landscape mode. This
        // is to ensure that accessibility consistency is maintained across rotations.
        final int action = e.getAction();
        final boolean handleKeyEvent = (action != KeyEvent.ACTION_UP);
        boolean wasHandled = false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (handleKeyEvent) {
                    ArrayList<View> views = getCellLayoutChildrenSortedSpatially(layout, parent);
                    int myIndex = views.indexOf(v);
                    // Select the previous button, otherwise do nothing
                    if (myIndex > 0) {
                        views.get(myIndex - 1).requestFocus();
                        v.playSoundEffect(SoundEffectConstants.NAVIGATION_LEFT);
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (handleKeyEvent) {
                    ArrayList<View> views = getCellLayoutChildrenSortedSpatially(layout, parent);
                    int myIndex = views.indexOf(v);
                    // Select the next button, otherwise do nothing
                    if (myIndex < views.size() - 1) {
                        views.get(myIndex + 1).requestFocus();
                        v.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT);
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (handleKeyEvent) {
                    final Workspace workspace = (Workspace)
                            v.getRootView().findViewById(R.id.workspace);
                    if (workspace != null) {
                        int pageIndex = workspace.getCurrentPage();
                        CellLayout topLayout = (CellLayout) workspace.getChildAt(pageIndex);
                        ShortcutAndWidgetContainer children = topLayout.getShortcutsAndWidgets();
                        final View newIcon = getIconInDirection(layout, children, -1, 1);
                        // Select the first bubble text view in the current page of the workspace
                        if (newIcon != null) {
                            newIcon.requestFocus();
                            v.playSoundEffect(SoundEffectConstants.NAVIGATION_UP);
                        } else {
                            workspace.requestFocus();
                        }
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
    private static ShortcutAndWidgetContainer getCellLayoutChildrenForIndex(
            ViewGroup container, int i) {
        CellLayout parent = (CellLayout) container.getChildAt(i);
        return parent.getShortcutsAndWidgets();
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
     * Private helper method to find the index of the next BubbleTextView or FolderIcon in the 
     * direction delta.
     * 
     * @param delta either -1 or 1 depending on the direction we want to search
     */
    private static View findIndexOfIcon(ArrayList<View> views, int i, int delta) {
        // Then we find the next BubbleTextView offset by delta from i
        final int count = views.size();
        int newI = i + delta;
        while (0 <= newI && newI < count) {
            View newV = views.get(newI);
            if (newV instanceof BubbleTextView || newV instanceof FolderIcon) {
                return newV;
            }
            newI += delta;
        }
        return null;
    }
    private static View getIconInDirection(CellLayout layout, ViewGroup parent, int i,
            int delta) {
        final ArrayList<View> views = getCellLayoutChildrenSortedSpatially(layout, parent);
        return findIndexOfIcon(views, i, delta);
    }
    private static View getIconInDirection(CellLayout layout, ViewGroup parent, View v,
            int delta) {
        final ArrayList<View> views = getCellLayoutChildrenSortedSpatially(layout, parent);
        return findIndexOfIcon(views, views.indexOf(v), delta);
    }
    /**
     * Private helper method to find the next closest BubbleTextView or FolderIcon in the direction 
     * delta on the next line.
     * 
     * @param delta either -1 or 1 depending on the line and direction we want to search
     */
    private static View getClosestIconOnLine(CellLayout layout, ViewGroup parent, View v,
            int lineDelta) {
        final ArrayList<View> views = getCellLayoutChildrenSortedSpatially(layout, parent);
        final CellLayout.LayoutParams lp = (CellLayout.LayoutParams) v.getLayoutParams();
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
                if (satisfiesRow &&
                        (newV instanceof BubbleTextView || newV instanceof FolderIcon)) {
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
     * Handles key events in a Workspace containing.
     */
    static boolean handleIconKeyEvent(View v, int keyCode, KeyEvent e) {
        ShortcutAndWidgetContainer parent = (ShortcutAndWidgetContainer) v.getParent();
        final CellLayout layout = (CellLayout) parent.getParent();
        final Workspace workspace = (Workspace) layout.getParent();
        final ViewGroup launcher = (ViewGroup) workspace.getParent();
        final ViewGroup tabs = (ViewGroup) launcher.findViewById(R.id.search_drop_target_bar);
        final ViewGroup hotseat = (ViewGroup) launcher.findViewById(R.id.hotseat);
        int pageIndex = workspace.indexOfChild(layout);
        int pageCount = workspace.getChildCount();

        final int action = e.getAction();
        final boolean handleKeyEvent = (action != KeyEvent.ACTION_UP);
        boolean wasHandled = false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (handleKeyEvent) {
                    // Select the previous icon or the last icon on the previous page if possible
                    View newIcon = getIconInDirection(layout, parent, v, -1);
                    if (newIcon != null) {
                        newIcon.requestFocus();
                        v.playSoundEffect(SoundEffectConstants.NAVIGATION_LEFT);
                    } else {
                        if (pageIndex > 0) {
                            parent = getCellLayoutChildrenForIndex(workspace, pageIndex - 1);
                            newIcon = getIconInDirection(layout, parent,
                                    parent.getChildCount(), -1);
                            if (newIcon != null) {
                                newIcon.requestFocus();
                            } else {
                                // Snap to the previous page
                                workspace.snapToPage(pageIndex - 1);
                            }
                            v.playSoundEffect(SoundEffectConstants.NAVIGATION_LEFT);
                        }
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (handleKeyEvent) {
                    // Select the next icon or the first icon on the next page if possible
                    View newIcon = getIconInDirection(layout, parent, v, 1);
                    if (newIcon != null) {
                        newIcon.requestFocus();
                        v.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT);
                    } else {
                        if (pageIndex < (pageCount - 1)) {
                            parent = getCellLayoutChildrenForIndex(workspace, pageIndex + 1);
                            newIcon = getIconInDirection(layout, parent, -1, 1);
                            if (newIcon != null) {
                                newIcon.requestFocus();
                            } else {
                                // Snap to the next page
                                workspace.snapToPage(pageIndex + 1);
                            }
                            v.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT);
                        }
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (handleKeyEvent) {
                    // Select the closest icon in the previous line, otherwise select the tab bar
                    View newIcon = getClosestIconOnLine(layout, parent, v, -1);
                    if (newIcon != null) {
                        newIcon.requestFocus();
                        wasHandled = true;
                    } else {
                        tabs.requestFocus();
                    }
                    v.playSoundEffect(SoundEffectConstants.NAVIGATION_UP);
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (handleKeyEvent) {
                    // Select the closest icon in the next line, otherwise select the button bar
                    View newIcon = getClosestIconOnLine(layout, parent, v, 1);
                    if (newIcon != null) {
                        newIcon.requestFocus();
                        v.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN);
                        wasHandled = true;
                    } else if (hotseat != null) {
                        hotseat.requestFocus();
                        v.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN);
                    }
                }
                break;
            case KeyEvent.KEYCODE_PAGE_UP:
                if (handleKeyEvent) {
                    // Select the first icon on the previous page or the first icon on this page
                    // if there is no previous page
                    if (pageIndex > 0) {
                        parent = getCellLayoutChildrenForIndex(workspace, pageIndex - 1);
                        View newIcon = getIconInDirection(layout, parent, -1, 1);
                        if (newIcon != null) {
                            newIcon.requestFocus();
                        } else {
                            // Snap to the previous page
                            workspace.snapToPage(pageIndex - 1);
                        }
                        v.playSoundEffect(SoundEffectConstants.NAVIGATION_UP);
                    } else {
                        View newIcon = getIconInDirection(layout, parent, -1, 1);
                        if (newIcon != null) {
                            newIcon.requestFocus();
                            v.playSoundEffect(SoundEffectConstants.NAVIGATION_UP);
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
                        View newIcon = getIconInDirection(layout, parent, -1, 1);
                        if (newIcon != null) {
                            newIcon.requestFocus();
                        } else {
                            // Snap to the next page
                            workspace.snapToPage(pageIndex + 1);
                        }
                        v.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN);
                    } else {
                        View newIcon = getIconInDirection(layout, parent,
                                parent.getChildCount(), -1);
                        if (newIcon != null) {
                            newIcon.requestFocus();
                            v.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN);
                        }
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_MOVE_HOME:
                if (handleKeyEvent) {
                    // Select the first icon on this page
                    View newIcon = getIconInDirection(layout, parent, -1, 1);
                    if (newIcon != null) {
                        newIcon.requestFocus();
                        v.playSoundEffect(SoundEffectConstants.NAVIGATION_UP);
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_MOVE_END:
                if (handleKeyEvent) {
                    // Select the last icon on this page
                    View newIcon = getIconInDirection(layout, parent,
                            parent.getChildCount(), -1);
                    if (newIcon != null) {
                        newIcon.requestFocus();
                        v.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN);
                    }
                }
                wasHandled = true;
                break;
            default: break;
        }
        return wasHandled;
    }

    /**
     * Handles key events for items in a Folder.
     */
    static boolean handleFolderKeyEvent(View v, int keyCode, KeyEvent e) {
        ShortcutAndWidgetContainer parent = (ShortcutAndWidgetContainer) v.getParent();
        final CellLayout layout = (CellLayout) parent.getParent();
        final ScrollView scrollView = (ScrollView) layout.getParent();
        final Folder folder = (Folder) scrollView.getParent();
        View title = folder.mFolderName;

        final int action = e.getAction();
        final boolean handleKeyEvent = (action != KeyEvent.ACTION_UP);
        boolean wasHandled = false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (handleKeyEvent) {
                    // Select the previous icon
                    View newIcon = getIconInDirection(layout, parent, v, -1);
                    if (newIcon != null) {
                        newIcon.requestFocus();
                        v.playSoundEffect(SoundEffectConstants.NAVIGATION_LEFT);
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (handleKeyEvent) {
                    // Select the next icon
                    View newIcon = getIconInDirection(layout, parent, v, 1);
                    if (newIcon != null) {
                        newIcon.requestFocus();
                    } else {
                        title.requestFocus();
                    }
                    v.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT);
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (handleKeyEvent) {
                    // Select the closest icon in the previous line
                    View newIcon = getClosestIconOnLine(layout, parent, v, -1);
                    if (newIcon != null) {
                        newIcon.requestFocus();
                        v.playSoundEffect(SoundEffectConstants.NAVIGATION_UP);
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (handleKeyEvent) {
                    // Select the closest icon in the next line
                    View newIcon = getClosestIconOnLine(layout, parent, v, 1);
                    if (newIcon != null) {
                        newIcon.requestFocus();
                    } else {
                        title.requestFocus();
                    }
                    v.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN);
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_MOVE_HOME:
                if (handleKeyEvent) {
                    // Select the first icon on this page
                    View newIcon = getIconInDirection(layout, parent, -1, 1);
                    if (newIcon != null) {
                        newIcon.requestFocus();
                        v.playSoundEffect(SoundEffectConstants.NAVIGATION_UP);
                    }
                }
                wasHandled = true;
                break;
            case KeyEvent.KEYCODE_MOVE_END:
                if (handleKeyEvent) {
                    // Select the last icon on this page
                    View newIcon = getIconInDirection(layout, parent,
                            parent.getChildCount(), -1);
                    if (newIcon != null) {
                        newIcon.requestFocus();
                        v.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN);
                    }
                }
                wasHandled = true;
                break;
            default: break;
        }
        return wasHandled;
    }
}
