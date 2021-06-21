package com.android.launcher3.accessibility;

import static android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK;

import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.anim.AnimatorListeners.forSuccessCallback;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.IGNORE;

import android.appwidget.AppWidgetProviderInfo;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.ButtonDropTarget;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.PendingAddItemInfo;
import com.android.launcher3.R;
import com.android.launcher3.Workspace;
import com.android.launcher3.dragndrop.DragController.DragListener;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.keyboard.KeyboardDragAndDropView;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.popup.ArrowPopup;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.views.OptionsPopupView;
import com.android.launcher3.views.OptionsPopupView.OptionItem;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.launcher3.widget.util.WidgetSizes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LauncherAccessibilityDelegate extends AccessibilityDelegate implements DragListener {

    private static final String TAG = "LauncherAccessibilityDelegate";

    public static final int REMOVE = R.id.action_remove;
    public static final int UNINSTALL = R.id.action_uninstall;
    public static final int DISMISS_PREDICTION = R.id.action_dismiss_prediction;
    public static final int PIN_PREDICTION = R.id.action_pin_prediction;
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

    protected final SparseArray<LauncherAction> mActions = new SparseArray<>();
    protected final Launcher mLauncher;

    private DragInfo mDragInfo = null;

    public LauncherAccessibilityDelegate(Launcher launcher) {
        mLauncher = launcher;

        mActions.put(REMOVE, new LauncherAction(
                REMOVE, R.string.remove_drop_target_label, KeyEvent.KEYCODE_X));
        mActions.put(UNINSTALL, new LauncherAction(
                UNINSTALL, R.string.uninstall_drop_target_label, KeyEvent.KEYCODE_U));
        mActions.put(DISMISS_PREDICTION, new LauncherAction(DISMISS_PREDICTION,
                R.string.dismiss_prediction_label, KeyEvent.KEYCODE_X));
        mActions.put(RECONFIGURE, new LauncherAction(
                RECONFIGURE, R.string.gadget_setup_text, KeyEvent.KEYCODE_E));
        mActions.put(ADD_TO_WORKSPACE, new LauncherAction(
                ADD_TO_WORKSPACE, R.string.action_add_to_workspace, KeyEvent.KEYCODE_P));
        mActions.put(MOVE, new LauncherAction(
                MOVE, R.string.action_move, KeyEvent.KEYCODE_M));
        mActions.put(MOVE_TO_WORKSPACE, new LauncherAction(MOVE_TO_WORKSPACE,
                R.string.action_move_to_workspace, KeyEvent.KEYCODE_P));
        mActions.put(RESIZE, new LauncherAction(
                RESIZE, R.string.action_resize, KeyEvent.KEYCODE_R));
        mActions.put(DEEP_SHORTCUTS, new LauncherAction(DEEP_SHORTCUTS,
                R.string.action_deep_shortcut, KeyEvent.KEYCODE_S));
        mActions.put(SHORTCUTS_AND_NOTIFICATIONS, new LauncherAction(DEEP_SHORTCUTS,
                R.string.shortcuts_menu_with_notifications_description, KeyEvent.KEYCODE_S));
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(host, info);
        if (host.getTag() instanceof ItemInfo) {
            ItemInfo item = (ItemInfo) host.getTag();

            List<LauncherAction> actions = new ArrayList<>();
            getSupportedActions(host, item, actions);
            actions.forEach(la -> info.addAction(la.accessibilityAction));

            if (!itemSupportsLongClick(host, item)) {
                info.setLongClickable(false);
                info.removeAction(AccessibilityAction.ACTION_LONG_CLICK);
            }
        }
    }

    /**
     * Adds all the accessibility actions that can be handled.
     */
    protected void getSupportedActions(View host, ItemInfo item, List<LauncherAction> out) {
        // If the request came from keyboard, do not add custom shortcuts as that is already
        // exposed as a direct shortcut
        if (ShortcutUtil.supportsShortcuts(item)) {
            out.add(mActions.get(NotificationListener.getInstanceIfConnected() != null
                    ? SHORTCUTS_AND_NOTIFICATIONS : DEEP_SHORTCUTS));
        }

        for (ButtonDropTarget target : mLauncher.getDropTargetBar().getDropTargets()) {
            if (target.supportsAccessibilityDrop(item, host)) {
                out.add(mActions.get(target.getAccessibilityAction()));
            }
        }

        // Do not add move actions for keyboard request as this uses virtual nodes.
        if (itemSupportsAccessibleDrag(item)) {
            out.add(mActions.get(MOVE));

            if (item.container >= 0) {
                out.add(mActions.get(MOVE_TO_WORKSPACE));
            } else if (item instanceof LauncherAppWidgetInfo) {
                if (!getSupportedResizeActions(host, (LauncherAppWidgetInfo) item).isEmpty()) {
                    out.add(mActions.get(RESIZE));
                }
            }
        }

        if ((item instanceof AppInfo) || (item instanceof PendingAddItemInfo)) {
            out.add(mActions.get(ADD_TO_WORKSPACE));
        }
    }

    /**
     * Returns all the accessibility actions that can be handled by the host.
     */
    public static List<LauncherAction> getSupportedActions(Launcher launcher, View host) {
        if (host == null || !(host.getTag() instanceof  ItemInfo)) {
            return Collections.emptyList();
        }
        PopupContainerWithArrow container = PopupContainerWithArrow.getOpen(launcher);
        LauncherAccessibilityDelegate delegate = container != null
                ? container.getAccessibilityDelegate() : launcher.getAccessibilityDelegate();
        List<LauncherAction> result = new ArrayList<>();
        delegate.getSupportedActions(host, (ItemInfo) host.getTag(), result);
        return result;
    }

    private boolean itemSupportsLongClick(View host, ItemInfo info) {
        return PopupContainerWithArrow.canShow(host, info);
    }

    private boolean itemSupportsAccessibleDrag(ItemInfo item) {
        if (item instanceof WorkspaceItemInfo) {
            // Support the action unless the item is in a context menu.
            return item.screenId >= 0 && item.container != Favorites.CONTAINER_HOTSEAT_PREDICTION;
        }
        return (item instanceof LauncherAppWidgetInfo)
                || (item instanceof FolderInfo);
    }

    @Override
    public boolean performAccessibilityAction(View host, int action, Bundle args) {
        if ((host.getTag() instanceof ItemInfo)
                && performAction(host, (ItemInfo) host.getTag(), action, false)) {
            return true;
        }
        return super.performAccessibilityAction(host, action, args);
    }

    /**
     * Performs the provided action on the host
     */
    protected boolean performAction(final View host, final ItemInfo item, int action,
            boolean fromKeyboard) {
        if (action == ACTION_LONG_CLICK) {
            if (PopupContainerWithArrow.canShow(host, item)) {
                // Long press should be consumed for workspace items, and it should invoke the
                // Shortcuts / Notifications / Actions pop-up menu, and not start a drag as the
                // standard long press path does.
                PopupContainerWithArrow.showForIcon((BubbleTextView) host);
                return true;
            }
        } else if (action == MOVE) {
            return beginAccessibleDrag(host, item, fromKeyboard);
        } else if (action == ADD_TO_WORKSPACE) {
            final int[] coordinates = new int[2];
            final int screenId = findSpaceOnWorkspace(item, coordinates);
            mLauncher.getStateManager().goToState(NORMAL, true, forSuccessCallback(() -> {
                if (item instanceof AppInfo) {
                    WorkspaceItemInfo info = ((AppInfo) item).makeWorkspaceItem();
                    mLauncher.getModelWriter().addItemToDatabase(info,
                            Favorites.CONTAINER_DESKTOP,
                            screenId, coordinates[0], coordinates[1]);

                    mLauncher.bindItems(
                            Collections.singletonList(info),
                            /* forceAnimateIcons= */ true,
                            /* focusFirstItemForAccessibility= */ true);
                    announceConfirmation(R.string.item_added_to_workspace);
                } else if (item instanceof PendingAddItemInfo) {
                    PendingAddItemInfo info = (PendingAddItemInfo) item;
                    Workspace workspace = mLauncher.getWorkspace();
                    workspace.snapToPage(workspace.getPageIndexForScreenId(screenId));
                    mLauncher.addPendingItem(info, Favorites.CONTAINER_DESKTOP,
                            screenId, coordinates, info.spanX, info.spanY);
                }
            }));
            return true;
        } else if (action == MOVE_TO_WORKSPACE) {
            Folder folder = Folder.getOpen(mLauncher);
            folder.close(true);
            WorkspaceItemInfo info = (WorkspaceItemInfo) item;
            folder.getInfo().remove(info, false);

            final int[] coordinates = new int[2];
            final int screenId = findSpaceOnWorkspace(item, coordinates);
            mLauncher.getModelWriter().moveItemInDatabase(info,
                    Favorites.CONTAINER_DESKTOP,
                    screenId, coordinates[0], coordinates[1]);

            // Bind the item in next frame so that if a new workspace page was created,
            // it will get laid out.
            new Handler().post(() -> {
                mLauncher.bindItems(Collections.singletonList(item), true);
                announceConfirmation(R.string.item_moved);
            });
            return true;
        } else if (action == RESIZE) {
            final LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) item;
            List<OptionItem> actions = getSupportedResizeActions(host, info);
            Rect pos = new Rect();
            mLauncher.getDragLayer().getDescendantRectRelativeToSelf(host, pos);
            ArrowPopup popup = OptionsPopupView.show(mLauncher, new RectF(pos), actions, false);
            popup.requestFocus();
            popup.setOnCloseCallback(host::requestFocus);
            return true;
        } else if (action == DEEP_SHORTCUTS || action == SHORTCUTS_AND_NOTIFICATIONS) {
            return PopupContainerWithArrow.showForIcon((BubbleTextView) host) != null;
        } else {
            for (ButtonDropTarget dropTarget : mLauncher.getDropTargetBar().getDropTargets()) {
                if (dropTarget.supportsAccessibilityDrop(item, host)
                        && action == dropTarget.getAccessibilityAction()) {
                    dropTarget.onAccessibilityDrop(host, item);
                    return true;
                }
            }
        }
        return false;
    }

    private List<OptionItem> getSupportedResizeActions(View host, LauncherAppWidgetInfo info) {
        List<OptionItem> actions = new ArrayList<>();
        AppWidgetProviderInfo providerInfo = ((LauncherAppWidgetHostView) host).getAppWidgetInfo();
        if (providerInfo == null) {
            return actions;
        }

        CellLayout layout;
        if (host.getParent() instanceof DragView) {
            layout = (CellLayout) ((DragView) host.getParent()).getContentViewParent().getParent();
        } else {
            layout = (CellLayout) host.getParent().getParent();
        }
        if ((providerInfo.resizeMode & AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0) {
            if (layout.isRegionVacant(info.cellX + info.spanX, info.cellY, 1, info.spanY) ||
                    layout.isRegionVacant(info.cellX - 1, info.cellY, 1, info.spanY)) {
                actions.add(new OptionItem(mLauncher,
                        R.string.action_increase_width,
                        R.drawable.ic_widget_width_increase,
                        IGNORE,
                        v -> performResizeAction(R.string.action_increase_width, host, info)));
            }

            if (info.spanX > info.minSpanX && info.spanX > 1) {
                actions.add(new OptionItem(mLauncher,
                        R.string.action_decrease_width,
                        R.drawable.ic_widget_width_decrease,
                        IGNORE,
                        v -> performResizeAction(R.string.action_decrease_width, host, info)));
            }
        }

        if ((providerInfo.resizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0) {
            if (layout.isRegionVacant(info.cellX, info.cellY + info.spanY, info.spanX, 1) ||
                    layout.isRegionVacant(info.cellX, info.cellY - 1, info.spanX, 1)) {
                actions.add(new OptionItem(mLauncher,
                        R.string.action_increase_height,
                        R.drawable.ic_widget_height_increase,
                        IGNORE,
                        v -> performResizeAction(R.string.action_increase_height, host, info)));
            }

            if (info.spanY > info.minSpanY && info.spanY > 1) {
                actions.add(new OptionItem(mLauncher,
                        R.string.action_decrease_height,
                        R.drawable.ic_widget_height_decrease,
                        IGNORE,
                        v -> performResizeAction(R.string.action_decrease_height, host, info)));
            }
        }
        return actions;
    }

    private boolean performResizeAction(int action, View host, LauncherAppWidgetInfo info) {
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
        WidgetSizes.updateWidgetSizeRanges(((LauncherAppWidgetHostView) host), mLauncher,
                info.spanX, info.spanY);
        host.requestLayout();
        mLauncher.getModelWriter().updateItemInDatabase(info);
        announceConfirmation(mLauncher.getString(R.string.widget_resized, info.spanX, info.spanY));
        return true;
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

    private boolean beginAccessibleDrag(View item, ItemInfo info, boolean fromKeyboard) {
        if (!itemSupportsAccessibleDrag(info)) {
            return false;
        }

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
        mLauncher.getDragController().addDragListener(this);

        DragOptions options = new DragOptions();
        options.isAccessibleDrag = true;
        options.isKeyboardDrag = fromKeyboard;
        options.simulatedDndStartPoint = new Point(pos.centerX(), pos.centerY());

        if (fromKeyboard) {
            KeyboardDragAndDropView popup = (KeyboardDragAndDropView) mLauncher.getLayoutInflater()
                    .inflate(R.layout.keyboard_drag_and_drop, mLauncher.getDragLayer(), false);
            popup.showForIcon(item, info, options);
        } else {
            ItemLongClickListener.beginDrag(item, mLauncher, info, options);
        }
        return true;
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

    public class LauncherAction {
        public final int keyCode;
        public final AccessibilityAction accessibilityAction;

        private final LauncherAccessibilityDelegate mDelegate;

        public LauncherAction(int id, int labelRes, int keyCode) {
            this.keyCode = keyCode;
            accessibilityAction = new AccessibilityAction(id, mLauncher.getString(labelRes));
            mDelegate = LauncherAccessibilityDelegate.this;
        }

        /**
         * Invokes the action for the provided host
         */
        public boolean invokeFromKeyboard(View host) {
            if (host != null && host.getTag() instanceof ItemInfo) {
                return mDelegate.performAction(
                        host, (ItemInfo) host.getTag(), accessibilityAction.getId(), true);
            } else {
                return false;
            }
        }
    }
}
