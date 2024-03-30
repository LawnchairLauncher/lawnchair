package com.android.launcher3.accessibility;

import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK;

import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;
import static com.android.launcher3.anim.AnimatorListeners.forSuccessCallback;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.IGNORE;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_NOT_PINNABLE;

import android.animation.AnimatorSet;
import android.appwidget.AppWidgetProviderInfo;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.ButtonDropTarget;
import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.PendingAddItemInfo;
import com.android.launcher3.R;
import com.android.launcher3.Workspace;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragOptions.PreDragCondition;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.keyboard.KeyboardDragAndDropView;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.CollectionInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemFactory;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.ArrowPopup;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.views.BubbleTextHolder;
import com.android.launcher3.views.OptionsPopupView;
import com.android.launcher3.views.OptionsPopupView.OptionItem;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.util.WidgetSizes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class LauncherAccessibilityDelegate extends BaseAccessibilityDelegate<Launcher> {

    private static final String TAG = "LauncherAccessibilityDelegate";

    public static final int REMOVE = R.id.action_remove;
    public static final int UNINSTALL = R.id.action_uninstall;
    public static final int DISMISS_PREDICTION = R.id.action_dismiss_prediction;
    public static final int PIN_PREDICTION = R.id.action_pin_prediction;
    public static final int RECONFIGURE = R.id.action_reconfigure;
    public static final int INVALID = -1;
    protected static final int ADD_TO_WORKSPACE = R.id.action_add_to_workspace;
    protected static final int MOVE = R.id.action_move;
    protected static final int MOVE_TO_WORKSPACE = R.id.action_move_to_workspace;
    protected static final int RESIZE = R.id.action_resize;
    public static final int DEEP_SHORTCUTS = R.id.action_deep_shortcuts;

    public LauncherAccessibilityDelegate(Launcher launcher) {
        super(launcher);

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
    }

    @Override
    protected void getSupportedActions(View host, ItemInfo item, List<LauncherAction> out) {
        // If the request came from keyboard, do not add custom shortcuts as that is already
        // exposed as a direct shortcut
        if (ShortcutUtil.supportsShortcuts(item)) {
            out.add(mActions.get(DEEP_SHORTCUTS));
        }

        for (ButtonDropTarget target : mContext.getDropTargetBar().getDropTargets()) {
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

        if (supportAddToWorkSpace(item)) {
            out.add(mActions.get(ADD_TO_WORKSPACE));
        }
    }

    private boolean supportAddToWorkSpace(ItemInfo item) {
        return ((item instanceof AppInfo)
                    && (((AppInfo) item).runtimeStatusFlags & FLAG_NOT_PINNABLE) == 0)
                || ((item instanceof WorkspaceItemInfo)
                    && (((WorkspaceItemInfo) item).runtimeStatusFlags & FLAG_NOT_PINNABLE) == 0)
                || ((item instanceof PendingAddItemInfo)
                    && (((PendingAddItemInfo) item).runtimeStatusFlags & FLAG_NOT_PINNABLE) == 0);
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

    @Override
    protected boolean performAction(final View host, final ItemInfo item, int action,
            boolean fromKeyboard) {
        if (action == ACTION_LONG_CLICK) {
            PreDragCondition dragCondition = null;
            // Long press should be consumed for workspace items, and it should invoke the
            // Shortcuts / Notifications / Actions pop-up menu, and not start a drag as the
            // standard long press path does.
            if (host instanceof BubbleTextView) {
                dragCondition = ((BubbleTextView) host).startLongPressAction();
            } else if (host instanceof BubbleTextHolder) {
                BubbleTextHolder holder = (BubbleTextHolder) host;
                dragCondition = holder.getBubbleText() == null ? null
                        : holder.getBubbleText().startLongPressAction();
            }
            return dragCondition != null;
        } else if (action == MOVE) {
            return beginAccessibleDrag(host, item, fromKeyboard);
        } else if (action == ADD_TO_WORKSPACE) {
            return addToWorkspace(item, true /*accessibility*/, null /*finishCallback*/);
        } else if (action == MOVE_TO_WORKSPACE) {
            return moveToWorkspace(item);
        } else if (action == RESIZE) {
            final LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) item;
            List<OptionItem> actions = getSupportedResizeActions(host, info);
            Rect pos = new Rect();
            mContext.getDragLayer().getDescendantRectRelativeToSelf(host, pos);
            ArrowPopup popup = OptionsPopupView.show(mContext, new RectF(pos), actions, false);
            popup.requestFocus();
            popup.addOnCloseCallback(() -> {
                host.requestFocus();
                host.sendAccessibilityEvent(TYPE_VIEW_FOCUSED);
                host.performAccessibilityAction(ACTION_ACCESSIBILITY_FOCUS, null);
            });
            return true;
        } else if (action == DEEP_SHORTCUTS) {
            BubbleTextView btv = host instanceof BubbleTextView ? (BubbleTextView) host
                    : (host instanceof BubbleTextHolder
                            ? ((BubbleTextHolder) host).getBubbleText() : null);
            return btv != null && PopupContainerWithArrow.showForIcon(btv) != null;
        } else {
            for (ButtonDropTarget dropTarget : mContext.getDropTargetBar().getDropTargets()) {
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
                actions.add(new OptionItem(mContext,
                        R.string.action_increase_width,
                        R.drawable.ic_widget_width_increase,
                        IGNORE,
                        v -> performResizeAction(R.string.action_increase_width, host, info)));
            }

            if (info.spanX > info.minSpanX && info.spanX > 1) {
                actions.add(new OptionItem(mContext,
                        R.string.action_decrease_width,
                        R.drawable.ic_widget_width_decrease,
                        IGNORE,
                        v -> performResizeAction(R.string.action_decrease_width, host, info)));
            }
        }

        if ((providerInfo.resizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0) {
            if (layout.isRegionVacant(info.cellX, info.cellY + info.spanY, info.spanX, 1) ||
                    layout.isRegionVacant(info.cellX, info.cellY - 1, info.spanX, 1)) {
                actions.add(new OptionItem(mContext,
                        R.string.action_increase_height,
                        R.drawable.ic_widget_height_increase,
                        IGNORE,
                        v -> performResizeAction(R.string.action_increase_height, host, info)));
            }

            if (info.spanY > info.minSpanY && info.spanY > 1) {
                actions.add(new OptionItem(mContext,
                        R.string.action_decrease_height,
                        R.drawable.ic_widget_height_decrease,
                        IGNORE,
                        v -> performResizeAction(R.string.action_decrease_height, host, info)));
            }
        }
        return actions;
    }

    private boolean performResizeAction(int action, View host, LauncherAppWidgetInfo info) {
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) host.getLayoutParams();
        CellLayout layout = (CellLayout) host.getParent().getParent();
        layout.markCellsAsUnoccupiedForView(host);

        if (action == R.string.action_increase_width) {
            if (((host.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL)
                    && layout.isRegionVacant(info.cellX - 1, info.cellY, 1, info.spanY))
                    || !layout.isRegionVacant(info.cellX + info.spanX, info.cellY, 1, info.spanY)) {
                lp.setCellX(lp.getCellX() - 1);
                info.cellX --;
            }
            lp.cellHSpan ++;
            info.spanX ++;
        } else if (action == R.string.action_decrease_width) {
            lp.cellHSpan --;
            info.spanX --;
        } else if (action == R.string.action_increase_height) {
            if (!layout.isRegionVacant(info.cellX, info.cellY + info.spanY, info.spanX, 1)) {
                lp.setCellY(lp.getCellY() - 1);
                info.cellY --;
            }
            lp.cellVSpan ++;
            info.spanY ++;
        } else if (action == R.string.action_decrease_height) {
            lp.cellVSpan --;
            info.spanY --;
        }

        layout.markCellsAsOccupiedForView(host);
        WidgetSizes.updateWidgetSizeRanges(((LauncherAppWidgetHostView) host), mContext,
                info.spanX, info.spanY);
        host.requestLayout();
        mContext.getModelWriter().updateItemInDatabase(info);
        announceConfirmation(mContext.getString(R.string.widget_resized, info.spanX, info.spanY));
        return true;
    }

    @Thunk void announceConfirmation(int resId) {
        announceConfirmation(mContext.getResources().getString(resId));
    }

    @Override
    protected boolean beginAccessibleDrag(View item, ItemInfo info, boolean fromKeyboard) {
        if (!itemSupportsAccessibleDrag(info)) {
            return false;
        }

        mDragInfo = new DragInfo();
        mDragInfo.info = info;
        mDragInfo.item = item;
        mDragInfo.dragType = DragType.ICON;
        if (info instanceof FolderInfo) {
            mDragInfo.dragType = DragType.FOLDER;
        } else if (info instanceof AppPairInfo) {
            mDragInfo.dragType = DragType.APP_PAIR;
        } else if (info instanceof LauncherAppWidgetInfo) {
            mDragInfo.dragType = DragType.WIDGET;
        }

        Rect pos = new Rect();
        mContext.getDragLayer().getDescendantRectRelativeToSelf(item, pos);
        mContext.getDragController().addDragListener(this);

        DragOptions options = new DragOptions();
        options.isAccessibleDrag = true;
        options.isKeyboardDrag = fromKeyboard;
        options.simulatedDndStartPoint = new Point(pos.centerX(), pos.centerY());

        if (fromKeyboard) {
            KeyboardDragAndDropView popup = (KeyboardDragAndDropView) mContext.getLayoutInflater()
                    .inflate(R.layout.keyboard_drag_and_drop, mContext.getDragLayer(), false);
            popup.showForIcon(item, info, options);
        } else {
            ItemLongClickListener.beginDrag(item, mContext, info, options);
        }
        return true;
    }

    /**
     * Find empty space on the workspace and returns the screenId.
     */
    protected int findSpaceOnWorkspace(ItemInfo info, int[] outCoordinates) {
        Workspace<?> workspace = mContext.getWorkspace();
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

        workspace.addExtraEmptyScreens();
        IntSet emptyScreenIds = workspace.commitExtraEmptyScreens();
        if (emptyScreenIds.isEmpty()) {
            // Couldn't create extra empty screens for some reason (e.g. Workspace is loading)
            return -1;
        }

        screenId = emptyScreenIds.getArray().get(0);
        layout = workspace.getScreenWithId(screenId);
        found = layout.findCellForSpan(outCoordinates, info.spanX, info.spanY);

        if (!found) {
            Log.wtf(TAG, "Not enough space on an empty screen");
        }
        return screenId;
    }

    /**
     * Functionality to add the item {@link ItemInfo} to the workspace
     * @param item item to be added
     * @param accessibility true if the first item to be added to the workspace
     *     should be focused for accessibility.
     * @param finishCallback Callback which will be run after this item has been added
     *                       and the view has been transitioned to the workspace, or on failure.
     *
     * @return true if the item could be successfully added
     */
    public boolean addToWorkspace(ItemInfo item, boolean accessibility,
            @Nullable Consumer<Boolean> finishCallback) {
        final int[] coordinates = new int[2];
        final int screenId = findSpaceOnWorkspace(item, coordinates);
        if (screenId == -1) {
            if (finishCallback != null) {
                finishCallback.accept(false /*success*/);
            }
            return false;
        }
        mContext.getStateManager().goToState(NORMAL, true, forSuccessCallback(() -> {
            if (item instanceof WorkspaceItemFactory) {
                WorkspaceItemInfo info = ((WorkspaceItemFactory) item).makeWorkspaceItem(mContext);
                mContext.getModelWriter().addItemToDatabase(info,
                        LauncherSettings.Favorites.CONTAINER_DESKTOP,
                        screenId, coordinates[0], coordinates[1]);

                bindItem(item, accessibility, finishCallback);
                announceConfirmation(R.string.item_added_to_workspace);
            } else if (item instanceof PendingAddItemInfo) {
                PendingAddItemInfo info = (PendingAddItemInfo) item;
                if (info instanceof PendingAddWidgetInfo widgetInfo
                        && widgetInfo.bindOptions == null) {
                    widgetInfo.bindOptions = widgetInfo.getDefaultSizeOptions(mContext);
                }
                Workspace<?> workspace = mContext.getWorkspace();
                workspace.post(
                        () -> workspace.snapToPage(workspace.getPageIndexForScreenId(screenId))
                );
                mContext.addPendingItem(info, LauncherSettings.Favorites.CONTAINER_DESKTOP,
                        screenId, coordinates, info.spanX, info.spanY);
            } else if (item instanceof WorkspaceItemInfo) {
                WorkspaceItemInfo info = ((WorkspaceItemInfo) item).clone();
                mContext.getModelWriter().addItemToDatabase(info,
                        LauncherSettings.Favorites.CONTAINER_DESKTOP,
                        screenId, coordinates[0], coordinates[1]);
                bindItem(info, accessibility, finishCallback);
            } else if (item instanceof CollectionInfo ci) {
                Workspace<?> workspace = mContext.getWorkspace();
                workspace.snapToPage(workspace.getPageIndexForScreenId(screenId));
                mContext.getModelWriter().addItemToDatabase(ci,
                        LauncherSettings.Favorites.CONTAINER_DESKTOP, screenId, coordinates[0],
                        coordinates[1]);
                ci.getContents().forEach(member ->
                        mContext.getModelWriter()
                                .addItemToDatabase(member, ci.id, -1, -1, -1));
                bindItem(ci, accessibility, finishCallback);
            }
        }));
        return true;
    }

    private void bindItem(ItemInfo item, boolean focusForAccessibility,
            @Nullable Consumer<Boolean> finishCallback) {
        View view = mContext.getItemInflater().inflateItem(item, mContext.getModelWriter());
        if (view == null) {
            if (finishCallback != null) {
                finishCallback.accept(false /*success*/);
            }
            return;
        }
        AnimatorSet anim = new AnimatorSet();
        anim.addListener(forEndCallback((success) -> {
            if (focusForAccessibility) {
                view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
            }
            if (finishCallback != null) {
                finishCallback.accept(success);
            }
        }));
        mContext.bindInflatedItems(Collections.singletonList(Pair.create(item, view)), anim);
    }

    /**
     * Functionality to move the item {@link ItemInfo} to the workspace
     * @param item item to be moved
     *
     * @return true if the item could be successfully added
     */
    public boolean moveToWorkspace(ItemInfo item) {
        Folder folder = Folder.getOpen(mContext);
        folder.close(true);
        WorkspaceItemInfo info = (WorkspaceItemInfo) item;
        folder.getInfo().remove(info, false);

        final int[] coordinates = new int[2];
        final int screenId = findSpaceOnWorkspace(item, coordinates);
        if (screenId == -1) {
            return false;
        }
        mContext.getModelWriter().moveItemInDatabase(info,
                LauncherSettings.Favorites.CONTAINER_DESKTOP,
                screenId, coordinates[0], coordinates[1]);

        // Bind the item in next frame so that if a new workspace page was created,
        // it will get laid out.
        new Handler().post(() -> {
            mContext.bindItems(Collections.singletonList(item), true);
            announceConfirmation(R.string.item_moved);
        });
        return true;
    }
}
