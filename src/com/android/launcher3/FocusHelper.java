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

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderPagedView;
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

/**
 * A keyboard listener we set on full screen pages (e.g. custom content).
 */
class FullscreenKeyEventListener implements View.OnKeyListener {
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_PAGE_DOWN || keyCode == KeyEvent.KEYCODE_PAGE_UP) {
            // Handle the key event just like a workspace icon would in these cases. In this case,
            // it will basically act as if there is a single icon in the top left (so you could
            // think of the fullscreen page as a focusable fullscreen widget).
            return FocusHelper.handleIconKeyEvent(v, keyCode, event);
        }
        return false;
    }
}

/**
 * TODO: Reevaluate if this is still required
 */
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
                if (FeatureFlags.IS_DOGFOOD_BUILD) {
                    throw new IllegalStateException("Parent of the focused item is not supported.");
                } else {
                    return false;
                }
            }

            // Initialize variables.
            final ShortcutAndWidgetContainer itemContainer = (ShortcutAndWidgetContainer) v.getParent();
            final CellLayout cellLayout = (CellLayout) itemContainer.getParent();

            final int iconIndex = itemContainer.indexOfChild(v);
            final FolderPagedView pagedView = (FolderPagedView) cellLayout.getParent();

            final int pageIndex = pagedView.indexOfChild(cellLayout);
            final int pageCount = pagedView.getPageCount();
            final boolean isLayoutRtl = Utilities.isRtl(v.getResources());

            int[][] matrix = FocusLogic.createSparseMatrix(cellLayout);
            // Process focus.
            int newIconIndex = FocusLogic.handleKeyEvent(keyCode, matrix, iconIndex, pageIndex,
                    pageCount, isLayoutRtl);
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
                                    ^ newParent.invertLayoutHorizontally()) ? 0 : matrix.length - 1,
                                row);
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
                        child = newParent.getChildAt(matrix.length - 1, matrix[0].length - 1);
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
                        child = FocusLogic.getAdjacentChildInNextFolderPage(
                                newParent, v, newIconIndex);
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
     * Handles key events in the workspace hotseat (bottom of the screen).
     * <p>Currently we don't special case for the phone UI in different orientations, even though
     * the hotseat is on the side in landscape mode. This is to ensure that accessibility
     * consistency is maintained across rotations.
     */
    static boolean handleHotseatButtonKeyEvent(View v, int keyCode, KeyEvent e) {
        boolean consume = FocusLogic.shouldConsume(keyCode);
        if (e.getAction() == KeyEvent.ACTION_UP || !consume) {
            return consume;
        }

        final Launcher launcher = Launcher.getLauncher(v.getContext());
        final DeviceProfile profile = launcher.getDeviceProfile();

        if (DEBUG) {
            Log.v(TAG, String.format(
                    "Handle HOTSEAT BUTTONS keyevent=[%s] on hotseat buttons, isVertical=%s",
                    KeyEvent.keyCodeToString(keyCode), profile.isVerticalBarLayout()));
        }

        // Initialize the variables.
        final Workspace workspace = (Workspace) v.getRootView().findViewById(R.id.workspace);
        final ShortcutAndWidgetContainer hotseatParent = (ShortcutAndWidgetContainer) v.getParent();
        final CellLayout hotseatLayout = (CellLayout) hotseatParent.getParent();

        final ItemInfo itemInfo = (ItemInfo) v.getTag();
        int pageIndex = workspace.getNextPage();
        int pageCount = workspace.getChildCount();
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
            matrix = FocusLogic.createSparseMatrixWithHotseat(iconLayout, hotseatLayout, profile);
            iconIndex += iconParent.getChildCount();
            parent = iconParent;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT &&
                profile.isVerticalBarLayout()) {
            matrix = FocusLogic.createSparseMatrixWithHotseat(iconLayout, hotseatLayout, profile);
            iconIndex += iconParent.getChildCount();
            parent = iconParent;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT &&
                profile.isVerticalBarLayout()) {
            keyCode = KeyEvent.KEYCODE_PAGE_DOWN;
        } else {
            // For other KEYCODE_DPAD_LEFT and KEYCODE_DPAD_RIGHT navigation, do not use the
            // matrix extended with hotseat.
            matrix = FocusLogic.createSparseMatrix(hotseatLayout);
            parent = hotseatParent;
        }

        // Process the focus.
        int newIconIndex = FocusLogic.handleKeyEvent(keyCode, matrix, iconIndex, pageIndex,
                pageCount, Utilities.isRtl(v.getResources()));

        View newIcon = null;
        switch (newIconIndex) {
            case FocusLogic.NEXT_PAGE_FIRST_ITEM:
                parent = getCellLayoutChildrenForIndex(workspace, pageIndex + 1);
                newIcon = parent.getChildAt(0);
                // TODO(hyunyoungs): handle cases where the child is not an icon but
                // a folder or a widget.
                workspace.snapToPage(pageIndex + 1);
                break;
            case FocusLogic.PREVIOUS_PAGE_FIRST_ITEM:
                parent = getCellLayoutChildrenForIndex(workspace, pageIndex - 1);
                newIcon = parent.getChildAt(0);
                // TODO(hyunyoungs): handle cases where the child is not an icon but
                // a folder or a widget.
                workspace.snapToPage(pageIndex - 1);
                break;
            case FocusLogic.PREVIOUS_PAGE_LAST_ITEM:
                parent = getCellLayoutChildrenForIndex(workspace, pageIndex - 1);
                newIcon = parent.getChildAt(parent.getChildCount() - 1);
                // TODO(hyunyoungs): handle cases where the child is not an icon but
                // a folder or a widget.
                workspace.snapToPage(pageIndex - 1);
                break;
            case FocusLogic.PREVIOUS_PAGE_LEFT_COLUMN:
            case FocusLogic.PREVIOUS_PAGE_RIGHT_COLUMN:
                // Go to the previous page but keep the focus on the same hotseat icon.
                workspace.snapToPage(pageIndex - 1);
                break;
            case FocusLogic.NEXT_PAGE_LEFT_COLUMN:
            case FocusLogic.NEXT_PAGE_RIGHT_COLUMN:
                // Go to the next page but keep the focus on the same hotseat icon.
                workspace.snapToPage(pageIndex + 1);
                break;
        }
        if (parent == iconParent && newIconIndex >= iconParent.getChildCount()) {
            newIconIndex -= iconParent.getChildCount();
        }
        if (parent != null) {
            if (newIcon == null && newIconIndex >= 0) {
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

        Launcher launcher = Launcher.getLauncher(v.getContext());
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
        final ViewGroup tabs = (ViewGroup) dragLayer.findViewById(R.id.drop_target_bar);
        final Hotseat hotseat = (Hotseat) dragLayer.findViewById(R.id.hotseat);

        final ItemInfo itemInfo = (ItemInfo) v.getTag();
        final int iconIndex = parent.indexOfChild(v);
        final int pageIndex = workspace.indexOfChild(iconLayout);
        final int pageCount = workspace.getChildCount();

        CellLayout hotseatLayout = (CellLayout) hotseat.getChildAt(0);
        ShortcutAndWidgetContainer hotseatParent = hotseatLayout.getShortcutsAndWidgets();
        int[][] matrix;

        // KEYCODE_DPAD_DOWN in portrait (KEYCODE_DPAD_RIGHT in landscape) is the only key allowed
        // to take a user to the hotseat. For other dpad navigation, do not use the matrix extended
        // with the hotseat.
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && !profile.isVerticalBarLayout()) {
            matrix = FocusLogic.createSparseMatrixWithHotseat(iconLayout, hotseatLayout, profile);
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT &&
                profile.isVerticalBarLayout()) {
            matrix = FocusLogic.createSparseMatrixWithHotseat(iconLayout, hotseatLayout, profile);
        } else {
            matrix = FocusLogic.createSparseMatrix(iconLayout);
        }

        // Process the focus.
        int newIconIndex = FocusLogic.handleKeyEvent(keyCode, matrix, iconIndex, pageIndex,
                pageCount, Utilities.isRtl(v.getResources()));
        boolean isRtl = Utilities.isRtl(v.getResources());
        View newIcon = null;
        CellLayout workspaceLayout = (CellLayout) workspace.getChildAt(pageIndex);
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
                if (parent != null) {
                    iconLayout = (CellLayout) parent.getParent();
                    matrix = FocusLogic.createSparseMatrixWithPivotColumn(iconLayout,
                            iconLayout.getCountX(), row);
                    newIconIndex = FocusLogic.handleKeyEvent(keyCode, matrix, FocusLogic.PIVOT,
                            newPageIndex, pageCount, Utilities.isRtl(v.getResources()));
                    if (newIconIndex == FocusLogic.NEXT_PAGE_FIRST_ITEM) {
                        newIcon = handleNextPageFirstItem(workspace, hotseatLayout, pageIndex,
                                isRtl);
                    } else if (newIconIndex == FocusLogic.PREVIOUS_PAGE_LAST_ITEM) {
                        newIcon = handlePreviousPageLastItem(workspace, hotseatLayout, pageIndex,
                                isRtl);
                    } else {
                        newIcon = parent.getChildAt(newIconIndex);
                    }
                }
                break;
            case FocusLogic.PREVIOUS_PAGE_FIRST_ITEM:
                workspaceLayout = (CellLayout) workspace.getChildAt(pageIndex - 1);
                newIcon = getFirstFocusableIconInReadingOrder(workspaceLayout, isRtl);
                if (newIcon == null) {
                    // Check the hotseat if no focusable item was found on the workspace.
                    newIcon = getFirstFocusableIconInReadingOrder(hotseatLayout, isRtl);
                    workspace.snapToPage(pageIndex - 1);
                }
                break;
            case FocusLogic.PREVIOUS_PAGE_LAST_ITEM:
                newIcon = handlePreviousPageLastItem(workspace, hotseatLayout, pageIndex, isRtl);
                break;
            case FocusLogic.NEXT_PAGE_FIRST_ITEM:
                newIcon = handleNextPageFirstItem(workspace, hotseatLayout, pageIndex, isRtl);
                break;
            case FocusLogic.NEXT_PAGE_LEFT_COLUMN:
            case FocusLogic.PREVIOUS_PAGE_LEFT_COLUMN:
                newPageIndex = pageIndex + 1;
                if (newIconIndex == FocusLogic.PREVIOUS_PAGE_LEFT_COLUMN) {
                    newPageIndex = pageIndex - 1;
                }
                row = ((CellLayout.LayoutParams) v.getLayoutParams()).cellY;
                parent = getCellLayoutChildrenForIndex(workspace, newPageIndex);
                if (parent != null) {
                    iconLayout = (CellLayout) parent.getParent();
                    matrix = FocusLogic.createSparseMatrixWithPivotColumn(iconLayout, -1, row);
                    newIconIndex = FocusLogic.handleKeyEvent(keyCode, matrix, FocusLogic.PIVOT,
                            newPageIndex, pageCount, Utilities.isRtl(v.getResources()));
                    if (newIconIndex == FocusLogic.NEXT_PAGE_FIRST_ITEM) {
                        newIcon = handleNextPageFirstItem(workspace, hotseatLayout, pageIndex,
                                isRtl);
                    } else if (newIconIndex == FocusLogic.PREVIOUS_PAGE_LAST_ITEM) {
                        newIcon = handlePreviousPageLastItem(workspace, hotseatLayout, pageIndex,
                                isRtl);
                    } else {
                        newIcon = parent.getChildAt(newIconIndex);
                    }
                }
                break;
            case FocusLogic.CURRENT_PAGE_FIRST_ITEM:
                newIcon = getFirstFocusableIconInReadingOrder(workspaceLayout, isRtl);
                if (newIcon == null) {
                    // Check the hotseat if no focusable item was found on the workspace.
                    newIcon = getFirstFocusableIconInReadingOrder(hotseatLayout, isRtl);
                }
                break;
            case FocusLogic.CURRENT_PAGE_LAST_ITEM:
                newIcon = getFirstFocusableIconInReverseReadingOrder(workspaceLayout, isRtl);
                if (newIcon == null) {
                    // Check the hotseat if no focusable item was found on the workspace.
                    newIcon = getFirstFocusableIconInReverseReadingOrder(hotseatLayout, isRtl);
                }
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

    private static View handlePreviousPageLastItem(Workspace workspace, CellLayout hotseatLayout,
            int pageIndex, boolean isRtl) {
        if (pageIndex - 1 < 0) {
            return null;
        }
        CellLayout workspaceLayout = (CellLayout) workspace.getChildAt(pageIndex - 1);
        View newIcon = getFirstFocusableIconInReverseReadingOrder(workspaceLayout, isRtl);
        if (newIcon == null) {
            // Check the hotseat if no focusable item was found on the workspace.
            newIcon = getFirstFocusableIconInReverseReadingOrder(hotseatLayout,isRtl);
            workspace.snapToPage(pageIndex - 1);
        }
        return newIcon;
    }

    private static View handleNextPageFirstItem(Workspace workspace, CellLayout hotseatLayout,
            int pageIndex, boolean isRtl) {
        if (pageIndex + 1 >= workspace.getPageCount()) {
            return null;
        }
        CellLayout workspaceLayout = (CellLayout) workspace.getChildAt(pageIndex + 1);
        View newIcon = getFirstFocusableIconInReadingOrder(workspaceLayout, isRtl);
        if (newIcon == null) {
            // Check the hotseat if no focusable item was found on the workspace.
            newIcon = getFirstFocusableIconInReadingOrder(hotseatLayout, isRtl);
            workspace.snapToPage(pageIndex + 1);
        }
        return newIcon;
    }

    private static View getFirstFocusableIconInReadingOrder(CellLayout cellLayout, boolean isRtl) {
        View icon;
        int countX = cellLayout.getCountX();
        for (int y = 0; y < cellLayout.getCountY(); y++) {
            int increment = isRtl ? -1 : 1;
            for (int x = isRtl ? countX - 1 : 0; 0 <= x && x < countX; x += increment) {
                if ((icon = cellLayout.getChildAt(x, y)) != null && icon.isFocusable()) {
                    return icon;
                }
            }
        }
        return null;
    }

    private static View getFirstFocusableIconInReverseReadingOrder(CellLayout cellLayout,
            boolean isRtl) {
        View icon;
        int countX = cellLayout.getCountX();
        for (int y = cellLayout.getCountY() - 1; y >= 0; y--) {
            int increment = isRtl ? 1 : -1;
            for (int x = isRtl ? 0 : countX - 1; 0 <= x && x < countX; x += increment) {
                if ((icon = cellLayout.getChildAt(x, y)) != null && icon.isFocusable()) {
                    return icon;
                }
            }
        }
        return null;
    }
}
