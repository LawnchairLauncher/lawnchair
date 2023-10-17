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

import static android.view.View.VISIBLE;

import com.android.app.viewcapture.data.ExportedData;
import com.android.app.viewcapture.data.FrameData;
import com.android.app.viewcapture.data.ViewNode;
import com.android.app.viewcapture.data.WindowData;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility that analyzes ViewCapture data and finds anomalies such as views appearing or
 * disappearing without alpha-fading.
 */
public class ViewCaptureAnalyzer {
    private static final String SCRIM_VIEW_CLASS = "com.android.launcher3.views.ScrimView";

    // All detectors. They will be invoked in the order listed here.
    private static final AnomalyDetector[] ANOMALY_DETECTORS = {
            new AlphaJumpDetector(),
            new FlashDetector(),
            new PositionJumpDetector()
    };

    static {
        for (int i = 0; i < ANOMALY_DETECTORS.length; ++i) ANOMALY_DETECTORS[i].detectorOrdinal = i;
    }

    // A view from view capture data converted to a form that's convenient for detecting anomalies.
    static class AnalysisNode {
        public String className;
        public String resourceId;
        public AnalysisNode parent;

        // Window coordinates of the view.
        public float left;
        public float top;
        public float right;
        public float bottom;

        // Visible scale and alpha, build recursively from the ancestor list.
        public float scaleX;
        public float scaleY;
        public float alpha; // Always > 0

        public int frameN;

        // Timestamp of the frame when this view became abruptly visible, i.e. its alpha became 1
        // the next frame after it was 0 or the view wasn't visible.
        // If the view is currently invisible or the last appearance wasn't abrupt, the value is -1.
        public long timeBecameVisibleNs;

        // Timestamp of the frame when this view became abruptly invisible last time, i.e. its
        // alpha became 0, or view disappeared, after being 1 in the previous frame.
        // If the view is currently visible or the last disappearance wasn't abrupt, the value is
        // -1.
        public long timeBecameInvisibleNs;

        public ViewNode viewCaptureNode;

        // Class name + resource id
        public String nodeIdentity;

        // Collection of detector-specific data for this node.
        public final Object[] detectorsData = new Object[ANOMALY_DETECTORS.length];

        @Override
        public String toString() {
            return String.format("view window coordinates: (%s, %s, %s, %s)",
                    left, top, right, bottom);
        }
    }

    /**
     * Scans a view capture record and searches for view animation anomalies. Can find anomalies for
     * multiple views.
     * Returns a map from the view path to the anomaly message for the view. Non-empty map means
     * that anomalies were detected.
     */
    public static Map<String, String> getAnomalies(ExportedData viewCaptureData) {
        final Map<String, String> anomalies = new HashMap<>();

        final int scrimClassIndex = viewCaptureData.getClassnameList().indexOf(SCRIM_VIEW_CLASS);

        final int windowDataCount = viewCaptureData.getWindowDataCount();
        for (int i = 0; i < windowDataCount; ++i) {
            analyzeWindowData(
                    viewCaptureData, viewCaptureData.getWindowData(i), scrimClassIndex, anomalies);
        }
        return anomalies;
    }

    private static void analyzeWindowData(ExportedData viewCaptureData, WindowData windowData,
            int scrimClassIndex, Map<String, String> anomalies) {
        // View hash code => Last seen node with this hash code.
        // The view is added when we analyze the first frame where it's visible.
        // After that, it gets updated for every frame where it's visible.
        // As we go though frames, if a view becomes invisible, it stays in the map.
        final Map<Integer, AnalysisNode> lastSeenNodes = new HashMap<>();

        int windowWidthPx = -1;
        int windowHeightPx = -1;

        for (int frameN = 0; frameN < windowData.getFrameDataCount(); ++frameN) {
            final FrameData frame = windowData.getFrameData(frameN);
            final ViewNode rootNode = frame.getNode();

            // If the rotation or window size has changed, reset the analyzer state.
            final boolean isFirstFrame = windowWidthPx != rootNode.getWidth()
                    || windowHeightPx != rootNode.getHeight();
            if (isFirstFrame) {
                windowWidthPx = rootNode.getWidth();
                windowHeightPx = rootNode.getHeight();
                lastSeenNodes.clear();
            }

            final int windowSizePx = Math.max(rootNode.getWidth(), rootNode.getHeight());

            analyzeFrame(frameN, isFirstFrame, frame, viewCaptureData, lastSeenNodes,
                    scrimClassIndex, anomalies, windowSizePx);
        }
    }

