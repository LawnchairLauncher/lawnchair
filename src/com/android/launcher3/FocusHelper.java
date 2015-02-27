/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.util.Log;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.util.FocusLogic;

/**
 * A keyboard listener we set on all the workspace icons.
 */
class IconKeyEventListener implements View.OnKeyListener {
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return FocusHelper.handleIconKeyEvent(v, keyCode, event);
    }
}

/**
 * A keyboard listener we set on all the workspace icons.
 */
class FolderKeyEventListener implements View.OnKeyListener {
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return FocusHelper.handleFolderKeyEvent(v, keyCode, event);
    }
}

/**
 * A keyboard listener we set on all the hotseat buttons.
 */
class HotseatIconKeyEventListener implements View.OnKeyListener {
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return FocusHelper.handleHotseatButtonKeyEvent(v, keyCode, event);
    }
}

public class FocusHelper {

    private static final String TAG = "FocusHelper";
    private static final boolean DEBUG = false;

    //
    // Key code handling methods.
    //

    /**
     * Handles key events in the all apps screen.
     */
    static boolean handleAppsCustomizeKeyEvent(View v, int keyCode, KeyEvent e) {
        boolean consume = FocusLogic.shouldConsume(keyCode);
        if (e.getAction() == KeyEvent.ACTION_UP) {
            return consume;
        }
        if (DEBUG) {
            Log.v(TAG, String.format("Handle ALL APPS keyevent=[%s].",
                    KeyEvent.keyCodeToString(keyCode)));
        }

        // Initialize variables.
        ViewGroup parentLayout;
        ViewGroup itemContainer;
        int countX;
        int countY;
        if (v.getParent() instanceof ShortcutAndWidgetContainer) {
            itemContainer = (ViewGroup) v.getParent();
            parentLayout = (ViewGroup) itemContainer.getParent();
            countX = ((CellLayout) parentLayout).getCountX();
            countY = ((CellLayout) parentLayout).getCountY();
        } else if (v.getParent() instanceof ViewGroup) {
            itemContainer = parentLayout = (ViewGroup) v.getParent();
            countX = ((PagedViewGridLayout) parentLayout).getCellCountX();
            countY = ((PagedViewGridLayout) parentLayout).getCellCountY();
        } else {
            throw new IllegalStateException(
                    "Parent of the focused item inside all apps screen is not a supported type.");
        }
        final int iconIndex = itemContainer.indexOfChild(v);
        final PagedView container = (PagedView) parentLayout.getParent();
        final int pageIndex = container.indexToPage(container.indexOfChild(parentLayout));
        final int pageCount = container.getChildCount();
        ViewGroup newParent = null;
        View child = null;
        int[][] matrix = FocusLogic.createFullMatrix(countX, countY, true);

        // Process focus.
        int newIconIndex = FocusLogic.handleKeyEvent(keyCode, countX, countY, matrix,
                iconIndex, pageIndex, pageCount);
        if (newIconIndex == FocusLogic.NOOP) {
            return consume;
        }
        switch (newIconIndex) {
            case FocusLogic.PREVIOUS_PAGE_FIRST_ITEM:
                newParent = getAppsCustomizePage(container, pageIndex - 1);
                if (newParent != null) {
                    container.snapToPage(pageIndex - 1);
                    child = newParent.getChildAt(0);
                }
            case FocusLogic.PREVIOUS_PAGE_LAST_ITEM:
                newParent = getAppsCustomizePage(container, pageIndex - 1);
                if (newParent != null) {
                    container.snapToPage(pageIndex - 1);
                    child = newParent.getChildAt(newParent.getChildCount() - 1);
                }
                break;
            case FocusLogic.NEXT_PAGE_FIRST_ITEM:
                newParent = getAppsCustomizePage(container, pageIndex + 1);
                if (newParent != null) {
                    container.snapToPage(pageIndex + 1);
                    child = newParent.getChildAt(0);
                }
                break;
            case FocusLogic.CURRENT_PAGE_FIRST_ITEM:
                child = container.getChildAt(0);
                break;
            case FocusLogic.CURRENT_PAGE_LAST_ITEM:
                child = itemContainer.getChildAt(itemContainer.getChildCount() - 1);
                break;
            default: // Go to some item on the current page.
                child = itemContainer.getChildAt(newIconIndex);
                break;
        }
        if (child != null) {
            child.requestFocus();
            playSoundEffect(keyCode, v);
        }
        return consume;
    }

