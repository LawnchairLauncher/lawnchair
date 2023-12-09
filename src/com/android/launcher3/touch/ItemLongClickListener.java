/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.touch;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.EDIT_MODE;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_ITEM_LONG_PRESSED;

import android.view.View;
import android.view.View.OnLongClickListener;

import com.android.launcher3.CellLayout;
import com.android.launcher3.DropTarget;
import com.android.launcher3.Launcher;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.logging.StatsLogManager.StatsLogger;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.views.BubbleTextHolder;
import com.android.launcher3.widget.LauncherAppWidgetHostView;

/**
 * Class to handle long-clicks on workspace items and start drag as a result.
 */
public class ItemLongClickListener {

    public static final OnLongClickListener INSTANCE_WORKSPACE =
            ItemLongClickListener::onWorkspaceItemLongClick;

    public static final OnLongClickListener INSTANCE_ALL_APPS =
            ItemLongClickListener::onAllAppsItemLongClick;

    private static boolean onWorkspaceItemLongClick(View v) {
        if (v instanceof LauncherAppWidgetHostView) {
            TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "Widgets.onLongClick");
        } else {
            TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "onWorkspaceItemLongClick");
        }
        Launcher launcher = Launcher.getLauncher(v.getContext());
        if (!canStartDrag(launcher)) return false;
        if (!launcher.isInState(NORMAL)
                && !launcher.isInState(OVERVIEW)
                && !launcher.isInState(EDIT_MODE)) {
            return false;
        }
        if (!(v.getTag() instanceof ItemInfo)) return false;

        launcher.setWaitingForResult(null);
        beginDrag(v, launcher, (ItemInfo) v.getTag(), new DragOptions());
        return true;
    }

    public static void beginDrag(View v, Launcher launcher, ItemInfo info,
            DragOptions dragOptions) {
        if (info.container >= 0) {
            Folder folder = Folder.getOpen(launcher);
            if (folder != null) {
                if (!folder.getIconsInReadingOrder().contains(v)) {
                    folder.close(true);
                } else {
                    folder.startDrag(v, dragOptions);
                    return;
                }
            }
        }

        CellLayout.CellInfo longClickCellInfo = new CellLayout.CellInfo(v, info,
                launcher.getCellPosMapper().mapModelToPresenter(info));
        launcher.getWorkspace().startDrag(longClickCellInfo, dragOptions);
    }

    private static boolean onAllAppsItemLongClick(View view) {
        TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "onAllAppsItemLongClick");
        view.cancelLongPress();
        View v = (view instanceof BubbleTextHolder)
                ? ((BubbleTextHolder) view).getBubbleText()
                : view;
        Launcher launcher = Launcher.getLauncher(v.getContext());
        if (!canStartDrag(launcher)) return false;
        // When we have exited all apps or are in transition, disregard long clicks
        if (!launcher.isInState(ALL_APPS) && !launcher.isInState(OVERVIEW)) return false;
        if (launcher.getWorkspace().isSwitchingState()) return false;

        StatsLogger logger = launcher.getStatsLogManager().logger();
        if (v.getTag() instanceof ItemInfo) {
            logger.withItemInfo((ItemInfo) v.getTag());
        }
        logger.log(LAUNCHER_ALLAPPS_ITEM_LONG_PRESSED);

        // Start the drag
        final DragController dragController = launcher.getDragController();
        dragController.addDragListener(new DragController.DragListener() {
            @Override
            public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
                v.setVisibility(INVISIBLE);
            }

            @Override
            public void onDragEnd() {
                v.setVisibility(VISIBLE);
                dragController.removeDragListener(this);
            }
        });

        launcher.getWorkspace().beginDragShared(v, launcher.getAppsView(), new DragOptions());
        return false;
    }

    public static boolean canStartDrag(Launcher launcher) {
        if (launcher == null) {
            return false;
        }
        // We prevent dragging when we are loading the workspace as it is possible to pick up a view
        // that is subsequently removed from the workspace in startBinding().
        if (launcher.isWorkspaceLocked()) return false;
        // Return early if an item is already being dragged (e.g. when long-pressing two shortcuts)
        if (launcher.getDragController().isDragging()) return false;

        return true;
    }
}
