/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.celllayout;

import android.view.View;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.views.DoubleShadowBubbleTextView;

import java.util.ArrayList;

public class CellLayoutTestUtils {

    public static ArrayList<CellLayoutBoard> workspaceToBoards(Launcher launcher) {
        ArrayList<CellLayoutBoard> boards = new ArrayList<>();
        int widgetCount = 0;
        for (CellLayout cellLayout : launcher.getWorkspace().mWorkspaceScreens) {
            CellLayoutBoard board = new CellLayoutBoard();
            int count = cellLayout.getShortcutsAndWidgets().getChildCount();
            for (int i = 0; i < count; i++) {
                View callView = cellLayout.getShortcutsAndWidgets().getChildAt(i);
                CellLayoutLayoutParams params =
                        (CellLayoutLayoutParams) callView.getLayoutParams();
                // is icon
                if (callView instanceof DoubleShadowBubbleTextView) {
                    board.addIcon(params.getCellX(), params.getCellY());
                } else {
                    // is widget
                    board.addWidget(params.getCellX(), params.getCellY(), params.cellHSpan,
                            params.cellVSpan, (char) ('A' + widgetCount));
                    widgetCount++;
                }
            }
            boards.add(board);
        }
        return boards;
    }
}
