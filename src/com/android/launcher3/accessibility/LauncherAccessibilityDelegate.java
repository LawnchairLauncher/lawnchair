package com.android.launcher3.accessibility;

import static android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK;

import static com.android.launcher3.LauncherState.NORMAL;

import android.app.AlertDialog;
import android.appwidget.AppWidgetProviderInfo;
import android.content.DialogInterface;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;

import com.android.launcher3.AppInfo;
import com.android.launcher3.AppWidgetResizeFrame;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.ButtonDropTarget;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.FolderInfo;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.PendingAddItemInfo;
import com.android.launcher3.R;
import com.android.launcher3.Workspace;
import com.android.launcher3.WorkspaceItemInfo;
import com.android.launcher3.dragndrop.DragController.DragListener;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.keyboard.CustomActionsPopup;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.widget.LauncherAppWidgetHostView;

import java.util.ArrayList;

public class LauncherAccessibilityDelegate extends AccessibilityDelegate implements DragListener {

    private static final String TAG = "LauncherAccessibilityDelegate";

    public static final int REMOVE = R.id.action_remove;
    public static final int UNINSTALL = R.id.action_uninstall;
    public static final int RECONFIGURE = R.id.action_reconfigure;
    protected static final int ADD_TO_WORKSPACE = R.id.action_add_to_workspace;
    protected static final int MOVE = R.id.action_move;
    protected static final int MOVE_TO_WORKSPACE = R.id.action_move_to_workspace;
    protected static final int RESIZE = R.id.action_resize;
    public static final int DEEP_SHORTCUTS = R.id.action_deep_shortcuts;
    public static final int SHORTCUTS_AND_NOTIFICATIONS = R.id.action_shortcuts_and_notifications;

    public enum DragType {
        ICON,
        FOLDER,
        WIDGET
    }

    public static class DragInfo {
        public DragType dragType;
        public ItemInfo info;
        public View item;
    }

    protected final SparseArray<AccessibilityAction> mActions = new SparseArray<>();
    @Thunk final Launcher mLauncher;

    private DragInfo mDragInfo = null;

    public LauncherAccessibilityDelegate(Launcher launcher) {
        mLauncher = launcher;

        mActions.put(REMOVE, new AccessibilityAction(REMOVE,
                launcher.getText(R.string.remove_drop_target_label)));
        mActions.put(UNINSTALL, new AccessibilityAction(UNINSTALL,
                launcher.getText(R.string.uninstall_drop_target_label)));
        mActions.put(RECONFIGURE, new AccessibilityAction(RECONFIGURE,
                launcher.getText(R.string.gadget_setup_text)));
        mActions.put(ADD_TO_WORKSPACE, new AccessibilityAction(ADD_TO_WORKSPACE,
                launcher.getText(R.string.action_add_to_workspace)));
        mActions.put(MOVE, new AccessibilityAction(MOVE,
                launcher.getText(R.string.action_move)));
        mActions.put(MOVE_TO_WORKSPACE, new AccessibilityAction(MOVE_TO_WORKSPACE,
                launcher.getText(R.string.action_move_to_workspace)));
        mActions.put(RESIZE, new AccessibilityAction(RESIZE,
                        launcher.getText(R.string.action_resize)));
        mActions.put(DEEP_SHORTCUTS, new AccessibilityAction(DEEP_SHORTCUTS,
                launcher.getText(R.string.action_deep_shortcut)));
        mActions.put(SHORTCUTS_AND_NOTIFICATIONS, new AccessibilityAction(DEEP_SHORTCUTS,
                launcher.getText(R.string.shortcuts_menu_with_notifications_description)));
    }

    public void addAccessibilityAction(int action, int actionLabel) {
        mActions.put(action, new AccessibilityAction(action, mLauncher.getText(actionLabel)));
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(host, info);
        addSupportedActions(host, info, false);
    }

