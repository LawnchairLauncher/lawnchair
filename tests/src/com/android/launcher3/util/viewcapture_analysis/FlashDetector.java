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

import static org.junit.Assert.assertTrue;

import com.android.launcher3.util.viewcapture_analysis.ViewCaptureAnalyzer.AnalysisNode;

import java.util.List;

/**
 * Anomaly detector that triggers an error when a view flashes, i.e. appears or disappears for a too
 * short period of time.
 */
final class FlashDetector extends AnomalyDetector {
    // Maximum time period of a view visibility or invisibility that is recognized as a flash.
    private static final int FLASH_DURATION_MS = 300;

    // Commonly used parts of the paths to ignore.
    private static final String CONTENT = "DecorView|LinearLayout|FrameLayout:id/content|";
    private static final String DRAG_LAYER =
            CONTENT + "LauncherRootView:id/launcher|DragLayer:id/drag_layer|";
    private static final String RECENTS_DRAG_LAYER =
            CONTENT + "LauncherRootView:id/launcher|RecentsDragLayer:id/drag_layer|";

    private static final IgnoreNode IGNORED_NODES_ROOT = buildIgnoreNodesTree(List.of(
            CONTENT + "LauncherRootView:id/launcher|FloatingIconView",
            DRAG_LAYER + "LauncherRecentsView:id/overview_panel|TaskView",
            DRAG_LAYER + "LauncherRecentsView:id/overview_panel|ClearAllButton:id/clear_all",
            DRAG_LAYER
                    + "LauncherAllAppsContainerView:id/apps_view|AllAppsRecyclerView:id"
                    + "/apps_list_view|BubbleTextView:id/icon",
            CONTENT
                    + "SimpleDragLayer:id/add_item_drag_layer|AddItemWidgetsBottomSheet:id"
                    + "/add_item_bottom_sheet|LinearLayout:id/add_item_bottom_sheet_content"
                    + "|ScrollView:id/widget_preview_scroll_view|WidgetCell:id/widget_cell"
                    + "|WidgetCellPreview:id/widget_preview_container|WidgetImageView:id"
                    + "/widget_preview",
            CONTENT
                    + "SimpleDragLayer:id/add_item_drag_layer|AddItemWidgetsBottomSheet:id"
                    + "/add_item_bottom_sheet|LinearLayout:id/add_item_bottom_sheet_content"
                    + "|ScrollView:id/widget_preview_scroll_view|WidgetCell:id/widget_cell"
                    + "|WidgetCellPreview:id/widget_preview_container|ImageView:id/widget_badge",
            RECENTS_DRAG_LAYER + "FallbackRecentsView:id/overview_panel|TaskView",
            RECENTS_DRAG_LAYER
                    + "FallbackRecentsView:id/overview_panel|ClearAllButton:id/clear_all",
            DRAG_LAYER + "SearchContainerView:id/apps_view",
            DRAG_LAYER + "LauncherDragView",
            DRAG_LAYER + "FloatingTaskView|FloatingTaskThumbnailView:id/thumbnail",
            DRAG_LAYER
                    + "WidgetsFullSheet|SpringRelativeLayout:id/container|WidgetsRecyclerView:id"
                    + "/primary_widgets_list_view|WidgetsListHeader:id/widgets_list_header",
            DRAG_LAYER
                    + "WidgetsTwoPaneSheet|SpringRelativeLayout:id/container|LinearLayout:id"
                    + "/linear_layout_container|FrameLayout:id/recycler_view_container"
                    + "|FrameLayout:id/widgets_two_pane_sheet_recyclerview|WidgetsRecyclerView:id"
                    + "/primary_widgets_list_view|WidgetsListHeader:id/widgets_list_header",
            DRAG_LAYER + "NexusOverviewActionsView:id/overview_actions_view"
    ));

    // Per-AnalysisNode data that's specific to this detector.
    private static class NodeData {
        public boolean ignoreFlashes;

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

        // If the parent view ignores flashes, its descendants will too.
        final boolean parentIgnoresFlashes = info.parent != null && getNodeData(
                info.parent).ignoreFlashes;
        if (parentIgnoresFlashes) {
            nodeData.ignoreFlashes = true;
            return;
        }

        // Parent view doesn't ignore flashes.
        // Initialize this AnalysisNode's ignore-node with the corresponding child of the
        // ignore-node of the parent, if present.
        final IgnoreNode parentIgnoreNode = info.parent != null
                ? getNodeData(info.parent).ignoreNode
                : IGNORED_NODES_ROOT;
        nodeData.ignoreNode = parentIgnoreNode != null
                ? parentIgnoreNode.children.get(info.nodeIdentity) : null;
        // AnalysisNode will be ignored if the corresponding ignore-node is a leaf.
        nodeData.ignoreFlashes =
                nodeData.ignoreNode != null && nodeData.ignoreNode.children.isEmpty();
    }

    @Override
    String detectAnomalies(AnalysisNode oldInfo, AnalysisNode newInfo, int frameN,
            long frameTimeNs, int windowSizePx) {
        // Should we check when a view was visible for a short period, then its alpha became 0?
        // Then 'lastVisible' time should be the last one still visible?
        // Check only transitions of alpha between 0 and 1?

        // If this is the first time ever when we see the view, there have been no flashes yet.
        if (oldInfo == null) return null;

        // A flash requires a view to go from the full visibility to no-visibility and then back,
        // or vice versa.
        // If the last time the view was seen before the current frame, it didn't have full
        // visibility; no flash can possibly be detected at the current frame.
        if (oldInfo.alpha < 1) return null;

        final AnalysisNode latestInfo = newInfo != null ? newInfo : oldInfo;
        final NodeData nodeData = getNodeData(latestInfo);
        if (nodeData.ignoreFlashes) return null;

        // Once the view becomes invisible, see for how long it was visible prior to that. If it
        // was visible only for a short interval of time, it's a flash.
        if (
            // View is invisible in the current frame
                newInfo == null
                        // When the view became visible last time, it was a transition from
                        // no-visibility to full visibility.
                        && oldInfo.timeBecameVisibleNs != -1) {
            final long wasVisibleTimeMs = (frameTimeNs - oldInfo.timeBecameVisibleNs) / 1000000;

            if (wasVisibleTimeMs <= FLASH_DURATION_MS) {
                nodeData.ignoreFlashes = true; // No need to report flashes in children.
                return
                        String.format(
                                "View was visible for a too short period of time %dms, which is a"
                                        + " flash",
                                wasVisibleTimeMs
                        );
            }
        }

        // Once a view becomes visible, see for how long it was invisible prior to that. If it
        // was invisible only for a short interval of time, it's a flash.
        if (
            // The view is fully visible now
                newInfo != null && newInfo.alpha >= 1
                        // The view wasn't visible in the previous frame
                        && frameN != oldInfo.frameN + 1) {
            // We can assert the below condition because at this point, we know that
            // oldInfo.alpha >= 1, i.e. it disappeared abruptly.
            assertTrue("oldInfo.timeBecameInvisibleNs must not be -1",
                    oldInfo.timeBecameInvisibleNs != -1);

            final long wasInvisibleTimeMs = (frameTimeNs - oldInfo.timeBecameInvisibleNs) / 1000000;
            if (wasInvisibleTimeMs <= FLASH_DURATION_MS) {
                nodeData.ignoreFlashes = true; // No need to report flashes in children.
                return
                        String.format(
                                "View was invisible for a too short period of time %dms, which "
                                        + "is a flash",
                                wasInvisibleTimeMs);
            }
        }
        return null;
    }
}
