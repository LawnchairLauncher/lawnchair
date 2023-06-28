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

import static com.android.launcher3.util.viewcapture_analysis.ViewCaptureAnalyzer.diagPathFromRoot;

import com.android.launcher3.util.viewcapture_analysis.ViewCaptureAnalyzer.AnalysisNode;
import com.android.launcher3.util.viewcapture_analysis.ViewCaptureAnalyzer.AnomalyDetector;

import java.util.Collection;
import java.util.Set;

/**
 * Anomaly detector that triggers an error when alpha of a view changes too rapidly.
 * Invisible views are treated as if they had zero alpha.
 */
final class AlphaJumpDetector extends AnomalyDetector {
    // Paths of nodes that are excluded from analysis.
    private static final Collection<String> PATHS_TO_IGNORE = Set.of(
            "DecorView|LinearLayout|FrameLayout:id/content|LauncherRootView:id/launcher|DragLayer"
                    + ":id/drag_layer|SearchContainerView:id/apps_view|SearchRecyclerView:id"
                    + "/search_results_list_view|SearchResultSmallIconRow",
            "DecorView|LinearLayout|FrameLayout:id/content|LauncherRootView:id/launcher|DragLayer"
                    + ":id/drag_layer|SearchContainerView:id/apps_view|SearchRecyclerView:id"
                    + "/search_results_list_view|SearchResultIcon",
            "DecorView|LinearLayout|FrameLayout:id/content|LauncherRootView:id/launcher|DragLayer"
                    + ":id/drag_layer|LauncherRecentsView:id/overview_panel|TaskView",
            "DecorView|LinearLayout|FrameLayout:id/content|LauncherRootView:id/launcher|DragLayer"
                    + ":id/drag_layer|WidgetsFullSheet|SpringRelativeLayout:id/container"
                    + "|WidgetsRecyclerView:id/primary_widgets_list_view|WidgetsListHeader:id"
                    + "/widgets_list_header",
            "DecorView|LinearLayout|FrameLayout:id/content|LauncherRootView:id/launcher|DragLayer"
                    + ":id/drag_layer|WidgetsFullSheet|SpringRelativeLayout:id/container"
                    + "|WidgetsRecyclerView:id/primary_widgets_list_view"
                    + "|StickyHeaderLayout$EmptySpaceView",
            "DecorView|LinearLayout|FrameLayout:id/content|LauncherRootView:id/launcher|DragLayer"
                    + ":id/drag_layer|SearchContainerView:id/apps_view|AllAppsRecyclerView:id"
                    + "/apps_list_view|BubbleTextView:id/icon",
            "DecorView|LinearLayout|FrameLayout:id/content|LauncherRootView:id/launcher|DragLayer"
                    + ":id/drag_layer|LauncherRecentsView:id/overview_panel|ClearAllButton:id"
                    + "/clear_all",
            "DecorView|LinearLayout|FrameLayout:id/content|LauncherRootView:id/launcher|DragLayer"
                    + ":id/drag_layer|NexusOverviewActionsView:id/overview_actions_view"
                    + "|LinearLayout:id/action_buttons"
    );
    // Minimal increase or decrease of view's alpha between frames that triggers the error.
    private static final float ALPHA_JUMP_THRESHOLD = 1f;

    @Override
    void initializeNode(AnalysisNode info) {
        // If the parent view ignores alpha jumps, its descendants will too.
        final boolean parentIgnoreAlphaJumps = info.parent != null && info.parent.ignoreAlphaJumps;
        info.ignoreAlphaJumps = parentIgnoreAlphaJumps
                || PATHS_TO_IGNORE.contains(diagPathFromRoot(info));
    }

    @Override
    void detectAnomalies(AnalysisNode oldInfo, AnalysisNode newInfo, int frameN) {
        // If the view was previously seen, proceed with analysis only if it was present in the
        // view hierarchy in the previous frame.
        if (oldInfo != null && oldInfo.frameN != frameN) return;

        final AnalysisNode latestInfo = newInfo != null ? newInfo : oldInfo;
        if (latestInfo.ignoreAlphaJumps) return;

        final float oldAlpha = oldInfo != null ? oldInfo.alpha : 0;
        final float newAlpha = newInfo != null ? newInfo.alpha : 0;
        final float alphaDeltaAbs = Math.abs(newAlpha - oldAlpha);

        if (alphaDeltaAbs >= ALPHA_JUMP_THRESHOLD) {
            throw new AssertionError(
                    String.format(
                            "Alpha jump detected in ViewCapture data: alpha change: %s (%s -> %s)"
                                    + ", threshold: %s, view: %s",
                            alphaDeltaAbs, oldAlpha, newAlpha, ALPHA_JUMP_THRESHOLD, latestInfo));
        }
    }
}