    public void addSupportedActions(View host, AccessibilityNodeInfo info, boolean fromKeyboard) {
        if (!(host.getTag() instanceof ItemInfo)) return;
        ItemInfo item = (ItemInfo) host.getTag();

        // If the request came from keyboard, do not add custom shortcuts as that is already
        // exposed as a direct shortcut
        if (!fromKeyboard && ShortcutUtil.supportsShortcuts(item)) {
            info.addAction(mActions.get(NotificationListener.getInstanceIfConnected() != null
                    ? SHORTCUTS_AND_NOTIFICATIONS : DEEP_SHORTCUTS));
        }

        for (ButtonDropTarget target : mLauncher.getDropTargetBar().getDropTargets()) {
            if (target.supportsAccessibilityDrop(item, host)) {
                info.addAction(mActions.get(target.getAccessibilityAction()));
            }
        }

        // Do not add move actions for keyboard request as this uses virtual nodes.
        if (!fromKeyboard && itemSupportsAccessibleDrag(item)) {
            info.addAction(mActions.get(MOVE));

            if (item.container >= 0) {
                info.addAction(mActions.get(MOVE_TO_WORKSPACE));
            } else if (item instanceof LauncherAppWidgetInfo) {
                if (!getSupportedResizeActions(host, (LauncherAppWidgetInfo) item).isEmpty()) {
                    info.addAction(mActions.get(RESIZE));
                }
            }
        }

        if ((item instanceof AppInfo) || (item instanceof PendingAddItemInfo)) {
            info.addAction(mActions.get(ADD_TO_WORKSPACE));
        }
    }

    private boolean itemSupportsAccessibleDrag(ItemInfo item) {
        if (item instanceof WorkspaceItemInfo) {
            // Support the action unless the item is in a context menu.
            return item.screenId >= 0;
        }
        return (item instanceof LauncherAppWidgetInfo)
                || (item instanceof FolderInfo);
    }

    @Override
    public boolean performAccessibilityAction(View host, int action, Bundle args) {
        if ((host.getTag() instanceof ItemInfo)
                && performAction(host, (ItemInfo) host.getTag(), action)) {
            return true;
        }
        return super.performAccessibilityAction(host, action, args);
    }