    private static void analyzeFrame(int frameN, boolean isFirstFrame, FrameData frame,
            ExportedData viewCaptureData,
            Map<Integer, AnalysisNode> lastSeenNodes, int scrimClassIndex,
            Map<String, String> anomalies, int windowSizePx) {
        // Analyze the node tree starting from the root.
        long frameTimeNs = frame.getTimestamp();
        analyzeView(
                frameTimeNs,
                frame.getNode(),
                /* parent = */ null,
                frameN,
                isFirstFrame,
                /* leftShift = */ 0,
                /* topShift = */ 0,
                viewCaptureData,
                lastSeenNodes,
                scrimClassIndex,
                anomalies,
                windowSizePx);

        // Analyze transitions when a view visible in the previous frame became invisible in the
        // current one.
        for (AnalysisNode info : lastSeenNodes.values()) {
            if (info.frameN == frameN - 1) {
                if (!info.viewCaptureNode.getWillNotDraw()) {
                    Arrays.stream(ANOMALY_DETECTORS).forEach(
                            detector ->
                                    detectAnomaly(
                                            detector,
                                            frameN,
                                            /* oldInfo = */ info,
                                            /* newInfo = */ null,
                                            anomalies,
                                            frameTimeNs,
                                            windowSizePx)
                    );
                }
                info.timeBecameInvisibleNs = info.alpha == 1 ? frameTimeNs : -1;
                info.timeBecameVisibleNs = -1;
            }
        }
    }

    private static void analyzeView(long frameTimeNs, ViewNode viewCaptureNode, AnalysisNode parent,
            int frameN,
            boolean isFirstFrame, float leftShift, float topShift, ExportedData viewCaptureData,
            Map<Integer, AnalysisNode> lastSeenNodes, int scrimClassIndex,
            Map<String, String> anomalies, int windowSizePx) {
        // Skip analysis of invisible views
        final float parentAlpha = parent != null ? parent.alpha : 1;
        final float alpha = getVisibleAlpha(viewCaptureNode, parentAlpha);
        if (alpha <= 0.0) return;

        // Calculate analysis node parameters
        final int hashcode = viewCaptureNode.getHashcode();
        final int classIndex = viewCaptureNode.getClassnameIndex();

        final float parentScaleX = parent != null ? parent.scaleX : 1;
        final float parentScaleY = parent != null ? parent.scaleY : 1;
        final float scaleX = parentScaleX * viewCaptureNode.getScaleX();
        final float scaleY = parentScaleY * viewCaptureNode.getScaleY();

        final float left = leftShift
                + (viewCaptureNode.getLeft() + viewCaptureNode.getTranslationX()) * parentScaleX
                + viewCaptureNode.getWidth() * (parentScaleX - scaleX) / 2;
        final float top = topShift
                + (viewCaptureNode.getTop() + viewCaptureNode.getTranslationY()) * parentScaleY
                + viewCaptureNode.getHeight() * (parentScaleY - scaleY) / 2;
        final float width = viewCaptureNode.getWidth() * scaleX;
        final float height = viewCaptureNode.getHeight() * scaleY;

        // Initialize new analysis node
        final AnalysisNode newAnalysisNode = new AnalysisNode();
        newAnalysisNode.className = viewCaptureData.getClassname(classIndex);
        newAnalysisNode.resourceId = viewCaptureNode.getId();
        newAnalysisNode.nodeIdentity =
                getNodeIdentity(newAnalysisNode.className, newAnalysisNode.resourceId);
        newAnalysisNode.parent = parent;
        newAnalysisNode.left = left;
        newAnalysisNode.top = top;
        newAnalysisNode.right = left + width;
        newAnalysisNode.bottom = top + height;
        newAnalysisNode.scaleX = scaleX;
        newAnalysisNode.scaleY = scaleY;
        newAnalysisNode.alpha = alpha;
        newAnalysisNode.frameN = frameN;
        newAnalysisNode.timeBecameInvisibleNs = -1;
        newAnalysisNode.viewCaptureNode = viewCaptureNode;
        Arrays.stream(ANOMALY_DETECTORS).forEach(
                detector -> detector.initializeNode(newAnalysisNode));

        final AnalysisNode oldAnalysisNode = lastSeenNodes.get(hashcode); // may be null

        if (oldAnalysisNode != null && oldAnalysisNode.frameN + 1 == frameN) {
            // If this view was present in the previous frame, keep the time when it became visible.
            newAnalysisNode.timeBecameVisibleNs = oldAnalysisNode.timeBecameVisibleNs;
        } else {
            // If the view is becoming visible after being invisible, initialize the time when it
            // became visible with a new value.
            // If the view became visible abruptly, i.e. alpha jumped from 0 to 1 between the
            // previous and the current frames, then initialize with the time of the current
            // frame. Otherwise, use -1.
            newAnalysisNode.timeBecameVisibleNs = newAnalysisNode.alpha >= 1 ? frameTimeNs : -1;
        }

        // Detect anomalies for the view.
        if (!isFirstFrame && !viewCaptureNode.getWillNotDraw()) {
            Arrays.stream(ANOMALY_DETECTORS).forEach(
                    detector ->
                            detectAnomaly(detector, frameN, oldAnalysisNode, newAnalysisNode,
                                    anomalies, frameTimeNs, windowSizePx)
            );
        }
        lastSeenNodes.put(hashcode, newAnalysisNode);

        // Enumerate children starting from the topmost one. Stop at ScrimView, if present.
        final float leftShiftForChildren = left - viewCaptureNode.getScrollX();
        final float topShiftForChildren = top - viewCaptureNode.getScrollY();
        for (int i = viewCaptureNode.getChildrenCount() - 1; i >= 0; --i) {
            final ViewNode child = viewCaptureNode.getChildren(i);

            // Don't analyze anything under scrim view because we don't know whether it's
            // transparent.
            if (child.getClassnameIndex() == scrimClassIndex) break;

            analyzeView(frameTimeNs, child, newAnalysisNode, frameN, isFirstFrame,
                    leftShiftForChildren,
                    topShiftForChildren,
                    viewCaptureData, lastSeenNodes, scrimClassIndex, anomalies, windowSizePx);
        }
    }

