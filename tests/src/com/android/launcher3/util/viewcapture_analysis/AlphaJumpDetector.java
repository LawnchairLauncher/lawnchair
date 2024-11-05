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
 * Anomaly detector that triggers an error when alpha of a view changes too rapidly.
 * Invisible views are treated as if they had zero alpha.
 */
final class AlphaJumpDetector extends AnomalyDetector {
    // Commonly used parts of the paths to ignore.
    private static final String CONTENT = "DecorView|LinearLayout|FrameLayout:id/content|";
    private static final String DRAG_LAYER =
            CONTENT + "LauncherRootView:id/launcher|DragLayer:id/drag_layer|";
    private static final String RECENTS_DRAG_LAYER =
            CONTENT + "LauncherRootView:id/launcher|RecentsDragLayer:id/drag_layer|";

    private static final IgnoreNode IGNORED_NODES_ROOT = buildIgnoreNodesTree(List.of(
            CONTENT
                    + "SimpleDragLayer:id/add_item_drag_layer|AddItemWidgetsBottomSheet:id"
                    + "/add_item_bottom_sheet|LinearLayout:id/add_item_bottom_sheet_content"
                    + "|ScrollView:id/widget_preview_scroll_view|WidgetCell:id/widget_cell"
                    + "|WidgetCellPreview:id/widget_preview_container|ImageView:id/widget_badge",
            CONTENT
                    + "SimpleDragLayer:id/add_item_drag_layer|AddItemWidgetsBottomSheet:id"
                    + "/add_item_bottom_sheet|LinearLayout:id/add_item_bottom_sheet_content"
                    + "|ScrollView:id/widget_preview_scroll_view|WidgetCell:id/widget_cell"
                    + "|WidgetCellPreview:id/widget_preview_container|WidgetCell$1|FrameLayout"
                    + "|ImageView:id/icon",
            CONTENT + "SimpleDragLayer:id/add_item_drag_layer|View",
            DRAG_LAYER
                    + "AppWidgetResizeFrame|FrameLayout|ImageButton:id/widget_reconfigure_button",
            DRAG_LAYER
                    + "AppWidgetResizeFrame|FrameLayout|ImageView:id/widget_resize_bottom_handle",
            DRAG_LAYER + "AppWidgetResizeFrame|FrameLayout|ImageView:id/widget_resize_frame",
            DRAG_LAYER + "AppWidgetResizeFrame|FrameLayout|ImageView:id/widget_resize_left_handle",
            DRAG_LAYER + "AppWidgetResizeFrame|FrameLayout|ImageView:id/widget_resize_right_handle",
            DRAG_LAYER + "AppWidgetResizeFrame|FrameLayout|ImageView:id/widget_resize_top_handle",
            DRAG_LAYER + "FloatingTaskView|FloatingTaskThumbnailView:id/thumbnail",
            DRAG_LAYER + "FloatingTaskView|SplitPlaceholderView:id/split_placeholder",
            DRAG_LAYER + "Folder|FolderPagedView:id/folder_content",
            DRAG_LAYER + "LauncherAllAppsContainerView:id/apps_view",
            DRAG_LAYER + "LauncherDragView",
            DRAG_LAYER + "LauncherRecentsView:id/overview_panel",
            DRAG_LAYER
                    + "NexusOverviewActionsView:id/overview_actions_view|FrameLayout:id"
                    + "/select_mode_buttons|ImageButton:id/close",
            DRAG_LAYER
                    + "PopupContainerWithArrow:id/popup_container|LinearLayout:id"
                    + "/deep_shortcuts_container|DeepShortcutView:id/deep_shortcut_material"
                    + "|DeepShortcutTextView:id/bubble_text",
            DRAG_LAYER
                    + "PopupContainerWithArrow:id/popup_container|LinearLayout:id"
                    + "/deep_shortcuts_container|DeepShortcutView:id/deep_shortcut_material|View"
                    + ":id/icon",
            DRAG_LAYER
                    + "PopupContainerWithArrow:id/popup_container|LinearLayout:id"
                    + "/system_shortcuts_container|DeepShortcutView:id/system_shortcut"
                    + "|BubbleTextView:id/bubble_text",
            DRAG_LAYER
                    + "PopupContainerWithArrow:id/popup_container|LinearLayout:id"
                    + "/system_shortcuts_container|DeepShortcutView:id/system_shortcut|View:id"
                    + "/icon",
            DRAG_LAYER
                    + "PopupContainerWithArrow:id/popup_container|LinearLayout:id"
                    + "/system_shortcuts_container|ImageView",
            DRAG_LAYER
                    + "PopupContainerWithArrow:id/popup_container|LinearLayout:id"
                    + "/widget_shortcut_container|DeepShortcutView:id/system_shortcut"
                    + "|BubbleTextView:id/bubble_text",
            DRAG_LAYER
                    + "PopupContainerWithArrow:id/popup_container|LinearLayout:id"
                    + "/widget_shortcut_container|DeepShortcutView:id/system_shortcut|View:id/icon",
            DRAG_LAYER + "SearchContainerView:id/apps_view",
            DRAG_LAYER + "Snackbar|TextView:id/action",
            DRAG_LAYER + "Snackbar|TextView:id/label",
            DRAG_LAYER + "SplitInstructionsView|AppCompatTextView:id/split_instructions_text",
            DRAG_LAYER + "TaskMenuView|LinearLayout:id/menu_option_layout",
            DRAG_LAYER + "TaskMenuViewWithArrow|LinearLayout:id/menu_option_layout",
            DRAG_LAYER + "TaskMenuView|TextView:id/task_name",
            DRAG_LAYER + "View",
            DRAG_LAYER + "WidgetsFullSheet|SpringRelativeLayout:id/container",
            DRAG_LAYER + "WidgetsTwoPaneSheet|SpringRelativeLayout:id/container",
            CONTENT + "LauncherRootView:id/launcher|FloatingIconView",
            RECENTS_DRAG_LAYER + "ArrowTipView",
            DRAG_LAYER + "ArrowTipView",
            DRAG_LAYER + "FallbackRecentsView:id/overview_panel",
            RECENTS_DRAG_LAYER + "FallbackRecentsView:id/overview_panel",
            DRAG_LAYER
                    + "NexusOverviewActionsView:id/overview_actions_view"
                    + "|LinearLayout:id/action_buttons",
            RECENTS_DRAG_LAYER
                    + "NexusOverviewActionsView:id/overview_actions_view"
                    + "|LinearLayout:id/action_buttons",
            DRAG_LAYER + "IconView",
            DRAG_LAYER
                    + "OptionsPopupView:id/popup_container|DeepShortcutView:id/system_shortcut"
                    + "|BubbleTextView:id/bubble_text"
    ));