    public boolean performAction(final View host, final ItemInfo item, int action) {
        if (action == ACTION_LONG_CLICK && ShortcutUtil.isDeepShortcut(item)) {
            CustomActionsPopup popup = new CustomActionsPopup(mLauncher, host);
            if (popup.canShow()) {
                popup.show();
                return true;
            }
        }
        if (action == MOVE) {
            beginAccessibleDrag(host, item);
        } else if (action == ADD_TO_WORKSPACE) {
            final int[] coordinates = new int[2];
            final int screenId = findSpaceOnWorkspace(item, coordinates);
            mLauncher.getStateManager().goToState(NORMAL, true, new Runnable() {

                @Override
                public void run() {
                    if (item instanceof AppInfo) {
                        WorkspaceItemInfo info = ((AppInfo) item).makeWorkspaceItem();
                        mLauncher.getModelWriter().addItemToDatabase(info,
                                Favorites.CONTAINER_DESKTOP,
                                screenId, coordinates[0], coordinates[1]);

                        ArrayList<ItemInfo> itemList = new ArrayList<>();
                        itemList.add(info);
                        mLauncher.bindItems(itemList, true);
                    } else if (item instanceof PendingAddItemInfo) {
                        PendingAddItemInfo info = (PendingAddItemInfo) item;
                        Workspace workspace = mLauncher.getWorkspace();
                        workspace.snapToPage(workspace.getPageIndexForScreenId(screenId));
                        mLauncher.addPendingItem(info, Favorites.CONTAINER_DESKTOP,
                                screenId, coordinates, info.spanX, info.spanY);
                    }
                    announceConfirmation(R.string.item_added_to_workspace);
                }
            });
            return true;
        } else if (action == MOVE_TO_WORKSPACE) {
            Folder folder = Folder.getOpen(mLauncher);
            folder.close(true);
            WorkspaceItemInfo info = (WorkspaceItemInfo) item;
            folder.getInfo().remove(info, false);

            final int[] coordinates = new int[2];
            final int screenId = findSpaceOnWorkspace(item, coordinates);
            mLauncher.getModelWriter().moveItemInDatabase(info,
                    LauncherSettings.Favorites.CONTAINER_DESKTOP,
                    screenId, coordinates[0], coordinates[1]);

            // Bind the item in next frame so that if a new workspace page was created,
            // it will get laid out.
            new Handler().post(new Runnable() {

                @Override
                public void run() {
                    ArrayList<ItemInfo> itemList = new ArrayList<>();
                    itemList.add(item);
                    mLauncher.bindItems(itemList, true);
                    announceConfirmation(R.string.item_moved);
                }
            });
        } else if (action == RESIZE) {
            final LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) item;
            final IntArray actions = getSupportedResizeActions(host, info);
            CharSequence[] labels = new CharSequence[actions.size()];
            for (int i = 0; i < actions.size(); i++) {
                labels[i] = mLauncher.getText(actions.get(i));
            }

            new AlertDialog.Builder(mLauncher)
                .setTitle(R.string.action_resize)
                .setItems(labels, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        performResizeAction(actions.get(which), host, info);
                        dialog.dismiss();
                    }
                })
                .show();
            return true;
        } else if (action == DEEP_SHORTCUTS) {
            return PopupContainerWithArrow.showForIcon((BubbleTextView) host) != null;
        } else {
            for (ButtonDropTarget dropTarget : mLauncher.getDropTargetBar().getDropTargets()) {
                if (dropTarget.supportsAccessibilityDrop(item, host) &&
                        action == dropTarget.getAccessibilityAction()) {
                    dropTarget.onAccessibilityDrop(host, item);
                    return true;
                }
            }
        }
        return false;
    }

    private IntArray getSupportedResizeActions(View host, LauncherAppWidgetInfo info) {
        IntArray actions = new IntArray();

        AppWidgetProviderInfo providerInfo = ((LauncherAppWidgetHostView) host).getAppWidgetInfo();
        if (providerInfo == null) {
            return actions;
        }

        CellLayout layout = (CellLayout) host.getParent().getParent();
        if ((providerInfo.resizeMode & AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0) {
            if (layout.isRegionVacant(info.cellX + info.spanX, info.cellY, 1, info.spanY) ||
                    layout.isRegionVacant(info.cellX - 1, info.cellY, 1, info.spanY)) {
                actions.add(R.string.action_increase_width);
            }

            if (info.spanX > info.minSpanX && info.spanX > 1) {
                actions.add(R.string.action_decrease_width);
            }
        }

        if ((providerInfo.resizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0) {
            if (layout.isRegionVacant(info.cellX, info.cellY + info.spanY, info.spanX, 1) ||
                    layout.isRegionVacant(info.cellX, info.cellY - 1, info.spanX, 1)) {
                actions.add(R.string.action_increase_height);
            }

            if (info.spanY > info.minSpanY && info.spanY > 1) {
                actions.add(R.string.action_decrease_height);
            }
        }
        return actions;
    }

    @Thunk void performResizeAction(int action, View host, LauncherAppWidgetInfo info) {
        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) host.getLayoutParams();
        CellLayout layout = (CellLayout) host.getParent().getParent();
        layout.markCellsAsUnoccupiedForView(host);

        if (action == R.string.action_increase_width) {
            if (((host.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL)
                    && layout.isRegionVacant(info.cellX - 1, info.cellY, 1, info.spanY))
                    || !layout.isRegionVacant(info.cellX + info.spanX, info.cellY, 1, info.spanY)) {
                lp.cellX --;
                info.cellX --;
            }
            lp.cellHSpan ++;
            info.spanX ++;
        } else if (action == R.string.action_decrease_width) {
            lp.cellHSpan --;
            info.spanX --;
        } else if (action == R.string.action_increase_height) {
            if (!layout.isRegionVacant(info.cellX, info.cellY + info.spanY, info.spanX, 1)) {
                lp.cellY --;
                info.cellY --;
            }
            lp.cellVSpan ++;
            info.spanY ++;
        } else if (action == R.string.action_decrease_height) {
            lp.cellVSpan --;
            info.spanY --;
        }

        layout.markCellsAsOccupiedForView(host);
        Rect sizeRange = new Rect();
        AppWidgetResizeFrame.getWidgetSizeRanges(mLauncher, info.spanX, info.spanY, sizeRange);
        ((LauncherAppWidgetHostView) host).updateAppWidgetSize(null,
                sizeRange.left, sizeRange.top, sizeRange.right, sizeRange.bottom);
        host.requestLayout();
        mLauncher.getModelWriter().updateItemInDatabase(info);
        announceConfirmation(mLauncher.getString(R.string.widget_resized, info.spanX, info.spanY));
    }

    @Thunk void announceConfirmation(int resId) {
        announceConfirmation(mLauncher.getResources().getString(resId));
    }

    @Thunk void announceConfirmation(String confirmation) {
        mLauncher.getDragLayer().announceForAccessibility(confirmation);

    }

    public boolean isInAccessibleDrag() {
        return mDragInfo != null;
    }

    public DragInfo getDragInfo() {
        return mDragInfo;
    }

    /**
     * @param clickedTarget the actual view that was clicked
     * @param dropLocation relative to {@param clickedTarget}. If provided, its center is used
     * as the actual drop location otherwise the views center is used.
     */
    public void handleAccessibleDrop(View clickedTarget, Rect dropLocation,
            String confirmation) {
        if (!isInAccessibleDrag()) return;

        int[] loc = new int[2];
        if (dropLocation == null) {
            loc[0] = clickedTarget.getWidth() / 2;
            loc[1] = clickedTarget.getHeight() / 2;
        } else {
            loc[0] = dropLocation.centerX();
            loc[1] = dropLocation.centerY();
        }

        mLauncher.getDragLayer().getDescendantCoordRelativeToSelf(clickedTarget, loc);
        mLauncher.getDragController().completeAccessibleDrag(loc);

        if (!TextUtils.isEmpty(confirmation)) {
            announceConfirmation(confirmation);
        }
    }

    public void beginAccessibleDrag(View item, ItemInfo info) {
        mDragInfo = new DragInfo();
        mDragInfo.info = info;
        mDragInfo.item = item;
        mDragInfo.dragType = DragType.ICON;
        if (info instanceof FolderInfo) {
            mDragInfo.dragType = DragType.FOLDER;
        } else if (info instanceof LauncherAppWidgetInfo) {
            mDragInfo.dragType = DragType.WIDGET;
        }

        Rect pos = new Rect();
        mLauncher.getDragLayer().getDescendantRectRelativeToSelf(item, pos);
        mLauncher.getDragController().prepareAccessibleDrag(pos.centerX(), pos.centerY());
        mLauncher.getDragController().addDragListener(this);

        DragOptions options = new DragOptions();
        options.isAccessibleDrag = true;
        ItemLongClickListener.beginDrag(item, mLauncher, info, options);
    }

    @Override
    public void onDragStart(DragObject dragObject, DragOptions options) {
        // No-op
    }

    @Override
    public void onDragEnd() {
        mLauncher.getDragController().removeDragListener(this);
        mDragInfo = null;
    }

    /**
     * Find empty space on the workspace and returns the screenId.
     */
    protected int findSpaceOnWorkspace(ItemInfo info, int[] outCoordinates) {
        Workspace workspace = mLauncher.getWorkspace();
        IntArray workspaceScreens = workspace.getScreenOrder();
        int screenId;

        // First check if there is space on the current screen.
        int screenIndex = workspace.getCurrentPage();
        screenId = workspaceScreens.get(screenIndex);
        CellLayout layout = (CellLayout) workspace.getPageAt(screenIndex);

        boolean found = layout.findCellForSpan(outCoordinates, info.spanX, info.spanY);
        screenIndex = 0;
        while (!found && screenIndex < workspaceScreens.size()) {
            screenId = workspaceScreens.get(screenIndex);
            layout = (CellLayout) workspace.getPageAt(screenIndex);
            found = layout.findCellForSpan(outCoordinates, info.spanX, info.spanY);
            screenIndex++;
        }

        if (found) {
            return screenId;
        }

        workspace.addExtraEmptyScreen();
        screenId = workspace.commitExtraEmptyScreen();
        layout = workspace.getScreenWithId(screenId);
        found = layout.findCellForSpan(outCoordinates, info.spanX, info.spanY);

        if (!found) {
            Log.wtf(TAG, "Not enough space on an empty screen");
        }
        return screenId;
    }
}