    /**
     * Handles key events in the workspace hot seat (bottom of the screen).
     * <p>Currently we don't special case for the phone UI in different orientations, even though
     * the hotseat is on the side in landscape mode. This is to ensure that accessibility
     * consistency is maintained across rotations.
     */
    static boolean handleHotseatButtonKeyEvent(View v, int keyCode, KeyEvent e) {
        boolean consume = FocusLogic.shouldConsume(keyCode);
        if (e.getAction() == KeyEvent.ACTION_UP || !consume) {
            return consume;
        }
        int orientation = v.getResources().getConfiguration().orientation;

        if (DEBUG) {
            Log.v(TAG, String.format(
                    "Handle HOTSEAT BUTTONS keyevent=[%s] on hotseat buttons, orientation=%d",
                    KeyEvent.keyCodeToString(keyCode), orientation));
        }

        // Initialize the variables.
        final ShortcutAndWidgetContainer hotseatParent = (ShortcutAndWidgetContainer) v.getParent();
        final CellLayout hotseatLayout = (CellLayout) hotseatParent.getParent();
        Hotseat hotseat = (Hotseat) hotseatLayout.getParent();

        Workspace workspace = (Workspace) v.getRootView().findViewById(R.id.workspace);
        int pageIndex = workspace.getCurrentPage();
        int pageCount = workspace.getChildCount();
        int countX = -1;
        int countY = -1;
        int iconIndex = findIndexOfView(hotseatParent, v);

        final CellLayout iconLayout = (CellLayout) workspace.getChildAt(pageIndex);
        final ViewGroup iconParent = iconLayout.getShortcutsAndWidgets();

        ViewGroup parent = null;
        int[][] matrix = null;

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP &&
                orientation == Configuration.ORIENTATION_PORTRAIT) {
            matrix = FocusLogic.createSparseMatrix(iconLayout, hotseatLayout, orientation,
                    hotseat.getAllAppsButtonRank(), true /* include all apps icon */);
            iconIndex += iconParent.getChildCount();
            countX = iconLayout.getCountX();
            countY = iconLayout.getCountY() + hotseatLayout.getCountY();
            parent = iconParent;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT &&
                orientation == Configuration.ORIENTATION_LANDSCAPE) {
            matrix = FocusLogic.createSparseMatrix(iconLayout, hotseatLayout, orientation,
                    hotseat.getAllAppsButtonRank(), true /* include all apps icon */);
            iconIndex += iconParent.getChildCount();
            countX = iconLayout.getCountX() + hotseatLayout.getCountX();
            countY = iconLayout.getCountY();
            parent = iconParent;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT &&
                orientation == Configuration.ORIENTATION_LANDSCAPE) {
            keyCode = KeyEvent.KEYCODE_PAGE_DOWN;
        }else {
            // For other KEYCODE_DPAD_LEFT and KEYCODE_DPAD_RIGHT navigation, do not use the
            // matrix extended with hotseat.
            matrix = FocusLogic.createSparseMatrix(hotseatLayout);
            countX = hotseatLayout.getCountX();
            countY = hotseatLayout.getCountY();
            parent = hotseatParent;
        }

        // Process the focus.
        int newIconIndex = FocusLogic.handleKeyEvent(keyCode, countX, countY, matrix,
                iconIndex, pageIndex, pageCount);