    private static void detectAnomaly(AnomalyDetector detector, int frameN,
            AnalysisNode oldAnalysisNode, AnalysisNode newAnalysisNode,
            Map<String, String> anomalies, long frameTimeNs, int windowSizePx) {
        final String maybeAnomaly =
                detector.detectAnomalies(oldAnalysisNode, newAnalysisNode, frameN, frameTimeNs,
                        windowSizePx);
        if (maybeAnomaly != null) {
            AnalysisNode latestInfo = newAnalysisNode != null ? newAnalysisNode : oldAnalysisNode;
            final String viewDiagPath = diagPathFromRoot(latestInfo);
            if (!anomalies.containsKey(viewDiagPath)) {
                anomalies.put(viewDiagPath, String.format("%s, %s", maybeAnomaly, latestInfo));
            }
        }
    }

    private static float getVisibleAlpha(ViewNode node, float parenVisibleAlpha) {
        return node.getVisibility() == VISIBLE
                ? parenVisibleAlpha * Math.max(0, Math.min(node.getAlpha(), 1))
                : 0f;
    }

    private static String classNameToSimpleName(String className) {
        return className.substring(className.lastIndexOf(".") + 1);
    }

    private static String diagPathFromRoot(AnalysisNode analysisNode) {
        final StringBuilder path = new StringBuilder(analysisNode.nodeIdentity);
        for (AnalysisNode ancestor = analysisNode.parent;
                ancestor != null;
                ancestor = ancestor.parent) {
            path.insert(0, ancestor.nodeIdentity + "|");
        }
        return path.toString();
    }

    private static String getNodeIdentity(String className, String resourceId) {
        final StringBuilder sb = new StringBuilder();
        sb.append(classNameToSimpleName(className));
        if (!"NO_ID".equals(resourceId)) sb.append(":" + resourceId);
        return sb.toString();
    }
}