    // Minimal increase or decrease of view's alpha between frames that triggers the error.
    private static final float ALPHA_JUMP_THRESHOLD = 1f;

    // Per-AnalysisNode data that's specific to this detector.
    private static class NodeData {
        public boolean ignoreAlphaJumps;

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

        // If the parent view ignores alpha jumps, its descendants will too.
        final boolean parentIgnoresAlphaJumps = info.parent != null && getNodeData(
                info.parent).ignoreAlphaJumps;
        if (parentIgnoresAlphaJumps) {
            nodeData.ignoreAlphaJumps = true;
            return;
        }

        // Parent view doesn't ignore alpha jumps.
        // Initialize this AnalysisNode's ignore-node with the corresponding child of the
        // ignore-node of the parent, if present.
        final IgnoreNode parentIgnoreNode = info.parent != null
                ? getNodeData(info.parent).ignoreNode
                : IGNORED_NODES_ROOT;
        nodeData.ignoreNode = parentIgnoreNode != null
                ? parentIgnoreNode.children.get(info.nodeIdentity) : null;
        // AnalysisNode will be ignored if the corresponding ignore-node is a leaf.
        nodeData.ignoreAlphaJumps =
                nodeData.ignoreNode != null && nodeData.ignoreNode.children.isEmpty();
    }

    @Override
    String detectAnomalies(AnalysisNode oldInfo, AnalysisNode newInfo, int frameN, long timestamp,
            int windowSizePx) {
        // If the view was previously seen, proceed with analysis only if it was present in the
        // view hierarchy in the previous frame.
        if (oldInfo != null && oldInfo.frameN != frameN) return null;

        final AnalysisNode latestInfo = newInfo != null ? newInfo : oldInfo;
        final NodeData nodeData = getNodeData(latestInfo);
        if (nodeData.ignoreAlphaJumps) return null;

        final float oldAlpha = oldInfo != null ? oldInfo.alpha : 0;
        final float newAlpha = newInfo != null ? newInfo.alpha : 0;
        final float alphaDeltaAbs = Math.abs(newAlpha - oldAlpha);

        if (alphaDeltaAbs >= ALPHA_JUMP_THRESHOLD) {
            nodeData.ignoreAlphaJumps = true; // No need to report alpha jump in children.
            return String.format(
                    "Alpha jump detected: alpha change: %s (%s -> %s), threshold: %s",
                    alphaDeltaAbs, oldAlpha, newAlpha, ALPHA_JUMP_THRESHOLD);
        }
        return null;
    }
}