        View newIcon = null;
        if (newIconIndex == FocusLogic.NEXT_PAGE_FIRST_ITEM) {
            parent = getCellLayoutChildrenForIndex(workspace, pageIndex + 1);
            newIcon = parent.getChildAt(0);
            // TODO(hyunyoungs): handle cases where the child is not an icon but
            // a folder or a widget.
            workspace.snapToPage(pageIndex + 1);
        }
        if (parent == iconParent && newIconIndex >= iconParent.getChildCount()) {
            newIconIndex -= iconParent.getChildCount();
        }
        if (parent != null) {
            if (newIcon == null && newIconIndex >=0) {
                newIcon = parent.getChildAt(newIconIndex);
            }
            if (newIcon != null) {
                newIcon.requestFocus();
                playSoundEffect(keyCode, v);
            }
        }
        return consume;
    }

    /**
     * Handles key events in a workspace containing icons.
     */
    static boolean handleIconKeyEvent(View v, int keyCode, KeyEvent e) {
        boolean consume = FocusLogic.shouldConsume(keyCode);
        if (e.getAction() == KeyEvent.ACTION_UP || !consume) {
            return consume;
        }
        int orientation = v.getResources().getConfiguration().orientation;
        if (DEBUG) {
            Log.v(TAG, String.format("Handle WORKSPACE ICONS keyevent=[%s] orientation=%d",
                    KeyEvent.keyCodeToString(keyCode), orientation));
        }

        // Initialize the variables.
        ShortcutAndWidgetContainer parent = (ShortcutAndWidgetContainer) v.getParent();
        final CellLayout iconLayout = (CellLayout) parent.getParent();
        final Workspace workspace = (Workspace) iconLayout.getParent();
        final ViewGroup launcher = (ViewGroup) workspace.getParent();
        final ViewGroup tabs = (ViewGroup) launcher.findViewById(R.id.search_drop_target_bar);
        final Hotseat hotseat = (Hotseat) launcher.findViewById(R.id.hotseat);
        int pageIndex = workspace.indexOfChild(iconLayout);
        int pageCount = workspace.getChildCount();
        int countX = iconLayout.getCountX();
        int countY = iconLayout.getCountY();
        final int iconIndex = findIndexOfView(parent, v);

        CellLayout hotseatLayout = (CellLayout) hotseat.getChildAt(0);
        ShortcutAndWidgetContainer hotseatParent = hotseatLayout.getShortcutsAndWidgets();
        int[][] matrix;

        // KEYCODE_DPAD_DOWN in portrait (KEYCODE_DPAD_RIGHT in landscape) is the only key allowed
        // to take a user to the hotseat. For other dpad navigation, do not use the matrix extended
        // with the hotseat.
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN &&
                orientation == Configuration.ORIENTATION_PORTRAIT) {
            matrix = FocusLogic.createSparseMatrix(iconLayout, hotseatLayout, orientation,
                    hotseat.getAllAppsButtonRank(), false /* all apps icon is ignored */);
            countY = countY + 1;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT &&
                orientation == Configuration.ORIENTATION_LANDSCAPE) {
            matrix = FocusLogic.createSparseMatrix(iconLayout, hotseatLayout, orientation,
                    hotseat.getAllAppsButtonRank(), false /* all apps icon is ignored */);
            countX = countX + 1;
        } else if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
            workspace.removeWorkspaceItem(v);
            return consume;
        } else {
            matrix = FocusLogic.createSparseMatrix(iconLayout);
        }

        // Process the focus.
        int newIconIndex = FocusLogic.handleKeyEvent(keyCode, countX, countY, matrix,
                iconIndex, pageIndex, pageCount);
        View newIcon = null;
        switch (newIconIndex) {
            case FocusLogic.NOOP:
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    newIcon = tabs;
                }
                break;
            case FocusLogic.PREVIOUS_PAGE_FIRST_ITEM:
                parent = getCellLayoutChildrenForIndex(workspace, pageIndex - 1);
                newIcon = parent.getChildAt(0);
                workspace.snapToPage(pageIndex - 1);
            case FocusLogic.PREVIOUS_PAGE_LAST_ITEM:
                parent = getCellLayoutChildrenForIndex(workspace, pageIndex - 1);
                newIcon = parent.getChildAt(parent.getChildCount() - 1);
                workspace.snapToPage(pageIndex - 1);
                break;
            case FocusLogic.NEXT_PAGE_FIRST_ITEM:
                parent = getCellLayoutChildrenForIndex(workspace, pageIndex + 1);
                newIcon = parent.getChildAt(0);
                workspace.snapToPage(pageIndex + 1);
                break;
            case FocusLogic.CURRENT_PAGE_FIRST_ITEM:
                newIcon = parent.getChildAt(0);
                break;
            case FocusLogic.CURRENT_PAGE_LAST_ITEM:
                newIcon = parent.getChildAt(parent.getChildCount() - 1);
                break;
            default:
                // current page, some item.
                if (0 <= newIconIndex && newIconIndex < parent.getChildCount()) {
                    newIcon = parent.getChildAt(newIconIndex);
                } else if (parent.getChildCount() <= newIconIndex &&
                        newIconIndex < parent.getChildCount() + hotseatParent.getChildCount()) {
                    newIcon = hotseatParent.getChildAt(newIconIndex - parent.getChildCount());
                }
                break;
        }
        if (newIcon != null) {
            newIcon.requestFocus();
            playSoundEffect(keyCode, v);
        }
        return consume;
    }

    /**
     * Handles key events for items in a Folder.
     */
    static boolean handleFolderKeyEvent(View v, int keyCode, KeyEvent e) {
        boolean consume = FocusLogic.shouldConsume(keyCode);
        if (e.getAction() == KeyEvent.ACTION_UP || !consume) {
            return consume;
        }
        if (DEBUG) {
            Log.v(TAG, String.format("Handle FOLDER keyevent=[%s].",
                    KeyEvent.keyCodeToString(keyCode)));
        }

        // Initialize the variables.
        ShortcutAndWidgetContainer parent = (ShortcutAndWidgetContainer) v.getParent();
        final CellLayout layout = (CellLayout) parent.getParent();
        final Folder folder = (Folder) layout.getParent().getParent();
        View title = folder.mFolderName;
        Workspace workspace = (Workspace) v.getRootView().findViewById(R.id.workspace);
        final int countX = layout.getCountX();
        final int countY = layout.getCountY();
        final int iconIndex = findIndexOfView(parent, v);
        int pageIndex = workspace.indexOfChild(layout);
        int pageCount = workspace.getChildCount();
        int[][] map = FocusLogic.createFullMatrix(countX, countY, true /* incremental order */);

        // Process the focus.
        int newIconIndex = FocusLogic.handleKeyEvent(keyCode, countX, countY, map, iconIndex,
                pageIndex, pageCount);
        View newIcon = null;
        switch (newIconIndex) {
            case FocusLogic.NOOP:
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    newIcon = title;
                }
                break;
            case FocusLogic.PREVIOUS_PAGE_FIRST_ITEM:
            case FocusLogic.PREVIOUS_PAGE_LAST_ITEM:
            case FocusLogic.NEXT_PAGE_FIRST_ITEM:
            case FocusLogic.CURRENT_PAGE_FIRST_ITEM:
            case FocusLogic.CURRENT_PAGE_LAST_ITEM:
                if (DEBUG) {
                    Log.v(TAG, "Page advance handling not supported on folder icons.");
                }
                break;
            default: // current page some item.
                newIcon = parent.getChildAt(newIconIndex);
                break;
        }
        if (newIcon != null) {
            newIcon.requestFocus();
            playSoundEffect(keyCode, v);
        }
        return consume;
    }

    //
    // Helper methods.
    //

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
     * Private helper method to get the CellLayoutChildren given a CellLayout index.
     */
    private static ShortcutAndWidgetContainer getCellLayoutChildrenForIndex(
            ViewGroup container, int i) {
        CellLayout parent = (CellLayout) container.getChildAt(i);
        return parent.getShortcutsAndWidgets();
    }

    private static int findIndexOfView(ViewGroup parent, View v) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (v != null && v.equals(parent.getChildAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Helper method to be used for playing sound effects.
     */
    private static void playSoundEffect(int keyCode, View v) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                v.playSoundEffect(SoundEffectConstants.NAVIGATION_LEFT);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                v.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_MOVE_END:
                v.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_MOVE_HOME:
                v.playSoundEffect(SoundEffectConstants.NAVIGATION_UP);
                break;
            default:
                break;
        }
    }
}
