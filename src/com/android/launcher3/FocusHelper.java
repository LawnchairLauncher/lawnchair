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

import android.util.Log;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.util.FocusLogic;
import com.android.launcher3.util.Thunk;

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

    /**
     * Handles key events in paged folder.
     */
    public static class PagedFolderKeyEventListener implements View.OnKeyListener {

        private final Folder mFolder;

        public PagedFolderKeyEventListener(Folder folder) {
            mFolder = folder;
        }

        @Override
        public boolean onKey(View v, int keyCode, KeyEvent e) {
            boolean consume = FocusLogic.shouldConsume(keyCode);
            if (e.getAction() == KeyEvent.ACTION_UP) {
                return consume;
            }
            if (DEBUG) {
                Log.v(TAG, String.format("Handle ALL Folders keyevent=[%s].",
                        KeyEvent.keyCodeToString(keyCode)));
            }


            if (!(v.getParent() instanceof ShortcutAndWidgetContainer)) {
                if (LauncherAppState.isDogfoodBuild()) {
                    throw new IllegalStateException("Parent of the focused item is not supported.");
                } else {
                    return false;
                }
            }

            // Initialize variables.
            final ShortcutAndWidgetContainer itemContainer = (ShortcutAndWidgetContainer) v.getParent();
            final CellLayout cellLayout = (CellLayout) itemContainer.getParent();
            final int countX = cellLayout.getCountX();
            final int countY = cellLayout.getCountY();

            final int iconIndex = itemContainer.indexOfChild(v);
            final FolderPagedView pagedView = (FolderPagedView) cellLayout.getParent();

            final int pageIndex = pagedView.indexOfChild(cellLayout);
            final int pageCount = pagedView.getPageCount();
            final boolean isLayoutRtl = Utilities.isRtl(v.getResources());

            int[][] matrix = FocusLogic.createSparseMatrix(cellLayout);
            // Process focus.
            int newIconIndex = FocusLogic.handleKeyEvent(keyCode, countX,
                    countY, matrix, iconIndex, pageIndex, pageCount, isLayoutRtl);
            if (newIconIndex == FocusLogic.NOOP) {
                handleNoopKey(keyCode, v);
                return consume;
            }
            ShortcutAndWidgetContainer newParent = null;
            View child = null;

            switch (newIconIndex) {
                case FocusLogic.PREVIOUS_PAGE_RIGHT_COLUMN:
                case FocusLogic.PREVIOUS_PAGE_LEFT_COLUMN:
                    newParent = getCellLayoutChildrenForIndex(pagedView, pageIndex - 1);
                    if (newParent != null) {
                        int row = ((CellLayout.LayoutParams) v.getLayoutParams()).cellY;
                        pagedView.snapToPage(pageIndex - 1);
                        child = newParent.getChildAt(
                                ((newIconIndex == FocusLogic.PREVIOUS_PAGE_LEFT_COLUMN)
                                    ^ newParent.invertLayoutHorizontally()) ? 0 : countX - 1, row);
                    }
                    break;
                case FocusLogic.PREVIOUS_PAGE_FIRST_ITEM:
                    newParent = getCellLayoutChildrenForIndex(pagedView, pageIndex - 1);
                    if (newParent != null) {
                        pagedView.snapToPage(pageIndex - 1);
                        child = newParent.getChildAt(0, 0);
                    }
                    break;
                case FocusLogic.PREVIOUS_PAGE_LAST_ITEM:
                    newParent = getCellLayoutChildrenForIndex(pagedView, pageIndex - 1);
                    if (newParent != null) {
                        pagedView.snapToPage(pageIndex - 1);
                        child = newParent.getChildAt(countX - 1, countY - 1);
                    }
                    break;
                case FocusLogic.NEXT_PAGE_FIRST_ITEM:
                    newParent = getCellLayoutChildrenForIndex(pagedView, pageIndex + 1);
                    if (newParent != null) {
                        pagedView.snapToPage(pageIndex + 1);
                        child = newParent.getChildAt(0, 0);
                    }
                    break;
                case FocusLogic.NEXT_PAGE_LEFT_COLUMN:
                case FocusLogic.NEXT_PAGE_RIGHT_COLUMN:
                    newParent = getCellLayoutChildrenForIndex(pagedView, pageIndex + 1);
                    if (newParent != null) {
                        pagedView.snapToPage(pageIndex + 1);
                        child = FocusLogic.getAdjacentChildInNextPage(newParent, v, newIconIndex);
                    }
                    break;
                case FocusLogic.CURRENT_PAGE_FIRST_ITEM:
                    child = cellLayout.getChildAt(0, 0);
                    break;
                case FocusLogic.CURRENT_PAGE_LAST_ITEM:
                    child = pagedView.getLastItem();
                    break;
                default: // Go to some item on the current page.
                    child = itemContainer.getChildAt(newIconIndex);
                    break;
            }
            if (child != null) {
                child.requestFocus();
                playSoundEffect(keyCode, v);
            } else {
                handleNoopKey(keyCode, v);
            }
            return consume;
        }

        public void handleNoopKey(int keyCode, View v) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                mFolder.mFolderName.requestFocus();
                playSoundEffect(keyCode, v);
            }
        }
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

        DeviceProfile profile = ((Launcher) v.getContext()).getDeviceProfile();

        if (DEBUG) {
            Log.v(TAG, String.format(
                    "Handle HOTSEAT BUTTONS keyevent=[%s] on hotseat buttons, isVertical=%s",
                    KeyEvent.keyCodeToString(keyCode), profile.isVerticalBarLayout()));
        }

        // Initialize the variables.
        final ShortcutAndWidgetContainer hotseatParent = (ShortcutAndWidgetContainer) v.getParent();
        final CellLayout hotseatLayout = (CellLayout) hotseatParent.getParent();
        Hotseat hotseat = (Hotseat) hotseatLayout.getParent();

        Workspace workspace = (Workspace) v.getRootView().findViewById(R.id.workspace);
        int pageIndex = workspace.getNextPage();
        int pageCount = workspace.getChildCount();
        int countX = -1;
        int countY = -1;
        int iconIndex = hotseatParent.indexOfChild(v);
        int iconRank = ((CellLayout.LayoutParams) hotseatLayout.getShortcutsAndWidgets()
                .getChildAt(iconIndex).getLayoutParams()).cellX;

        final CellLayout iconLayout = (CellLayout) workspace.getChildAt(pageIndex);
        if (iconLayout == null) {
            // This check is to guard against cases where key strokes rushes in when workspace
            // child creation/deletion is still in flux. (e.g., during drop or fling
            // animation.)
            return consume;
        }
        final ViewGroup iconParent = iconLayout.getShortcutsAndWidgets();

        ViewGroup parent = null;
        int[][] matrix = null;

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP &&
                !profile.isVerticalBarLayout()) {
            matrix = FocusLogic.createSparseMatrix(iconLayout, hotseatLayout,
                    true /* hotseat horizontal */, profile.inv.hotseatAllAppsRank,
                    iconRank == profile.inv.hotseatAllAppsRank /* include all apps icon */);
            iconIndex += iconParent.getChildCount();
            countX = iconLayout.getCountX();
            countY = iconLayout.getCountY() + hotseatLayout.getCountY();
            parent = iconParent;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT &&
                profile.isVerticalBarLayout()) {
            matrix = FocusLogic.createSparseMatrix(iconLayout, hotseatLayout,
                    false /* hotseat horizontal */, profile.inv.hotseatAllAppsRank,
                    iconRank == profile.inv.hotseatAllAppsRank /* include all apps icon */);
            iconIndex += iconParent.getChildCount();
            countX = iconLayout.getCountX() + hotseatLayout.getCountX();
            countY = iconLayout.getCountY();
            parent = iconParent;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT &&
                profile.isVerticalBarLayout()) {
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
        int newIconIndex = FocusLogic.handleKeyEvent(keyCode, countX,
                countY, matrix, iconIndex, pageIndex, pageCount, Utilities.isRtl(v.getResources()));

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

        Launcher launcher = (Launcher) v.getContext();
        DeviceProfile profile = launcher.getDeviceProfile();

        if (DEBUG) {
            Log.v(TAG, String.format("Handle WORKSPACE ICONS keyevent=[%s] isVerticalBar=%s",
                    KeyEvent.keyCodeToString(keyCode), profile.isVerticalBarLayout()));
        }

        // Initialize the variables.
        ShortcutAndWidgetContainer parent = (ShortcutAndWidgetContainer) v.getParent();
        CellLayout iconLayout = (CellLayout) parent.getParent();
        final Workspace workspace = (Workspace) iconLayout.getParent();
        final ViewGroup dragLayer = (ViewGroup) workspace.getParent();
        final ViewGroup tabs = (ViewGroup) dragLayer.findViewById(R.id.search_drop_target_bar);
        final Hotseat hotseat = (Hotseat) dragLayer.findViewById(R.id.hotseat);

        final int iconIndex = parent.indexOfChild(v);
        final int pageIndex = workspace.indexOfChild(iconLayout);
        final int pageCount = workspace.getChildCount();
        int countX = iconLayout.getCountX();
        int countY = iconLayout.getCountY();

        CellLayout hotseatLayout = (CellLayout) hotseat.getChildAt(0);
        ShortcutAndWidgetContainer hotseatParent = hotseatLayout.getShortcutsAndWidgets();
        int[][] matrix;

        // KEYCODE_DPAD_DOWN in portrait (KEYCODE_DPAD_RIGHT in landscape) is the only key allowed
        // to take a user to the hotseat. For other dpad navigation, do not use the matrix extended
        // with the hotseat.
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && !profile.isVerticalBarLayout()) {
            matrix = FocusLogic.createSparseMatrix(iconLayout, hotseatLayout, true /* horizontal */,
                    profile.inv.hotseatAllAppsRank,
                    !hotseat.hasIcons() /* ignore all apps icon, unless there are no other icons */);
            countY = countY + 1;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT &&
                profile.isVerticalBarLayout()) {
            matrix = FocusLogic.createSparseMatrix(iconLayout, hotseatLayout, false /* horizontal */,
                    profile.inv.hotseatAllAppsRank,
                    !hotseat.hasIcons() /* ignore all apps icon, unless there are no other icons */);
            countX = countX + 1;
        } else if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
            workspace.removeWorkspaceItem(v);
            return consume;
        } else {
            matrix = FocusLogic.createSparseMatrix(iconLayout);
        }

        // Process the focus.
        int newIconIndex = FocusLogic.handleKeyEvent(keyCode, countX,
                countY, matrix, iconIndex, pageIndex, pageCount, Utilities.isRtl(v.getResources()));
        View newIcon = null;
        switch (newIconIndex) {
            case FocusLogic.NOOP:
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    newIcon = tabs;
                }
                break;
            case FocusLogic.PREVIOUS_PAGE_RIGHT_COLUMN:
            case FocusLogic.NEXT_PAGE_RIGHT_COLUMN:
                int newPageIndex = pageIndex - 1;
                if (newIconIndex == FocusLogic.NEXT_PAGE_RIGHT_COLUMN) {
                    newPageIndex = pageIndex + 1;
                }
                int row = ((CellLayout.LayoutParams) v.getLayoutParams()).cellY;
                parent = getCellLayoutChildrenForIndex(workspace, newPageIndex);
                workspace.snapToPage(newPageIndex);
                if (parent != null) {
                    workspace.snapToPage(newPageIndex);
                    iconLayout = (CellLayout) parent.getParent();
                    matrix = FocusLogic.createSparseMatrix(iconLayout,
                        iconLayout.getCountX(), row);
                    newIconIndex = FocusLogic.handleKeyEvent(keyCode, countX + 1, countY,
                            matrix, FocusLogic.PIVOT, newPageIndex, pageCount,
                            Utilities.isRtl(v.getResources()));
                    newIcon = parent.getChildAt(newIconIndex);
                }
                break;
            case FocusLogic.PREVIOUS_PAGE_FIRST_ITEM:
                parent = getCellLayoutChildrenForIndex(workspace, pageIndex - 1);
                newIcon = parent.getChildAt(0);
                workspace.snapToPage(pageIndex - 1);
                break;
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
            case FocusLogic.NEXT_PAGE_LEFT_COLUMN:
            case FocusLogic.PREVIOUS_PAGE_LEFT_COLUMN:
                newPageIndex = pageIndex + 1;
                if (newIconIndex == FocusLogic.PREVIOUS_PAGE_LEFT_COLUMN) {
                    newPageIndex = pageIndex - 1;
                }
                workspace.snapToPage(newPageIndex);
                row = ((CellLayout.LayoutParams) v.getLayoutParams()).cellY;
                parent = getCellLayoutChildrenForIndex(workspace, newPageIndex);
                if (parent != null) {
                    workspace.snapToPage(newPageIndex);
                    iconLayout = (CellLayout) parent.getParent();
                    matrix = FocusLogic.createSparseMatrix(iconLayout, -1, row);
                    newIconIndex = FocusLogic.handleKeyEvent(keyCode, countX + 1, countY,
                            matrix, FocusLogic.PIVOT, newPageIndex, pageCount,
                            Utilities.isRtl(v.getResources()));
                    newIcon = parent.getChildAt(newIconIndex);
                }
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

    //
    // Helper methods.
    //

    /**
     * Private helper method to get the CellLayoutChildren given a CellLayout index.
     */
    @Thunk static ShortcutAndWidgetContainer getCellLayoutChildrenForIndex(
            ViewGroup container, int i) {
        CellLayout parent = (CellLayout) container.getChildAt(i);
        return parent.getShortcutsAndWidgets();
    }

    /**
     * Helper method to be used for playing sound effects.
     */
    @Thunk static void playSoundEffect(int keyCode, View v) {
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
