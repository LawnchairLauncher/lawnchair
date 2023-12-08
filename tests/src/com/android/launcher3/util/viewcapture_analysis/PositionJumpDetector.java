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
package com.android.launcher3.util.viewcapture_analysis;

import com.android.launcher3.util.viewcapture_analysis.ViewCaptureAnalyzer.AnalysisNode;

import java.util.List;

/**
 * Anomaly detector that triggers an error when a view position jumps.
 */
final class PositionJumpDetector extends AnomalyDetector {
    // Maximum allowed jump in "milliwindows", i.e. a 1/1000's of the maximum of the window
    // dimensions.
    private static final float JUMP_MIW = 250;

    private static final String[] BORDER_NAMES = {"left", "top", "right", "bottom"};

    // Commonly used parts of the paths to ignore.
    private static final String CONTENT = "DecorView|LinearLayout|FrameLayout:id/content|";
    private static final String DRAG_LAYER =
            CONTENT + "LauncherRootView:id/launcher|DragLayer:id/drag_layer|";
    private static final String RECENTS_DRAG_LAYER =
            CONTENT + "LauncherRootView:id/launcher|RecentsDragLayer:id/drag_layer|";

    private static final IgnoreNode IGNORED_NODES_ROOT = buildIgnoreNodesTree(List.of(
            DRAG_LAYER + "SearchContainerView:id/apps_view",
            DRAG_LAYER + "AppWidgetResizeFrame",
            DRAG_LAYER + "LauncherAllAppsContainerView:id/apps_view",
            CONTENT
                    + "AddItemDragLayer:id/add_item_drag_layer|AddItemWidgetsBottomSheet:id"
                    + "/add_item_bottom_sheet|LinearLayout:id/add_item_bottom_sheet_content",
            DRAG_LAYER + "WidgetsTwoPaneSheet|SpringRelativeLayout:id/container",
            DRAG_LAYER + "WidgetsFullSheet|SpringRelativeLayout:id/container",
            DRAG_LAYER + "LauncherDragView",
            RECENTS_DRAG_LAYER + "FallbackRecentsView:id/overview_panel|TaskView",
            CONTENT + "LauncherRootView:id/launcher|FloatingIconView",
            DRAG_LAYER + "FloatingTaskView",
            DRAG_LAYER + "LauncherRecentsView:id/overview_panel"
    ));

    // Per-AnalysisNode data that's specific to this detector.
    private static class NodeData {
        public boolean ignoreJumps;

        // If ignoreNode is null, then this AnalysisNode node will be ignored if its parent is
        // ignored.
        // Otherwise, this AnalysisNode will be ignored if ignoreNode is a leaf i.e. has no
        // children.
        public IgnoreNode ignoreNode;
    }

    private NodeData getNodeData(AnalysisNode info) {
        return (NodeData) info.detectorsData[detectorOrdinal];
    }

    @Override
    void initializeNode(AnalysisNode info) {
        final NodeData nodeData = new NodeData();
        info.detectorsData[detectorOrdinal] = nodeData;

        // If the parent view ignores jumps, its descendants will too.
        final boolean parentIgnoresJumps = info.parent != null && getNodeData(
                info.parent).ignoreJumps;
        if (parentIgnoresJumps) {
            nodeData.ignoreJumps = true;
            return;
        }

        // Parent view doesn't ignore jumps.
        // Initialize this AnalysisNode's ignore-node with the corresponding child of the
        // ignore-node of the parent, if present.
        final IgnoreNode parentIgnoreNode = info.parent != null
                ? getNodeData(info.parent).ignoreNode
                : IGNORED_NODES_ROOT;
        nodeData.ignoreNode = parentIgnoreNode != null
                ? parentIgnoreNode.children.get(info.nodeIdentity) : null;
        // AnalysisNode will be ignored if the corresponding ignore-node is a leaf.
        nodeData.ignoreJumps =
                nodeData.ignoreNode != null && nodeData.ignoreNode.children.isEmpty();
    }

    @Override
    String detectAnomalies(AnalysisNode oldInfo, AnalysisNode newInfo, int frameN,
            long frameTimeNs, int windowSizePx) {
        // If the view is not present in the current frame, there can't be a jump detected in the
        // current frame.
        if (newInfo == null) return null;

        // We only detect position jumps if the view was visible in the previous frame.
        if (oldInfo == null || frameN != oldInfo.frameN + 1) return null;

        final NodeData newNodeData = getNodeData(newInfo);
        if (newNodeData.ignoreJumps) return null;

        final float[] positionDiffs = {
                newInfo.left - oldInfo.left,
                newInfo.top - oldInfo.top,
                newInfo.right - oldInfo.right,
                newInfo.bottom - oldInfo.bottom
        };

        for (int i = 0; i < 4; ++i) {
            final float positionDiffAbs = Math.abs(positionDiffs[i]);
            if (positionDiffAbs * 1000 > JUMP_MIW * windowSizePx) {
                newNodeData.ignoreJumps = true;
                return String.format("Position jump: %s jumped by %s",
                        BORDER_NAMES[i], positionDiffAbs);
            }
        }
        return null;
    }
}
